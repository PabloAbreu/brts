package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/m2ts/M2tsPacketUtils.java' is part of BRTS.
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
import java.util.Arrays;
import java.util.Map;

final class M2tsPacketUtils {

	static final int TS_PACKET_SIZE = 188;

	private static final int SYNC_BYTE = 0x47;

	private M2tsPacketUtils() {
	}

	@FunctionalInterface
	interface TsPacketWriter {
		void write(byte[] tsPacket, int packetIndex) throws IOException;
	}

	static int writePesToTs(byte[] pes, int pid, Map<Integer, Integer> continuityCounters, TsPacketWriter writer)
			throws IOException {
		int tsCount = 0;
		int offset = 0;
		boolean first = true;

		while (offset < pes.length) {
			int remaining = pes.length - offset;
			int payloadCapacity = TS_PACKET_SIZE - 4;
			int chunkLen = Math.min(remaining, payloadCapacity);
			byte[] ts = new byte[TS_PACKET_SIZE];
			ts[0] = (byte) SYNC_BYTE;
			ts[1] = (byte) ((first ? 0x40 : 0x00) | ((pid >> 8) & 0x1F));
			ts[2] = (byte) (pid & 0xFF);
			int continuityCounter = nextContinuityCounter(continuityCounters, pid);

			if (chunkLen < payloadCapacity) {
				int stuffingLength = payloadCapacity - chunkLen;
				ts[3] = (byte) (0x30 | continuityCounter);
				if (stuffingLength == 1) {
					ts[4] = 0x00;
					System.arraycopy(pes, offset, ts, 5, chunkLen);
				} else {
					ts[4] = (byte) (stuffingLength - 1);
					ts[5] = 0x00;
					Arrays.fill(ts, 6, 4 + stuffingLength, (byte) 0xFF);
					System.arraycopy(pes, offset, ts, 4 + stuffingLength, chunkLen);
				}
			} else {
				ts[3] = (byte) (0x10 | continuityCounter);
				System.arraycopy(pes, offset, ts, 4, chunkLen);
			}

			writer.write(ts, tsCount);
			tsCount++;
			offset += chunkLen;
			first = false;
		}

		return tsCount;
	}

	static byte[] buildTsPacket(int pid, boolean pusi, int continuityCounter, byte[] payload) {
		byte[] ts = new byte[TS_PACKET_SIZE];
		ts[0] = (byte) SYNC_BYTE;
		ts[1] = (byte) ((pusi ? 0x40 : 0x00) | ((pid >> 8) & 0x1F));
		ts[2] = (byte) (pid & 0xFF);
		ts[3] = (byte) (0x10 | (continuityCounter & 0x0F));

		int copyLength = Math.min(payload.length, TS_PACKET_SIZE - 4);
		System.arraycopy(payload, 0, ts, 4, copyLength);
		Arrays.fill(ts, 4 + copyLength, TS_PACKET_SIZE, (byte) 0xFF);
		return ts;
	}

	static byte[] buildPcrPacket(int pid, long pcr27, Map<Integer, Integer> continuityCounters) {
		long pcrBase = pcr27 / 300;
		long pcrExt = pcr27 % 300;
		byte[] adaptationField = new byte[184];
		adaptationField[0] = (byte) (adaptationField.length - 1);
		adaptationField[1] = (byte) 0x10;
		adaptationField[2] = (byte) ((pcrBase >> 25) & 0xFF);
		adaptationField[3] = (byte) ((pcrBase >> 17) & 0xFF);
		adaptationField[4] = (byte) ((pcrBase >> 9) & 0xFF);
		adaptationField[5] = (byte) ((pcrBase >> 1) & 0xFF);
		adaptationField[6] = (byte) (((pcrBase & 0x01) << 7) | 0x7E | ((pcrExt >> 8) & 0x01));
		adaptationField[7] = (byte) (pcrExt & 0xFF);
		Arrays.fill(adaptationField, 8, adaptationField.length, (byte) 0xFF);

		byte[] ts = new byte[TS_PACKET_SIZE];
		ts[0] = (byte) SYNC_BYTE;
		ts[1] = (byte) ((pid >> 8) & 0x1F);
		ts[2] = (byte) (pid & 0xFF);
		ts[3] = (byte) (0x20 | (continuityCounters.getOrDefault(pid, 0) & 0x0F));
		System.arraycopy(adaptationField, 0, ts, 4, adaptationField.length);
		return ts;
	}

	private static int nextContinuityCounter(Map<Integer, Integer> continuityCounters, int pid) {
		int value = continuityCounters.getOrDefault(pid, 0);
		continuityCounters.put(pid, (value + 1) & 0x0F);
		return value;
	}

	/**
	 * Writes a 5-byte ISO 13818-1 (Table 2-21) 33-bit timestamp at {@code buf[offset..offset+4]}.
	 *
	 * @param prefixNibble 4-bit marker prefixing the timestamp: {@code 0010} (PTS only), {@code 0011} (PTS when DTS
	 *                     also present), or {@code 0001} (DTS)
	 * @param ts90         timestamp in 90 kHz ticks
	 */
	static void writeTimestamp(byte[] buf, int offset, int prefixNibble, long ts90) {
		buf[offset] = (byte) (((prefixNibble & 0x0F) << 4) | 0x01 | ((ts90 >> 29) & 0x0E));
		buf[offset + 1] = (byte) ((ts90 >> 22) & 0xFF);
		buf[offset + 2] = (byte) (0x01 | ((ts90 >> 14) & 0xFE));
		buf[offset + 3] = (byte) ((ts90 >> 7) & 0xFF);
		buf[offset + 4] = (byte) (0x01 | ((ts90 << 1) & 0xFE));
	}
}
