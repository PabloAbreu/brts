package org.brts.lowlevel.pgs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/pgs/PgsSupReader.java' is part of BRTS.
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

import org.brts.lowlevel.igs.IgsSegmentType;
import org.brts.lowlevel.igs.model.IgsRawSegment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads a standalone PGS ".sup" file (as produced by BDSup2Sub, eac3to, mkvextract, etc.) into {@link IgsRawSegment}s
 * with PTS/DTS populated.
 * <p>
 * Each record in a ".sup" file is framed as: 2-byte magic {@code "PG"} + 32-bit PTS (90 kHz) + 32-bit DTS + 1-byte
 * segment type + 2-byte big-endian segment length + segment data. This is the same framing written by
 * {@link PgsMuxer#writeSegmentWithPts}.
 */
@Slf4j
public class PgsSupReader {

	private static final int MAGIC_P = 'P';

	private static final int MAGIC_G = 'G';

	private PgsSupReader() {
	}

	/**
	 * Parses a ".sup" file from disk.
	 *
	 * @param supFile path to the standalone PGS file
	 * @return ordered list of raw segments with real PTS/DTS
	 * @throws IOException on I/O error
	 */
	public static List<IgsRawSegment> parseSegments(Path supFile) throws IOException {
		return parseSegments(Files.readAllBytes(supFile));
	}

	/**
	 * Parses ".sup"-framed bytes into raw segments.
	 *
	 * @param data raw bytes of a ".sup" file
	 * @return ordered list of raw segments with real PTS/DTS
	 */
	public static List<IgsRawSegment> parseSegments(byte[] data) {
		List<IgsRawSegment> segments = new ArrayList<>();
		int pos = 0;
		while (pos + 13 <= data.length) {
			if ((data[pos] & 0xFF) != MAGIC_P || (data[pos + 1] & 0xFF) != MAGIC_G) {
				log.warn("Expected 'PG' magic at offset {}, found 0x{}{}, stopping", pos,
						Integer.toHexString(data[pos] & 0xFF), Integer.toHexString(data[pos + 1] & 0xFF));
				break;
			}
			long pts = readU32(data, pos + 2);
			long dts = readU32(data, pos + 6);
			int typeByte = data[pos + 10] & 0xFF;
			int segLen = ((data[pos + 11] & 0xFF) << 8) | (data[pos + 12] & 0xFF);
			pos += 13;

			if (pos + segLen > data.length) {
				log.warn("Truncated segment at offset {}: type=0x{}, declared len={}, remaining={}", pos - 13,
						Integer.toHexString(typeByte), segLen, data.length - pos);
				break;
			}

			IgsSegmentType type = IgsSegmentType.fromByte(typeByte);
			if (type == null) {
				log.warn("Unknown segment type 0x{} at offset {}, skipping {} bytes", Integer.toHexString(typeByte),
						pos - 13, segLen);
				pos += segLen;
				continue;
			}

			byte[] segData = new byte[segLen];
			System.arraycopy(data, pos, segData, 0, segLen);
			pos += segLen;

			IgsRawSegment seg = new IgsRawSegment();
			seg.setPts(pts);
			seg.setDts(dts);
			seg.setType(type);
			seg.setSegmentData(segData);
			segments.add(seg);
		}
		log.info("Parsed {} PGS segments from {} bytes", segments.size(), data.length);
		return segments;
	}

	private static long readU32(byte[] d, int pos) {
		return ((long) (d[pos] & 0xFF) << 24) | ((d[pos + 1] & 0xFF) << 16) | ((d[pos + 2] & 0xFF) << 8)
				| (d[pos + 3] & 0xFF);
	}

}
