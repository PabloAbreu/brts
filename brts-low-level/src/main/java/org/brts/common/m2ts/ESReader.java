package org.brts.common.m2ts;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.model.StreamCodingType;

import lombok.extern.slf4j.Slf4j;

/**
 * Wraps an elementary stream file, providing frame-by-frame reads and PES stream_id derivation. Detects access unit
 * (frame) boundaries based on the codec type:
 * <ul>
 * <li>H.264/AVC: splits at AUD NAL (type 9) or at every VCL NAL if no AUDs present. When B-frames are detected via
 * H.264 POC analysis, per-frame PTS values (display order) and DTS values (decode order) are pre-computed so that the
 * muxer can write correct {@code PTS_DTS_flags=11} PES headers.</li>
 * <li>MPEG-2 video: splits at picture_start_code (00 00 01 00)</li>
 * <li>AC3/E-AC3 audio: splits at sync word (0x0B 0x77)</li>
 * <li>Others: returns fixed-size chunks</li>
 * </ul>
 */
@Slf4j
public class ESReader implements Closeable {

	private final M2tsDescriptor.StreamEntry entry;

	private boolean done = false;

	private final StreamCodingType codingType;

	// Entire ES loaded into memory for frame boundary detection
	private final byte[] esData;

	// Pre-split frame offsets for frame-aware reading
	private final List<int[]> frameOffsets; // [start, length] pairs

	private int frameIndex = 0;

	/**
	 * Per-frame PTS in 90 kHz ticks, pre-computed from H.264 POC display order with a reorder-delay offset so that DTS
	 * ≤ PTS for every frame. {@code null} when no B-frame reordering is needed (poc-type != 0, no displayrank shuffle,
	 * or non-H264 stream).
	 */
	private long[] framePts;

	/**
	 * Per-frame DTS in 90 kHz ticks (decode order: {@code i * frameDuration}). Non-null precisely when
	 * {@link #framePts} is non-null.
	 */
	private long[] frameDts;

	/**
	 * Number of frames the stream reorders for display (= max(decode_i − display_rank_i)); used to shift PTS so that
	 * DTS ≤ PTS always holds.
	 */
	private int numReorderDelay;

	ESReader(M2tsDescriptor.StreamEntry entry, Path file) throws IOException {
		this.entry = entry;
		this.codingType = M2tsParser.mapStreamType(entry.getStreamTypeByte());
		this.esData = Files.readAllBytes(file);
		this.frameOffsets = detectFrameBoundaries();
		if (codingType == StreamCodingType.H264_AVC && !frameOffsets.isEmpty()) {
			computeH264PtsDts();
		}
	}

	// -----------------------------------------------------------------
	// B-frame PTS/DTS reordering (H.264 only)
	// -----------------------------------------------------------------

	/**
	 * Pre-computes {@link #framePts} and {@link #frameDts} for H.264 streams that contain B-frames, using
	 * {@code pic_order_cnt_lsb} values parsed from slice headers to determine the display order (POC rank) of each
	 * access unit.
	 *
	 * <p>
	 * The algorithm:
	 * <ol>
	 * <li>Find the first SPS NAL and parse {@link H264SpsInfo}.</li>
	 * <li>For each AU, locate the first VCL slice NAL and read {@code pic_order_cnt_lsb} (POC type&nbsp;0 only).</li>
	 * <li>Reconstruct a global POC value per AU using the wrap-around rule described in H.264 spec §8.2.1.1.</li>
	 * <li>Rank AUs by ascending POC to get their display order.</li>
	 * <li>Compute {@code numReorderDelay} = max(decode_i − display_rank_i) so that DTS ≤ PTS for every frame.</li>
	 * <li>Assign:
	 * <ul>
	 * <li>{@code framePts[i] = (displayRank[i] + numReorderDelay) × fd}</li>
	 * <li>{@code frameDts[i] = i × fd}</li>
	 * </ul>
	 * </li>
	 * </ol>
	 * If parsing fails or no reordering is needed, {@link #framePts} and {@link #frameDts} are left {@code null} and
	 * the mux loop falls back to the existing sequential PTS assignment.
	 */
	private void computeH264PtsDts() {
		H264SpsInfo sps = findAndParseSps();
		if (sps == null || sps.picOrderCntType != 0)
			return;

		int n = frameOffsets.size();
		int[] pocValues = new int[n];
		int prevPocMsb = 0, prevPocLsb = 0;
		int maxPocLsb = 1 << (sps.log2MaxPicOrderCntLsbMinus4 + 4);
		boolean firstFrame = true;

		for (int i = 0; i < n; i++) {
			int[] off = frameOffsets.get(i);
			int pocLsb = findAndReadSlicePocLsb(off[0], off[1], sps);
			if (pocLsb < 0)
				return; // parse error — abort

			int pocMsb;
			if (firstFrame) {
				pocMsb = 0;
				firstFrame = false;
			} else if (pocLsb < prevPocLsb && (prevPocLsb - pocLsb) >= maxPocLsb / 2) {
				pocMsb = prevPocMsb + maxPocLsb; // positive wrap
			} else if (pocLsb > prevPocLsb && (pocLsb - prevPocLsb) > maxPocLsb / 2) {
				pocMsb = prevPocMsb - maxPocLsb; // negative wrap
			} else {
				pocMsb = prevPocMsb;
			}
			pocValues[i] = pocMsb + pocLsb;
			prevPocMsb = pocMsb;
			prevPocLsb = pocLsb;
		}

		// Rank each AU by ascending POC → display order
		Integer[] indices = new Integer[n];
		for (int i = 0; i < n; i++)
			indices[i] = i;
		Arrays.sort(indices, (a, b) -> Integer.compare(pocValues[a], pocValues[b]));

		int[] displayRank = new int[n];
		for (int rank = 0; rank < n; rank++)
			displayRank[indices[rank]] = rank;

		// Minimum offset so that DTS ≤ PTS for every frame
		int delay = 0;
		for (int i = 0; i < n; i++)
			delay = Math.max(delay, i - displayRank[i]);

		if (delay == 0)
			return; // no reordering needed (all-intra or B-frames absent)

		long fd = frameDuration90kHz();
		framePts = new long[n];
		frameDts = new long[n];
		numReorderDelay = delay;
		for (int i = 0; i < n; i++) {
			framePts[i] = (long) (displayRank[i] + delay) * fd;
			frameDts[i] = (long) i * fd;
		}
		log.debug("H.264 B-frame reordering: {} frames, reorder delay={} frames", n, delay);
	}

	/**
	 * Scans {@link #esData} for the first SPS NAL unit (type&nbsp;7) and parses it.
	 *
	 * @return parsed {@link H264SpsInfo}, or {@code null} if none found
	 */
	private H264SpsInfo findAndParseSps() {
		for (int i = 0; i <= esData.length - 5; i++) {
			if ((esData[i] & 0xFF) == 0x00 && (esData[i + 1] & 0xFF) == 0x00 && (esData[i + 2] & 0xFF) == 0x00
					&& (esData[i + 3] & 0xFF) == 0x01 && (esData[i + 4] & 0x1F) == 7) { // NAL
																						// type
																						// 7
																						// =
																						// SPS
				int nalStart = i + 4; // offset of the NAL header byte (type field)
				int nalEnd = esData.length;
				for (int j = nalStart + 1; j <= esData.length - 4; j++) {
					if ((esData[j] & 0xFF) == 0x00 && (esData[j + 1] & 0xFF) == 0x00 && (esData[j + 2] & 0xFF) == 0x00
							&& (esData[j + 3] & 0xFF) == 0x01) {
						nalEnd = j;
						break;
					}
				}
				return M2tsWriter.parseH264Sps(esData, nalStart, nalEnd - nalStart);
			}
		}
		return null;
	}

	/**
	 * Searches the AU at [{@code frameStart}, {@code frameStart+frameLen}) for the first VCL slice NAL (type&nbsp;1
	 * or&nbsp;5) and returns its {@code pic_order_cnt_lsb}, or {@code -1} on error.
	 */
	private int findAndReadSlicePocLsb(int frameStart, int frameLen, H264SpsInfo sps) {
		int end = frameStart + frameLen;
		for (int i = frameStart; i <= end - 5; i++) {
			if ((esData[i] & 0xFF) == 0x00 && (esData[i + 1] & 0xFF) == 0x00 && (esData[i + 2] & 0xFF) == 0x00
					&& (esData[i + 3] & 0xFF) == 0x01) {
				int nalType = esData[i + 4] & 0x1F;
				if (nalType == 1 || nalType == 5) { // VCL slice (non-IDR or IDR)
					int nalStart = i + 4;
					int nalEnd = end;
					for (int j = nalStart + 1; j <= end - 4; j++) {
						if ((esData[j] & 0xFF) == 0x00 && (esData[j + 1] & 0xFF) == 0x00
								&& (esData[j + 2] & 0xFF) == 0x00 && (esData[j + 3] & 0xFF) == 0x01) {
							nalEnd = j;
							break;
						}
					}
					return M2tsWriter.readSlicePocLsb(esData, nalStart, nalEnd - nalStart, sps);
				}
			}
		}
		return -1;
	}

	// -----------------------------------------------------------------
	// B-frame timing accessors
	// -----------------------------------------------------------------

	/**
	 * Returns {@code true} iff this is an H.264 stream whose access units were found to be stored in a reordered
	 * (B-frame) decode order. When {@code true}, the caller must use {@link #getPts} / {@link #getDts} rather than
	 * external sequential PTS tracking.
	 */
	boolean hasBFrames() {
		return framePts != null;
	}

	/** Current frame index (= the index of the <em>next</em> frame to be read). */
	int getFrameIndex() {
		return frameIndex;
	}

	/**
	 * Returns the PES PTS (display-order, with reorder-delay offset) for decode-order frame {@code i}. The offset
	 * guarantees DTS ≤ PTS.
	 */
	long getPts(int i) {
		return framePts[i];
	}

	/**
	 * Returns the PES DTS (decode-order) for frame {@code i}. Equal to {@code i × frameDuration90kHz()}.
	 */
	long getDts(int i) {
		return frameDts[i];
	}

	/**
	 * Returns the <em>display</em> PTS without the reorder-delay offset, i.e. {@code displayRank[i] × frameDuration}.
	 * Use this value against the chapter {@code ptsTicks} map so that descriptors can still specify chapter positions
	 * as logical display times (0 = first I-frame).
	 */
	long getDisplayPts(int i) {
		return framePts[i] - (long) numReorderDelay * frameDuration90kHz();
	}

	/**
	 * Detects frame boundaries and returns a list of [offset, length] pairs.
	 */
	private List<int[]> detectFrameBoundaries() {
		List<int[]> frames = new ArrayList<>();
		if (esData.length == 0)
			return frames;

		if (codingType == StreamCodingType.H264_AVC) {
			// H.264 Annex B (as produced by MkvDemuxer): detect access unit boundaries.
			// Collect all 4-byte start code positions (00 00 00 01) plus their NAL type.
			List<int[]> nalList = new ArrayList<>(); // [offset, nalType]
			for (int i = 0; i <= esData.length - 5; i++) {
				if ((esData[i] & 0xFF) == 0x00 && (esData[i + 1] & 0xFF) == 0x00 && (esData[i + 2] & 0xFF) == 0x00
						&& (esData[i + 3] & 0xFF) == 0x01) {
					int nalType = esData[i + 4] & 0x1F;
					nalList.add(new int[] { i, nalType });
					i += 3; // skip start code bytes to avoid re-scanning
				}
			}
			if (nalList.isEmpty()) {
				frames.add(new int[] { 0, esData.length });
			} else {
				// Prefer AUD NAL (type 9) as AU delimiter; fall back to every VCL NAL
				// (types 1-5).
				boolean hasAud = nalList.stream().anyMatch(n -> n[1] == 9);
				List<Integer> auStarts = new ArrayList<>();
				if (hasAud) {
					for (int[] nal : nalList) {
						if (nal[1] == 9)
							auStarts.add(nal[0]);
					}
				} else {
					// No AUDs: every VCL NAL starts a new AU.
					// Scan backwards from each VCL to include its non-VCL preamble (SPS,
					// PPS, SEI).
					for (int k = 0; k < nalList.size(); k++) {
						int nalType = nalList.get(k)[1];
						if (nalType >= 1 && nalType <= 5) {
							int auStart = nalList.get(k)[0];
							for (int m = k - 1; m >= 0; m--) {
								int prevType = nalList.get(m)[1];
								if (prevType >= 1 && prevType <= 5)
									break; // hit previous VCL
								auStart = nalList.get(m)[0];
							}
							auStarts.add(auStart);
						}
					}
				}
				if (auStarts.isEmpty()) {
					frames.add(new int[] { 0, esData.length });
				} else {
					// First AU starts at offset 0 to capture any SPS/PPS preamble.
					for (int j = 0; j < auStarts.size(); j++) {
						int start = (j == 0) ? 0 : auStarts.get(j);
						int end = (j + 1 < auStarts.size()) ? auStarts.get(j + 1) : esData.length;
						frames.add(new int[] { start, end - start });
					}
				}
			}
		} else if (codingType != null && codingType.isVideo()) {
			// MPEG-2 video: split at picture_start_code (00 00 01 00)
			List<Integer> picStarts = new ArrayList<>();
			for (int i = 0; i <= esData.length - 4; i++) {
				if (esData[i] == 0x00 && esData[i + 1] == 0x00 && esData[i + 2] == 0x01 && esData[i + 3] == 0x00) {
					picStarts.add(i);
				}
			}
			if (picStarts.isEmpty()) {
				frames.add(new int[] { 0, esData.length });
			} else {
				for (int j = 0; j < picStarts.size(); j++) {
					int start = (j == 0) ? 0 : picStarts.get(j);
					int end = (j + 1 < picStarts.size()) ? picStarts.get(j + 1) : esData.length;
					frames.add(new int[] { start, end - start });
				}
			}
		} else if (codingType != null && codingType.isDolbyAudio()) {
			// AC3 / E-AC3: split at sync word 0x0B77
			List<Integer> syncStarts = new ArrayList<>();
			for (int i = 0; i <= esData.length - 2; i++) {
				if ((esData[i] & 0xFF) == 0x0B && (esData[i + 1] & 0xFF) == 0x77) {
					syncStarts.add(i);
				}
			}
			if (syncStarts.isEmpty()) {
				frames.add(new int[] { 0, esData.length });
			} else {
				for (int j = 0; j < syncStarts.size(); j++) {
					int start = syncStarts.get(j);
					int end = (j + 1 < syncStarts.size()) ? syncStarts.get(j + 1) : esData.length;
					frames.add(new int[] { start, end - start });
				}
			}
		} else if (codingType != null && (codingType == StreamCodingType.DTS || codingType == StreamCodingType.DTS_HD
				|| codingType == StreamCodingType.DTS_HD_MASTER_AUDIO)) {
			// DTS: split at sync word 0x7FFE8001
			List<Integer> syncStarts = new ArrayList<>();
			for (int i = 0; i <= esData.length - 4; i++) {
				if ((esData[i] & 0xFF) == 0x7F && (esData[i + 1] & 0xFF) == 0xFE && (esData[i + 2] & 0xFF) == 0x80
						&& (esData[i + 3] & 0xFF) == 0x01) {
					syncStarts.add(i);
				}
			}
			if (syncStarts.isEmpty()) {
				frames.add(new int[] { 0, esData.length });
			} else {
				for (int j = 0; j < syncStarts.size(); j++) {
					int start = syncStarts.get(j);
					int end = (j + 1 < syncStarts.size()) ? syncStarts.get(j + 1) : esData.length;
					frames.add(new int[] { start, end - start });
				}
			}
		} else if (codingType == StreamCodingType.INTERACTIVE_GRAPHICS
				|| codingType == StreamCodingType.PRESENTATION_GRAPHICS) {
			// IGS / PGS: the raw ES file is a concatenation of bare segments:
			// 1 byte type
			// 2 bytes big-endian length
			// N bytes data
			// The HDMV spec requires each segment to be carried in its own
			// PES packet, so we split at segment boundaries here.
			int pos = 0;
			while (pos + 3 <= esData.length) {
				int segLen = ((esData[pos + 1] & 0xFF) << 8) | (esData[pos + 2] & 0xFF);
				int totalLen = 3 + segLen;
				if (pos + totalLen > esData.length) {
					log.warn("Truncated IGS/PGS segment at offset {}, stopping", pos);
					break;
				}
				frames.add(new int[] { pos, totalLen });
				pos += totalLen;
			}
			if (frames.isEmpty()) {
				frames.add(new int[] { 0, esData.length });
			}
		} else {
			// Fallback: fixed-size chunks (kept within 16-bit PES packet_length limit)
			int chunkSize = 65000;
			for (int off = 0; off < esData.length; off += chunkSize) {
				int len = Math.min(chunkSize, esData.length - off);
				frames.add(new int[] { off, len });
			}
		}
		return frames;
	}

	/** PID of this stream. */
	int pid() {
		return entry.getPid();
	}

	/** Is this stream a video stream? */
	boolean isVideo() {
		return codingType != null && codingType.isVideo();
	}

	/** Is this an H.264/AVC video stream? */
	boolean isH264() {
		return codingType == StreamCodingType.H264_AVC;
	}

	/** Frame duration in 90 kHz ticks based on stream type and frame rate. */
	long frameDuration90kHz() {
		if (codingType != null && codingType.isVideo()) {
			Double fps = entry.getFrameRateFps();
			if (fps != null && fps > 0)
				return Math.round(90_000.0 / fps);
			return 90_000L / 24; // default 24 fps
		}
		if (codingType != null && (codingType == StreamCodingType.INTERACTIVE_GRAPHICS
				|| codingType == StreamCodingType.PRESENTATION_GRAPHICS)) {
			// All segments within a display set share the same composition PTS.
			return 0L;
		}
		if (codingType != null && codingType.isDolbyAudio()) {
			// AC3: 1536 samples per frame at 48 kHz = 32 ms
			return 90_000L * 1536 / 48000; // = 2880
		}
		if (codingType != null && (codingType == StreamCodingType.DTS || codingType == StreamCodingType.DTS_HD
				|| codingType == StreamCodingType.DTS_HD_MASTER_AUDIO)) {
			return 90_000L * 512 / 48000; // DTS: 512 samples per frame
		}
		return 90_000L / 24; // fallback
	}

	/**
	 * MPEG-2 PES stream_id: 0xE0 = video, 0xC0-0xDF = audio, 0xBD = private (DTS, AC3, PGS, IGS).
	 */
	int streamId() {
		if (codingType == null)
			return 0xBD;
		if (codingType.isVideo())
			return 0xE0;
		return switch (codingType) {
		case LPCM, DOLBY_AC3, DOLBY_AC3_PLUS, DOLBY_TRUEHD, DTS, DTS_HD, DTS_HD_MASTER_AUDIO, PRESENTATION_GRAPHICS,
				INTERACTIVE_GRAPHICS, TEXT_SUBTITLE ->
			0xBD;
		default -> 0xC0;
		};
	}

	/**
	 * HDMV sub-stream identifier inserted as the first byte of the PES data payload for {@code stream_id=0xBD}
	 * (private_stream_1) packets.
	 * <p>
	 * Blu-ray spec mapping:
	 * <ul>
	 * <li>0x20–0x3F = Interactive Graphics (IG) sub-stream n</li>
	 * <li>0x00–0x1F = Presentation Graphics (PG) sub-stream n</li>
	 * <li>0x80–0xBF = private audio (AC-3, DTS, LPCM, …)</li>
	 * </ul>
	 * For stream types that do not use {@code 0xBD} the value is not used (callers check {@link #streamId()} first).
	 */
	int substreamId() {
		if (codingType == null)
			return M2tsWriter.SUBSTREAM_ID_AUDIO_PRIVATE;
		return switch (codingType) {
		case INTERACTIVE_GRAPHICS -> M2tsWriter.SUBSTREAM_ID_IG;
		case PRESENTATION_GRAPHICS, TEXT_SUBTITLE -> M2tsWriter.SUBSTREAM_ID_PG;
		case LPCM, DOLBY_AC3, DOLBY_AC3_PLUS, DOLBY_TRUEHD, DTS, DTS_HD, DTS_HD_MASTER_AUDIO ->
			M2tsWriter.SUBSTREAM_ID_AUDIO_PRIVATE;
		default -> 0x00;
		};
	}

	/** Returns the next access unit (frame), or null when EOF. */
	byte[] nextFrame() {
		if (done || frameIndex >= frameOffsets.size()) {
			done = true;
			return null;
		}
		int[] off = frameOffsets.get(frameIndex++);
		if (frameIndex >= frameOffsets.size())
			done = true;
		return Arrays.copyOfRange(esData, off[0], off[0] + off[1]);
	}

	boolean isDone() {
		return done;
	}

	@Override
	public void close() {
		// Data is in memory, nothing to close
	}

}