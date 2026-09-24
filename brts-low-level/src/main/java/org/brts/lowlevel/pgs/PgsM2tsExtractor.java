package org.brts.lowlevel.pgs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/pgs/PgsM2tsExtractor.java' is part of BRTS.
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

import org.brts.common.m2ts.M2tsDemuxer;
import org.brts.common.m2ts.M2tsPacketHandler;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.igs.model.IgsRawSegment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts a PGS (Presentation Graphics Stream) elementary stream from an M2TS file, preserving the per-segment PTS
 * carried in each PES packet header.
 * <p>
 * Unlike {@code IgsParser}/plain ES dumps (which discard PTS once the PES header is stripped), this class records, for
 * every PES packet boundary, the byte offset it starts at in the reconstructed elementary stream together with its PTS,
 * so that {@link #parseSegments(byte[], List)} can attribute a real PTS to every segment.
 */
@Slf4j
public class PgsM2tsExtractor {

	/** A PES packet boundary: the offset in the reconstructed ES buffer where it starts, and its PTS. */
	public record PtsBoundary(int esOffset, long pts) {
	}

	private PgsM2tsExtractor() {
	}

	/**
	 * Lists all Presentation Graphics streams found in the given M2TS.
	 */
	public static List<M2tsStreamInfo> listPgsStreams(M2tsInfo info) {
		List<M2tsStreamInfo> pgs = new ArrayList<>();
		for (M2tsStreamInfo s : info.getStreams()) {
			if (s.getCodingType() == StreamCodingType.PRESENTATION_GRAPHICS) {
				pgs.add(s);
			}
		}
		return pgs;
	}

	/**
	 * Demuxes the PGS elementary stream at {@code pid} into memory, recording PES boundary PTS values.
	 *
	 * @param m2tsFile source M2TS file
	 * @param info     pre-parsed stream metadata for {@code m2tsFile}
	 * @param pid      PID of the PGS stream to extract
	 * @return the raw ES bytes (PES headers stripped, segments concatenated) and their PTS boundary map
	 * @throws IOException on I/O error
	 */
	public static EsData demuxToMemory(Path m2tsFile, M2tsInfo info, int pid) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
		List<PtsBoundary> boundaries = new ArrayList<>();

		new M2tsDemuxer().demux(m2tsFile, info, new M2tsPacketHandler() {
			@Override
			public void onPayload(int p, byte[] payload, int offset, int length, boolean payloadUnitStart,
					long packetIndex, long ats) throws IOException {
				int payloadOff = offset;
				int payloadLen = length;

				if (payloadUnitStart && payloadLen > 8 && payload[payloadOff] == 0x00 && payload[payloadOff + 1] == 0x00
						&& payload[payloadOff + 2] == 0x01) {
					long pts = readPesPts(payload, payloadOff, payloadLen);
					boundaries.add(new PtsBoundary(out.size(), pts));

					int pesHeaderLen = (payload[payloadOff + 8] & 0xFF) + 9;
					payloadOff += pesHeaderLen;
					payloadLen = length - (payloadOff - offset);
					if (payloadLen <= 0)
						return;
				}

				out.write(payload, payloadOff, payloadLen);
			}

			@Override
			public void close() {
			}
		}, Set.of(pid));

		log.info("Demuxed {} bytes of PGS data from PID 0x{} ({} PES boundaries)", out.size(), Integer.toHexString(pid),
				boundaries.size());
		return new EsData(out.toByteArray(), boundaries);
	}

	/** Bare ES bytes plus the PES-boundary-to-PTS map recorded while demuxing. */
	public record EsData(byte[] esBytes, List<PtsBoundary> boundaries) {
	}

	/**
	 * Parses bare segment bytes (as produced by {@link #demuxToMemory}), attributing to each segment the PTS of the PES
	 * boundary it starts at.
	 *
	 * @param data       raw segment bytes (type + length + data, concatenated)
	 * @param boundaries PES boundary PTS map, ordered by ascending {@code esOffset}
	 * @return ordered list of raw segments with PTS resolved
	 */
	public static List<IgsRawSegment> parseSegments(byte[] data, List<PtsBoundary> boundaries) {
		List<IgsRawSegment> segments = org.brts.lowlevel.igs.IgsParser.parseSegments(data);

		// Re-walk the same byte layout to recover each segment's starting offset and attribute PTS.
		int pos = 0;
		int boundaryIdx = 0;
		long currentPts = -1;
		for (IgsRawSegment seg : segments) {
			while (boundaryIdx < boundaries.size() && boundaries.get(boundaryIdx).esOffset() <= pos) {
				currentPts = boundaries.get(boundaryIdx).pts();
				boundaryIdx++;
			}
			seg.setPts(currentPts);
			pos += 3 + seg.getSegmentData().length;
		}
		return segments;
	}

	/**
	 * Reads the PTS from a PES header (returns -1 if not present).
	 * <p>
	 * Layout: bytes[0-2] = {@code 00 00 01} start code, byte[3] = stream id, bytes[4-5] = PES_packet_length, byte[6] =
	 * flags1, byte[7] = flags2 (bits 7-6 = PTS_DTS_flags), byte[8] = PES_header_data_length, bytes[9-13] = PTS (present
	 * when PTS_DTS_flags != 00).
	 */
	private static long readPesPts(byte[] payload, int offset, int length) {
		if (length < 14)
			return -1;
		int ptsDtsFlags = (payload[offset + 7] & 0xC0) >> 6;
		if (ptsDtsFlags == 0)
			return -1;

		int b0 = payload[offset + 9] & 0xFF;
		int b1 = payload[offset + 10] & 0xFF;
		int b2 = payload[offset + 11] & 0xFF;
		int b3 = payload[offset + 12] & 0xFF;
		int b4 = payload[offset + 13] & 0xFF;

		return ((long) (b0 & 0x0E) << 29) | ((long) b1 << 22) | ((long) (b2 & 0xFE) << 14) | ((long) b3 << 7)
				| ((b4 & 0xFE) >> 1);
	}

}
