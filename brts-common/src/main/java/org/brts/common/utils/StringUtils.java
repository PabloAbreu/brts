package org.brts.common.utils;

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
