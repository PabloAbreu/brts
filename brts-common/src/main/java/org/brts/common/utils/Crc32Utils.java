package org.brts.common.utils;

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