/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/clpi/VideoEntryPointScanner.java' is part of BRTS.
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

import java.util.function.LongUnaryOperator;

import org.brts.common.model.StreamCodingType;

final class VideoEntryPointScanner {
	@FunctionalInterface
	interface EntryConsumer {
		void accept(long pts, long spn);
	}

	private final org.brts.common.m2ts.VideoEntryPointScanner scanner;

	VideoEntryPointScanner(StreamCodingType codec, LongUnaryOperator timestamps, EntryConsumer entries) {
		scanner = new org.brts.common.m2ts.VideoEntryPointScanner(codec, timestamps, entries::accept);
	}

	void accept(byte[] data, int offset, int length, boolean start, long spn) {
		scanner.accept(data, offset, length, start, spn);
	}

	long malformedHeaders() {
		return scanner.malformedHeaders();
	}

	long untimestampedPictures() {
		return scanner.untimestampedPictures();
	}

	void reset() {
		scanner.reset();
	}
}
