package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/AnnexBUtils.java' is part of BRTS.
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
