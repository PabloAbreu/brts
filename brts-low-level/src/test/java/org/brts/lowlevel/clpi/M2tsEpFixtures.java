/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/clpi/M2tsEpFixtures.java' is part of BRTS.
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

package org.brts.lowlevel.clpi;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/** Small, deterministic PES and 192-byte source packet fixtures, with no native media dependency. */
final class M2tsEpFixtures {

	static final int VIDEO_PID = 0x1011;

	static final int PCR_PID = 0x1001;

	private M2tsEpFixtures() {
	}

	static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++)
			result[i] = (byte) values[i];
		return result;
	}

	static byte[] join(byte[]... chunks) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] chunk : chunks)
			out.writeBytes(chunk);
		return out.toByteArray();
	}

	static byte[] hevc(int type) {
		return bytes(0, 0, 0, 1, type << 1, 1, 0x80);
	}

	static byte[] avc(int type) {
		return bytes(0, 0, 1, 0x60 | type, 0x80);
	}

	static byte[] mpeg(int type) {
		return bytes(0, 0, 1, 0, 0, type << 3);
	}

	static byte[] timestamp(long pts, int prefix) {
		return bytes((prefix << 4) | (int) ((pts >> 29) & 14) | 1, (int) (pts >> 22), (int) ((pts >> 14) & 0xFE) | 1,
				(int) (pts >> 7), (int) ((pts << 1) & 0xFE) | 1);
	}

	static byte[] pes(long pts, byte[] es) {
		return join(bytes(0, 0, 1, 0xE0, 0, 0, 0x80, pts >= 0 ? 0x80 : 0, pts >= 0 ? 5 : 0),
				pts >= 0 ? timestamp(pts, 2) : new byte[0], es);
	}

	static byte[] packet(int pid, boolean start, int counter, byte[] payload) {
		if (payload.length == 0 || payload.length > 184)
			throw new IllegalArgumentException("TS fixture payload must contain 1..184 bytes");
		byte[] packet = new byte[192];
		Arrays.fill(packet, (byte) 0xFF);
		Arrays.fill(packet, 0, 4, (byte) 0);
		packet[4] = 0x47;
		packet[5] = (byte) ((start ? 0x40 : 0) | (pid >> 8));
		packet[6] = (byte) pid;
		packet[7] = (byte) ((payload.length == 184 ? 0x10 : 0x30) | (counter & 15));
		if (payload.length < 184) {
			packet[8] = (byte) (183 - payload.length);
			if (payload.length < 183)
				packet[9] = 0;
		}
		System.arraycopy(payload, 0, packet, 192 - payload.length, payload.length);
		return packet;
	}

	static byte[] pcr(long ticks) {
		byte[] packet = new byte[192];
		packet[4] = 0x47;
		packet[5] = (byte) (PCR_PID >> 8);
		packet[6] = (byte) PCR_PID;
		packet[7] = 0x20;
		packet[8] = (byte) 183;
		packet[9] = 0x10;
		long base = ticks / 300;
		int ext = (int) (ticks % 300);
		packet[10] = (byte) (base >> 25);
		packet[11] = (byte) (base >> 17);
		packet[12] = (byte) (base >> 9);
		packet[13] = (byte) (base >> 1);
		packet[14] = (byte) (((base & 1) << 7) | 0x7E | (ext >> 8));
		packet[15] = (byte) ext;
		return packet;
	}

	static byte[] tables(int streamType) {
		return tables(streamType, VIDEO_PID);
	}

	static byte[] tables(int streamType, int... videoPids) {
		byte[] pat = bytes(0, 0xB0, 13, 0, 1, 0xC1, 0, 0, 0, 1, 0xE1, 0);
		int sectionLength = 13 + 5 * videoPids.length;
		byte[] pmt = bytes(2, 0xB0 | (sectionLength >> 8), sectionLength, 0, 1, 0xC1, 0, 0, 0xE0 | (PCR_PID >> 8),
				PCR_PID, 0xF0, 0);
		for (int pid : videoPids)
			pmt = join(pmt, bytes(streamType, 0xE0 | (pid >> 8), pid, 0xF0, 0));
		return join(packet(0, true, 0, join(bytes(0), crc(pat))), packet(0x100, true, 0, join(bytes(0), crc(pmt))));
	}

	private static byte[] crc(byte[] section) {
		int crc = -1;
		for (byte b : section) {
			crc ^= (b & 0xFF) << 24;
			for (int bit = 0; bit < 8; bit++)
				crc = (crc << 1) ^ (crc < 0 ? 0x04C11DB7 : 0);
		}
		return join(section, bytes(crc >> 24, crc >> 16, crc >> 8, crc));
	}

}
