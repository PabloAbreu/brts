package org.brts.common.utils;

import java.nio.ByteBuffer;

/**
 * Utilities for working with Annex B NAL-unit streams.
 */
public final class AnnexBUtils {

	private AnnexBUtils() {
	}

	/**
	 * Returns whether the remaining bytes begin with a 3-byte ({@code 00 00 01}) or 4-byte ({@code 00 00 00 01}) Annex
	 * B start code.
	 *
	 * @param buffer buffer to inspect without changing its position
	 * @return {@code true} when the buffer begins with an Annex B start code
	 */
	public static boolean isAnnexBFormat(ByteBuffer buffer) {
		int position = buffer.position();
		int remaining = buffer.remaining();
		if (remaining >= 4 && buffer.get(position) == 0x00 && buffer.get(position + 1) == 0x00
				&& buffer.get(position + 2) == 0x00 && buffer.get(position + 3) == 0x01) {
			return true;
		}
		return remaining >= 3 && buffer.get(position) == 0x00 && buffer.get(position + 1) == 0x00
				&& buffer.get(position + 2) == 0x01;
	}
}