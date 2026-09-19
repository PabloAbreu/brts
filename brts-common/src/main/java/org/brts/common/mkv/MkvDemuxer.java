package org.brts.common.mkv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/mkv/MkvDemuxer.java' is part of BRTS.
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

import org.brts.common.utils.AnnexBUtils;
import org.brts.common.utils.StringUtils;
import org.ebml.io.FileDataSource;
import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Demuxes an MKV (Matroska) container into separate elementary stream (ES) files, one per track.
 * <p>
 * For H.264 (AVC) tracks stored in MKV's "avcC" codec-private format, the demuxer prepends the SPS/PPS from the codec
 * private data and converts length-prefixed NAL units to Annex B start codes (00 00 00 01).
 * <p>
 * For other codecs (audio, HEVC, PGS, text subtitles), raw frame data is written sequentially.
 * <p>
 * Usage:
 *
 * <pre>{@code
 * MkvDemuxer demuxer = new MkvDemuxer();
 * Map<Integer, Path> files = demuxer.demux(mkvPath, outputDir);
 * // files maps track number → extracted ES file path
 * }</pre>
 */
@Slf4j
public class MkvDemuxer implements EsDemuxer {

	private static final byte[] ANNEX_B_START_CODE = { 0x00, 0x00, 0x00, 0x01 };
	private static final long SRT_FALLBACK_DURATION_MS = 2_000L;

	/**
	 * Demuxes all tracks from the given MKV file into separate ES files.
	 *
	 * @param mkvPath   path to the MKV file
	 * @param outputDir directory where ES files are written
	 * @return map from track number to the extracted ES file path
	 * @throws IOException on I/O error
	 */
	public Map<Integer, Path> demux(Path mkvPath, Path outputDir) throws IOException {
		return demux(mkvPath, outputDir, null);
	}

	/**
	 * Demuxes selected tracks from the given MKV file into separate ES files.
	 *
	 * @param mkvPath     path to the MKV file
	 * @param outputDir   directory where ES files are written
	 * @param trackFilter set of track numbers to extract (null = all)
	 * @return map from track number to the extracted ES file path
	 * @throws IOException on I/O error
	 */
	public Map<Integer, Path> demux(Path mkvPath, Path outputDir, Set<Integer> trackFilter) throws IOException {
		Files.createDirectories(outputDir);
		Map<Integer, Path> result = new LinkedHashMap<>();
		Map<Integer, PendingSrtCue> pendingSrtCues = new HashMap<>();
		Map<Integer, Integer> srtCueCounter = new HashMap<>();

		try (FileDataSource dataSource = new FileDataSource(mkvPath.toAbsolutePath().toString())) {
			MatroskaFile mkv = new MatroskaFile(dataSource);
			mkv.readFile();

			// Build track metadata map
			Map<Integer, MatroskaFileTrack> trackMap = new LinkedHashMap<>();
			Map<Integer, Integer> nalLengthSizeMap = new HashMap<>();
			MatroskaFileTrack[] tracks = mkv.getTrackList();
			if (tracks != null) {
				for (MatroskaFileTrack t : tracks) {
					int trackNo = t.getTrackNo();
					if (trackFilter != null && !trackFilter.contains(trackNo)) {
						continue;
					}
					trackMap.put(trackNo, t);

					// For H.264 avcC format, parse the NAL length size
					String codecId = t.getCodecID();
					if ("V_MPEG4/ISO/AVC".equals(codecId)) {
						ByteBuffer cp = t.getCodecPrivate();
						if (cp != null && cp.remaining() >= 6) {
							ByteBuffer dup = cp.duplicate();
							dup.get(); // configVersion
							dup.get(); // profile
							dup.get(); // compat
							dup.get(); // level
							nalLengthSizeMap.put(trackNo, (dup.get() & 0x03) + 1);
						} else {
							nalLengthSizeMap.put(trackNo, 4);
						}
					}
				}
			}

			// Open output streams for each track
			Map<Integer, OutputStream> outputs = new LinkedHashMap<>();
			try {
				for (var entry : trackMap.entrySet()) {
					int trackNo = entry.getKey();
					MatroskaFileTrack t = entry.getValue();
					String ext = extensionForCodec(t.getCodecID());
					Path outFile = outputDir.resolve("track_" + trackNo + "." + ext);
					result.put(trackNo, outFile);
					OutputStream os = new BufferedOutputStream(Files.newOutputStream(outFile), 1 << 16);
					outputs.put(trackNo, os);

					// For H.264 tracks, write SPS/PPS from codec private data first
					if ("V_MPEG4/ISO/AVC".equals(t.getCodecID())) {
						writeAvcParameterSets(t.getCodecPrivate(), os);
					} else if (("S_TEXT/ASS".equals(t.getCodecID()) || "S_TEXT/SSA".equals(t.getCodecID()))
							&& t.getCodecPrivate() != null && t.getCodecPrivate().hasRemaining()) {
						byte[] buf = new byte[t.getCodecPrivate().remaining()];
						t.getCodecPrivate().get(buf);
						os.write(buf);
					} else
						log.debug("Demuxing track {} (codec={}, ext={}) → {} . codec private data :\n {}", trackNo,
								t.getCodecID(), ext, outFile.getFileName(),
								StringUtils.bytesToHex(t.getCodecPrivate()));
				}

				// Read all frames
				long frameCount = 0;
				MatroskaFileFrame frame;
				while ((frame = mkv.getNextFrame()) != null) {
					int trackNo = frame.getTrackNo();
					OutputStream os = outputs.get(trackNo);
					if (os == null) {
						log.trace("Skipping frame for track {} (not in filter)", trackNo);
						continue;
					}

					ByteBuffer data = frame.getData();
					if (data == null || !data.hasRemaining())
						continue;

					MatroskaFileTrack track = trackMap.get(trackNo);
					String codecID = track.getCodecID();
					if ("V_MPEG4/ISO/AVC".equals(codecID)) {
						// Convert length-prefixed NALUs to Annex B
						int nalLenSize = nalLengthSizeMap.getOrDefault(trackNo, 4);
						writeAnnexBFrame(data, nalLenSize, os);
					} else if (codecID.equals("S_TEXT/ASS") || codecID.equals("S_TEXT/SSA")) {
						// Text subtitles: write raw UTF-8 text
						byte[] buf = new byte[data.remaining()];
						data.get(buf);
						if (!StringUtils.startsWith(buf, SSA_DIALOGUE)) {
							os.write(SSA_DIALOGUE);
							int pos = StringUtils.pos(buf, (byte) ',', 2);
							os.write('0');
							os.write(',');
							long start = frame.getTimecode();
							long end = start + frame.getDuration();
							os.write(timestampFrom(start).getBytes());
							os.write(',');
							os.write(timestampFrom(end).getBytes());
							os.write(buf, pos, buf.length - pos);
							os.write('\n');
						} else {
							os.write(buf);
						}
					} else if (codecID.equals("S_TEXT/UTF8")) {
						byte[] buf = new byte[data.remaining()];
						data.get(buf);
						String text = new String(buf, StandardCharsets.UTF_8);
						if (text.trim().isEmpty()) {
							continue;
						}

						long start = frame.getTimecode();
						long duration = frame.getDuration();
						long fallbackEnd = start + (duration > 0 ? duration : SRT_FALLBACK_DURATION_MS);

						PendingSrtCue previous = pendingSrtCues.get(trackNo);
						if (previous != null) {
							writeSrtCue(os, previous.number, previous.startMs, previous.endMs, previous.text, start);
						}

						int cueNumber = srtCueCounter.getOrDefault(trackNo, 0) + 1;
						srtCueCounter.put(trackNo, cueNumber);
						pendingSrtCues.put(trackNo, new PendingSrtCue(cueNumber, start, fallbackEnd, text));
					} else {
						// Raw copy
						byte[] buf = new byte[data.remaining()];
						data.get(buf);
						os.write(buf);
					}
					frameCount++;
				}

				for (Map.Entry<Integer, PendingSrtCue> entry : pendingSrtCues.entrySet()) {
					OutputStream os = outputs.get(entry.getKey());
					PendingSrtCue cue = entry.getValue();
					if (os == null || cue == null) {
						continue;
					}
					writeSrtCue(os, cue.number, cue.startMs, cue.endMs, cue.text, null);
				}

				log.info("Demuxed {} frames from {} tracks in {}", frameCount, outputs.size(), mkvPath.getFileName());
			} finally {
				for (OutputStream os : outputs.values()) {
					os.close();
				}
			}
		}

		return result;
	}

	private String timestampFrom(long timecode) {
		long totalSeconds = timecode / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		long milliseconds = timecode % 1000;
		milliseconds = milliseconds / 10;// displayed with 2 digits, so convert
		return String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, milliseconds);
	}

	private static final byte[] SSA_DIALOGUE = "Dialogue: ".getBytes();

	private void writeSrtCue(OutputStream os, int cueNumber, long startMs, long endMs, String text, Long nextStartMs)
			throws IOException {
		long safeEndMs = endMs;
		if (nextStartMs != null) {
			safeEndMs = Math.min(safeEndMs, nextStartMs.longValue());
		}
		if (safeEndMs < startMs) {
			safeEndMs = startMs;
		}

		os.write(Integer.toString(cueNumber).getBytes(StandardCharsets.UTF_8));
		os.write('\n');
		os.write(srtTimestampFrom(startMs).getBytes(StandardCharsets.UTF_8));
		os.write(" --> ".getBytes(StandardCharsets.UTF_8));
		os.write(srtTimestampFrom(safeEndMs).getBytes(StandardCharsets.UTF_8));
		os.write('\n');
		os.write(text.getBytes(StandardCharsets.UTF_8));
		os.write('\n');
		os.write('\n');
	}

	private static String srtTimestampFrom(long timecode) {
		long totalSeconds = timecode / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		long milliseconds = timecode % 1000;
		return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds);
	}

	private static final class PendingSrtCue {
		private final int number;
		private final long startMs;
		private final long endMs;
		private final String text;

		private PendingSrtCue(int number, long startMs, long endMs, String text) {
			this.number = number;
			this.startMs = startMs;
			this.endMs = endMs;
			this.text = text;
		}
	}

	/**
	 * Writes SPS and PPS NAL units from the AVCDecoderConfigurationRecord (codec private data) using Annex B start
	 * codes.
	 */
	private void writeAvcParameterSets(ByteBuffer codecPrivate, OutputStream os) throws IOException {
		if (codecPrivate == null || codecPrivate.remaining() < 7)
			return;
		ByteBuffer cp = codecPrivate.duplicate();

		cp.get(); // configurationVersion
		cp.get(); // AVCProfileIndication
		cp.get(); // profile_compatibility
		cp.get(); // AVCLevelIndication
		cp.get(); // lengthSizeMinusOne (lower 2 bits)

		// SPS
		int numSps = cp.get() & 0x1F;
		for (int i = 0; i < numSps; i++) {
			int spsLen = (cp.get() & 0xFF) << 8 | (cp.get() & 0xFF);
			byte[] sps = new byte[spsLen];
			cp.get(sps);
			os.write(ANNEX_B_START_CODE);
			os.write(sps);
		}

		// PPS
		if (!cp.hasRemaining())
			return;
		int numPps = cp.get() & 0xFF;
		for (int i = 0; i < numPps; i++) {
			int ppsLen = (cp.get() & 0xFF) << 8 | (cp.get() & 0xFF);
			byte[] pps = new byte[ppsLen];
			cp.get(pps);
			os.write(ANNEX_B_START_CODE);
			os.write(pps);
		}
	}

	/**
	 * Converts a length-prefixed NAL unit frame to Annex B format, or passes through data that is already in Annex B
	 * format (starts with a 3- or 4-byte start code).
	 *
	 * <p>
	 * Some MKV files with codec ID {@code V_MPEG4/ISO/AVC} store H.264 data in Annex B format (start-code delimited)
	 * rather than the spec-mandated AVCC (length-prefixed) format. Misidentifying such frames as AVCC leads to
	 * near-total data loss: the start code {@code 00 00 00 01} is read as nalLen=1, only one byte is written, and the
	 * following byte (e.g. {@code F0} from the AUD primary_pic_type) produces a negative Java int nalLen which triggers
	 * the safety break.
	 */
	private void writeAnnexBFrame(ByteBuffer data, int nalLenSize, OutputStream os) throws IOException {
		ByteBuffer buf = data.duplicate();
		if (!buf.hasRemaining())
			return;

		// If the frame already carries Annex B start codes, pass it through as-is.
		if (AnnexBUtils.isAnnexBFormat(buf)) {
			byte[] raw = new byte[buf.remaining()];
			buf.get(raw);
			os.write(raw);
			return;
		}

		// AVCC → Annex B conversion: each NAL is preceded by a nalLenSize-byte length
		// prefix.
		while (buf.remaining() >= nalLenSize) {
			int nalLen = 0;
			for (int i = 0; i < nalLenSize; i++) {
				nalLen = (nalLen << 8) | (buf.get() & 0xFF);
			}
			if (nalLen <= 0 || nalLen > buf.remaining())
				break;
			byte[] nalData = new byte[nalLen];
			buf.get(nalData);
			os.write(ANNEX_B_START_CODE);
			os.write(nalData);
		}
	}

	/**
	 * Maps a Matroska codec ID to an appropriate file extension.
	 */
	static String extensionForCodec(String codecId) {
		if (codecId == null)
			return "bin";
		return switch (codecId) {
		case "V_MPEG4/ISO/AVC" -> "h264";
		case "V_MPEGH/ISO/HEVC" -> "h265";
		case "V_MPEG2" -> "m2v";
		case "V_MS/VFW/FOURCC" -> "vc1";
		case "A_AC3" -> "ac3";
		case "A_EAC3" -> "eac3";
		case "A_TRUEHD" -> "thd";
		case "A_DTS" -> "dts";
		case "A_DTS/HD/MA" -> "dtsma";
		case "A_DTS/HD/HRA" -> "dtshd";
		case "A_PCM/INT/BIG" -> "lpcm";
		case "S_HDMV/PGS" -> "pgs";
		case "S_TEXT/UTF8" -> "srt";
		case "S_TEXT/ASS" -> "ass";
		case "S_TEXT/SSA" -> "ssa";
		default -> "bin";
		};
	}

}
