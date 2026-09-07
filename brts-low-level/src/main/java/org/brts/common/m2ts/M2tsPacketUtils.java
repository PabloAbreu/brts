package org.brts.common.m2ts;

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
}