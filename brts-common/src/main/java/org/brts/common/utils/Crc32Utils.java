package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/Crc32Utils.java' is part of BRTS.
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

import java.util.Objects;

public final class Crc32Utils {

	private static final int POLYNOMIAL = 0x04C11DB7;

	private static final int INITIAL_VALUE = 0xFFFFFFFF;

	private static final int[] CRC_TABLE = createCrcTable();

	private Crc32Utils() {
	}

	public static long crc32(byte[] data, int offset, int length) {
		Objects.requireNonNull(data, "data");
		if (offset < 0 || length < 0 || offset > data.length - length) {
			throw new IndexOutOfBoundsException(
					"Invalid CRC range: offset=" + offset + ", length=" + length + ", dataLength=" + data.length);
		}

		int crc = INITIAL_VALUE;
		for (int index = offset; index < offset + length; index++) {
			crc = (crc << 8) ^ CRC_TABLE[((crc >>> 24) ^ (data[index] & 0xFF)) & 0xFF];
		}
		return crc & 0xFFFFFFFFL;
	}

	private static int[] createCrcTable() {
		int[] table = new int[256];
		for (int index = 0; index < table.length; index++) {
			int crc = index << 24;
			for (int bit = 0; bit < 8; bit++) {
				crc = (crc << 1) ^ ((crc < 0) ? POLYNOMIAL : 0);
			}
			table[index] = crc;
		}
		return table;
	}
}
