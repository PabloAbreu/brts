/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/PesTimestampParser.java' is part of BRTS.
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

package org.brts.common.m2ts;

/**
 * Decodes 33-bit PTS and DTS values from MPEG-2 PES headers. Returned timestamps are in 90 kHz ticks; {@code -1}
 * indicates that the requested timestamp is absent or truncated.
 */
public final class PesTimestampParser {

	private static final int PES_FIXED_HEADER_SIZE = 9;

	private static final int TIMESTAMP_SIZE = 5;

	private PesTimestampParser() {
	}

	/**
	 * Decodes a 5-byte PTS or DTS field at {@code offset}.
	 *
	 * @return the 33-bit timestamp, or {@code -1} if the field is truncated
	 */
	public static long decodeTimestamp(byte[] data, int offset) {
		if (!hasRange(data, offset, TIMESTAMP_SIZE))
			return -1;
		return (((long) (data[offset] & 0x0E)) << 29) | (((long) (data[offset + 1] & 0xFF)) << 22)
				| (((long) (data[offset + 2] & 0xFE)) << 14) | (((long) (data[offset + 3] & 0xFF)) << 7)
				| (((long) (data[offset + 4] & 0xFE)) >> 1);
	}

	/**
	 * Reads the PTS from a complete PES packet beginning at {@code offset}.
	 *
	 * @return the 33-bit PTS, or {@code -1} if PTS is absent or its header bytes are truncated
	 */
	public static long parsePts(byte[] data, int offset, int length) {
		if (!hasRange(data, offset, length) || length < PES_FIXED_HEADER_SIZE || !hasOptionalHeader(data, offset))
			return -1;

		int flags = (data[offset + 7] & 0xC0) >> 6;
		int headerDataLength = data[offset + 8] & 0xFF;
		if (flags < 2 || headerDataLength < TIMESTAMP_SIZE || length < PES_FIXED_HEADER_SIZE + headerDataLength)
			return -1;
		return decodeTimestamp(data, offset + PES_FIXED_HEADER_SIZE);
	}

	/**
	 * Reads the DTS from a complete PES packet beginning at {@code offset}.
	 *
	 * @return the 33-bit DTS, or {@code -1} if DTS is absent or its header bytes are truncated
	 */
	public static long parseDts(byte[] data, int offset, int length) {
		if (!hasRange(data, offset, length) || length < PES_FIXED_HEADER_SIZE || !hasOptionalHeader(data, offset))
			return -1;

		int flags = (data[offset + 7] & 0xC0) >> 6;
		int headerDataLength = data[offset + 8] & 0xFF;
		if (flags != 3 || headerDataLength < 2 * TIMESTAMP_SIZE || length < PES_FIXED_HEADER_SIZE + headerDataLength)
			return -1;
		return decodeTimestamp(data, offset + PES_FIXED_HEADER_SIZE + TIMESTAMP_SIZE);
	}

	private static boolean hasOptionalHeader(byte[] data, int offset) {
		return (data[offset + 6] & 0xC0) == 0x80;
	}

	private static boolean hasRange(byte[] data, int offset, int length) {
		return data != null && offset >= 0 && length >= 0 && offset <= data.length && length <= data.length - offset;
	}

}
