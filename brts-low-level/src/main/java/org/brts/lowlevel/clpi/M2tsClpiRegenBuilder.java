package org.brts.lowlevel.clpi;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/clpi/M2tsClpiRegenBuilder.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.AudioChannelLayoutConverter;
import org.brts.common.m2ts.M2tsDemuxer;
import org.brts.common.m2ts.M2tsPacketHandler;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.clpi.EpMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Regenerates a {@link ClipInfo} (CLPI metadata) by fully parsing an existing M2TS file — without any JSON descriptor
 * or pre-muxed ES files.
 *
 * <h2>Algorithm</h2>
 * <ol>
 * <li><b>Phase 1 – PMT scan</b>: {@link M2tsParser} reads PAT/PMT to extract stream metadata (PIDs, coding types, video
 * format/frame-rate, audio channels, languages).</li>
 * <li><b>Phase 2 – Full-file EP scan</b>: {@link M2tsDemuxer} iterates every source packet; for each video PES access
 * unit the PTS is decoded from the PES header and the bitstream is probed to determine whether the access unit is a
 * keyframe (IDR for H.264/HEVC, I-frame for MPEG-2). Every keyframe produces an {@link EpMap.EpMapEntry} with its PTS
 * (90 kHz) and SPN (source packet number).</li>
 * <li><b>Phase 3 – Assembly</b>: stream metadata and EP entries are composed into a {@link ClipInfo} model ready for
 * {@link org.brts.lowlevel.writer.ClipInfoWriter}.</li>
 * </ol>
 */
@Slf4j
public class M2tsClpiRegenBuilder {

	/** MPEG-2 PTS/PCR_base modulo in 90 kHz ticks (33-bit counter). */
	static final long PTS_MODULO = 1L << 33;

	/** PCR modulo in 27 MHz ticks ({@code PCR_base * 300 + PCR_ext}). */
	static final long PCR_27MHZ_MODULO = PTS_MODULO * 300L;

	/**
	 * Parses {@code m2tsPath} and builds a fully-populated {@link ClipInfo}.
	 *
	 * @param m2tsPath path to the {@code .m2ts} source file
	 * @param clipName 5-digit clip name without extension (e.g. {@code "00001"})
	 * @return populated {@link ClipInfo} ready for {@link org.brts.lowlevel.writer.ClipInfoWriter}
	 * @throws IOException on I/O error
	 */
	public ClipInfo build(Path m2tsPath, String clipName) throws IOException {
		log.info("Regenerating CLPI for clip '{}' from {}", clipName, m2tsPath);

		// Phase 1 – stream metadata from PAT/PMT
		M2tsInfo info = new M2tsParser().parse(m2tsPath);
		log.debug("M2TS stream info: {}",
				JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValueAsString(info));

		M2tsStreamInfo videoStream = findFirstVideoStream(info);
		if (videoStream == null) {
			log.warn("No video stream found in {}; CLPI will have empty EP map", m2tsPath.getFileName());
		}

		// Phase 2 – full-file scan for PTS range and IDR/I-frame entry points
		EpScanResult scan = doEpScan(m2tsPath, info, videoStream);

		// Phase 3 – assemble ClipInfo
		return assemble(clipName, info, videoStream, scan);
	}

	// =========================================================================
	// Phase 1 helpers
	// =========================================================================

	private static M2tsStreamInfo findFirstVideoStream(M2tsInfo info) {
		if (info.getStreams() == null)
			return null;
		return info.getStreams().stream().filter(s -> s.getCodingType() != null && s.getCodingType().isVideo())
				.findFirst().orElse(null);
	}

	// =========================================================================
	// Phase 2 – full-file IDR / I-frame scan
	// =========================================================================

	/** Accumulated results of the EP-map scan pass. */
	private static class EpScanResult {

		long firstPts = -1;

		long lastPts = -1;

		final List<EpMap.EpMapEntry> entries = new ArrayList<>();

	}

	private EpScanResult doEpScan(Path path, M2tsInfo info, M2tsStreamInfo videoStream) throws IOException {
		EpScanResult result = new EpScanResult();
		if (videoStream == null)
			return result;

		MonotonicTimestampUnwrapper pcrUnwrapper = new MonotonicTimestampUnwrapper(PCR_27MHZ_MODULO);
		MonotonicTimestampUnwrapper ptsUnwrapper = new MonotonicTimestampUnwrapper(PTS_MODULO);

		int videoPid = videoStream.getPid();
		StreamCodingType videoType = videoStream.getCodingType();

		Set<Integer> filter = new HashSet<>();
		filter.add(videoPid);
		filter.add(info.getPcrPid());
		// reset PCR and re-calculate from scratch during the EP scan
		// since the M2tsInfo holds only results for a 50000 packets scan.
		info.setFirstPcr27MHz(-1);
		info.setLastPcr27MHz(-1);

		new M2tsDemuxer().demux(path, info, new M2tsPacketHandler() {

			@Override
			public void onPayload(int pid, byte[] sp, int offset, int length, boolean payloadUnitStart,
					long packetIndex, long ats) {

				if (pid == info.getPcrPid()) {
					long pcr = pcrUnwrapper.unwrap(M2tsParser.extractPcr(sp));
					if (pcr >= 0) {
						if (info.getFirstPcr27MHz() < 0 || pcr < info.getFirstPcr27MHz())
							info.setFirstPcr27MHz(pcr);
						if (pcr > info.getLastPcr27MHz())
							info.setLastPcr27MHz(pcr);
					}
				}
				if (!payloadUnitStart)
					return;
				// Need at least 14 bytes: 3 start-code + stream_id + 2 length +
				// 2 flags + 1 header-data-len + 5 PTS bytes.
				if (offset + 14 > sp.length)
					return;

				// Validate PES start code: 00 00 01
				if ((sp[offset] & 0xFF) != 0x00 || (sp[offset + 1] & 0xFF) != 0x00 || (sp[offset + 2] & 0xFF) != 0x01)
					return;

				// PTS_DTS_flags in byte 7, bits 7-6; 0 means no PTS
				int ptsDtsFlags = (sp[offset + 7] >> 6) & 0x03;
				if (ptsDtsFlags == 0)
					return;

				long pts = ptsUnwrapper.unwrap(decodePts(sp, offset + 9));
				if (pts < 0)
					return;

				if (result.firstPts < 0 || pts < result.firstPts)
					result.firstPts = pts;
				if (pts > result.lastPts)
					result.lastPts = pts;

				// ES data starts after the variable-length PES header
				int pesHeaderDataLen = sp[offset + 8] & 0xFF;
				int esOff = offset + 9 + pesHeaderDataLen;
				if (esOff >= offset + length)
					return;

				if (isKeyframe(videoType, sp, esOff, offset + length)) {
					EpMap.EpMapEntry e = new EpMap.EpMapEntry();
					e.setPtsTicks(pts);
					e.setSpn(packetIndex);
					result.entries.add(e);
					log.trace("EP entry: PTS={} SPN={}", pts, packetIndex);
				}
			}

			@Override
			public void close() {
				/* no resources to release */ }

		}, filter);

		log.info("EP scan complete: {} IDR entries, PTS range [{} .. {}]", result.entries.size(), result.firstPts,
				result.lastPts);
		return result;
	}

	// =========================================================================
	// Phase 3 – assemble the ClipInfo model
	// =========================================================================

	private static ClipInfo assemble(String clipName, M2tsInfo info, M2tsStreamInfo videoStream, EpScanResult scan) {
		ClipInfo clip = new ClipInfo();
		clip.setClipName(clipName);
		clip.setClipStreamType(1); // AV clip
		clip.setApplicationType(1); // Movie

		// Determine PTS range in 90 kHz ticks.
		// Prefer PTS extracted from PES headers; fall back to PCR-derived values.
		long firstPts;
		long lastPts;
		log.debug("PES PTS range: [{} .. {}]", scan.firstPts, scan.lastPts);
		if (scan.firstPts >= 0 && scan.lastPts >= 0) {
			firstPts = scan.firstPts;
			lastPts = scan.lastPts;
		} else if (info.getFirstPcr27MHz() >= 0 && info.getLastPcr27MHz() >= 0) {
			// PCR is at 27 MHz; 90 kHz = 27 MHz / 300
			firstPts = info.getFirstPcr27MHz() / 300;
			lastPts = info.getLastPcr27MHz() / 300;
		} else {
			firstPts = 0;
			lastPts = 0;
		}

		clip.setTsRecordingStartPts(Timestamp.ofTicks(firstPts));
		clip.setTsRecordingEndPts(Timestamp.ofTicks(lastPts));
		clip.setDuration(Timestamp.ofTicks(Math.max(0L, lastPts - firstPts)));

		clip.setStreams(buildClipStreams(info.getStreams()));

		clip.setNumSourcePackets(info.getTotalPackets());
		// TODO find what this should be
		// real-life values seem to often be 6000000
		clip.setTsRecordingRate(6_000_000);

		// EP map — one stream entry for the primary video PID
		if (videoStream != null && !scan.entries.isEmpty()) {
			EpMap.EpMapStream eps = new EpMap.EpMapStream();
			eps.setPid(videoStream.getPid());
			eps.setEpType(1); // I-frame only (standard)
			eps.setEntries(new ArrayList<>(scan.entries));

			EpMap epMap = new EpMap();
			epMap.setStreams(List.of(eps));
			clip.setEpMap(epMap);
		}

		return clip;
	}

	private static List<ClipStream> buildClipStreams(List<M2tsStreamInfo> streams) {
		List<ClipStream> out = new ArrayList<>();
		if (streams == null)
			return out;
		for (M2tsStreamInfo si : streams) {
			if (si.getCodingType() == null)
				continue;
			ClipStream cs = new ClipStream();
			cs.setPid(si.getPid());
			cs.setCodingType(si.getCodingType());
			if (si.getCodingType().isVideo()) {
				// videoFormat and frameRate codes come directly from the HDMV
				// registration descriptor parsed by M2tsParser.
				cs.setVideoFormat(si.getVideoFormat() != null ? si.getVideoFormat() : 6); // default
																							// 1080p
				cs.setFrameRate(si.getFrameRate() != null ? si.getFrameRate() : 2); // default
																					// 24
																					// fps
				cs.setAspectRatio(si.getAspectRatio() != null ? si.getAspectRatio() : 3); // default
																							// 16:9
			} else if (si.getCodingType().isAudio()) {
				cs.setAudioChannelLayout(AudioChannelLayoutConverter.channelsToLayout(si.getChannels()));
				cs.setSampleRate(sampleRateToCode(si.getSampleRateHz()));
				cs.setLanguage(si.getLanguage() != null ? si.getLanguage() : "und");
			} else {
				// PGS / IG / text subtitle
				if (si.getCodingType() == StreamCodingType.TEXT_SUBTITLE) {
					cs.setCharacterCode(0); // UTF-8 default
				}
				cs.setLanguage(si.getLanguage() != null ? si.getLanguage() : "und");
			}
			out.add(cs);
		}
		return out;
	}

	// =========================================================================
	// PES and bitstream helpers (package-private for unit testing)
	// =========================================================================

	/**
	 * Converts a modulo counter into a monotonic timeline by counting wrap-arounds.
	 */
	static final class MonotonicTimestampUnwrapper {

		private final long modulo;

		private long wraps;

		private long lastRaw = -1;

		MonotonicTimestampUnwrapper(long modulo) {
			if (modulo <= 0)
				throw new IllegalArgumentException("modulo must be > 0");
			this.modulo = modulo;
		}

		long unwrap(long raw) {
			if (raw < 0)
				return -1;
			// A large backward jump means the source counter wrapped.
			if (lastRaw >= 0 && raw < lastRaw && (lastRaw - raw) > (modulo / 2)) {
				wraps++;
			}
			lastRaw = raw;
			return raw + wraps * modulo;
		}

	}

	/**
	 * Decodes the 33-bit PTS value from the 5-byte PES PTS field starting at {@code buf[off]}. Returns {@code -1} when
	 * the buffer does not contain enough bytes.
	 */
	static long decodePts(byte[] buf, int off) {
		if (off + 5 > buf.length)
			return -1;
		return (((long) (buf[off] & 0x0E)) << 29) | (((long) (buf[off + 1] & 0xFF)) << 22)
				| (((long) (buf[off + 2] & 0xFE)) << 14) | (((long) (buf[off + 3] & 0xFF)) << 7)
				| (((long) (buf[off + 4] & 0xFE)) >> 1);
	}

	/**
	 * Returns {@code true} when the ES data starting at {@code esOff} is a keyframe access unit for the given
	 * {@code codingType}:
	 * <ul>
	 * <li>H.264: first significant NAL unit (after AUD/SPS/PPS) has type 5 (IDR_slice).</li>
	 * <li>HEVC: first significant NAL unit has type 19 (IDR_W_RADL) or 20 (IDR_N_LP).</li>
	 * <li>MPEG-2: a picture start code is found with {@code picture_coding_type == 1} (I-frame).</li>
	 * </ul>
	 */
	static boolean isKeyframe(StreamCodingType codingType, byte[] buf, int esOff, int endOff) {
		if (codingType == null || esOff >= endOff)
			return false;
		return switch (codingType) {
		case H264_AVC -> isH264Idr(buf, esOff, endOff);
		case H265_HEVC -> isHevcIdr(buf, esOff, endOff);
		case MPEG2_VIDEO -> isMpeg2Iframe(buf, esOff, endOff);
		default -> false;
		};
	}

	/**
	 * H.264 Annex-B: scans NAL units up to {@code start + 512} bytes. Skips AUD (type 9), SPS (type 7), and PPS (type
	 * 8); returns {@code true} when the first remaining NAL has type 5 (IDR_slice).
	 */
	private static boolean isH264Idr(byte[] buf, int start, int end) {
		int maxScan = Math.min(end, start + 512);
		int pos = start;
		while (pos < maxScan - 3) {
			int sc = findStartCode(buf, pos, maxScan);
			if (sc < 0)
				break;
			int nalOff = nalByteOffset(buf, sc);
			if (nalOff >= maxScan)
				break;
			int nalType = buf[nalOff] & 0x1F; // H.264: lower 5 bits of NAL header byte
			switch (nalType) {
			case 9, 7, 8 -> pos = nalOff + 1; // AUD / SPS / PPS: continue scanning
			case 5 -> {
				return true;
			} // IDR slice
			default -> {
				return false;
			} // non-IDR: P/B/... frame
			}
		}
		return false;
	}

	/**
	 * HEVC Annex-B: scans NAL units up to {@code start + 512} bytes. Skips AUD (35), VPS (32), SPS (33), PPS (34), and
	 * PREFIX_SEI (39); returns {@code true} for type 19 (IDR_W_RADL) or 20 (IDR_N_LP).
	 */
	private static boolean isHevcIdr(byte[] buf, int start, int end) {
		int maxScan = Math.min(end, start + 512);
		int pos = start;
		while (pos < maxScan - 3) {
			int sc = findStartCode(buf, pos, maxScan);
			if (sc < 0)
				break;
			int nalOff = nalByteOffset(buf, sc);
			if (nalOff + 1 >= maxScan)
				break;
			// HEVC 2-byte NAL header: nal_unit_type = (first_byte & 0x7E) >> 1
			int nalType = (buf[nalOff] & 0x7E) >> 1;
			switch (nalType) {
			case 35, 32, 33, 34, 39 -> pos = nalOff + 2; // skip infra NALs
			case 19, 20 -> {
				return true;
			} // IDR_W_RADL / IDR_N_LP
			default -> {
				return false;
			} // non-IDR
			}
		}
		return false;
	}

	/**
	 * MPEG-2: returns {@code true} when a picture start code ({@code 00 00 01 00}) is found with
	 * {@code picture_coding_type == 1} (I-frame).
	 */
	private static boolean isMpeg2Iframe(byte[] buf, int start, int end) {
		int maxScan = Math.min(end, start + 256);
		for (int i = start; i <= maxScan - 6; i++) {
			if ((buf[i] & 0xFF) == 0x00 && (buf[i + 1] & 0xFF) == 0x00 && (buf[i + 2] & 0xFF) == 0x01
					&& (buf[i + 3] & 0xFF) == 0x00) {
				// picture_coding_type occupies bits 5-3 of byte i+5:
				// byte i+4: temporal_reference[9:2]
				// byte i+5: temporal_reference[1:0] | picture_coding_type[2:0] |
				// vbv_delay[15:13]
				int picType = (buf[i + 5] >> 3) & 0x07;
				return picType == 1; // 1 = I-frame
			}
		}
		return false;
	}

	/**
	 * Finds the next Annex-B start code ({@code 00 00 01} or {@code 00 00 00 01}) at or after {@code from}, returning
	 * the index of the first zero byte, or {@code -1} if none is found before {@code end}.
	 */
	private static int findStartCode(byte[] buf, int from, int end) {
		for (int i = from; i < end - 2; i++) {
			if ((buf[i] & 0xFF) == 0x00 && (buf[i + 1] & 0xFF) == 0x00) {
				if ((buf[i + 2] & 0xFF) == 0x01)
					return i;
				if (i + 3 < end && (buf[i + 2] & 0xFF) == 0x00 && (buf[i + 3] & 0xFF) == 0x01)
					return i;
			}
		}
		return -1;
	}

	/**
	 * Returns the byte offset of the first NAL header byte after the start code whose first zero byte is at {@code sc}.
	 * Handles both 3-byte ({@code 00 00 01}) and 4-byte ({@code 00 00 00 01}) start codes.
	 */
	private static int nalByteOffset(byte[] buf, int sc) {
		// 4-byte start code: buf[sc..sc+3] = 00 00 00 01 → NAL at sc+4
		if ((buf[sc + 2] & 0xFF) == 0x00)
			return sc + 4;
		// 3-byte start code: buf[sc..sc+2] = 00 00 01 → NAL at sc+3
		return sc + 3;
	}

	// =========================================================================
	// Blu-ray attribute code mappings
	// =========================================================================

	/**
	 * Maps a sample rate in Hz to the Blu-ray {@code audio_sample_rate} code used in CLPI. Defaults to {@code 0x01} (48
	 * kHz) for unrecognised values.
	 */
	private static int sampleRateToCode(Integer sampleRateHz) {
		if (sampleRateHz == null)
			return 0x01; // 48 kHz default
		return switch (sampleRateHz) {
		case 96000 -> 0x04;
		case 192000 -> 0x05;
		default -> 0x01; // 48 kHz
		};
	}

}
