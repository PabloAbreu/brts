package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/StringUtils.java' is part of BRTS.
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

import java.nio.ByteBuffer;

public class StringUtils {

	public static String bytesToHex(byte[] bytes, int offset, int length) {
		if (bytes == null)
			return "";
		final StringBuilder sb = new StringBuilder();
		final int max = Math.min(offset + length, bytes.length);
		for (int i = offset; i < max; i++) {
			sb.append(String.format("%02X ", bytes[i]));
		}
		return sb.toString().trim();
	}

	public static String bytesToHex(ByteBuffer buffer) {
		if (buffer == null)
			return "";
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytesToHex(bytes, 0, bytes.length);
	}

	public static boolean startsWith(byte[] buffer, byte[] prefix) {
		if (buffer == null || prefix == null || buffer.length < prefix.length) {
			return false;
		}
		for (int i = 0; i < prefix.length; i++) {
			if (buffer[i] != prefix[i]) {
				return false;
			}
		}
		return true;
	}

	public static int pos(byte[] buffer, byte searched, int numberOfOccurrences) {
		if (buffer == null || numberOfOccurrences <= 0) {
			return -1;
		}
		int count = 0;
		for (int i = 0; i < buffer.length; i++) {
			if (buffer[i] == searched) {
				count++;
				if (count == numberOfOccurrences) {
					return i;
				}
			}
		}
		return -1;
	}

}
