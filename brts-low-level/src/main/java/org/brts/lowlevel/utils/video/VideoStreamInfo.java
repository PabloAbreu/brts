/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/VideoStreamInfo.java' is part of BRTS.
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

package org.brts.lowlevel.utils.video;

/**
 * Immutable probe snapshot. Null timing/color/HDR values mean absent metadata; inputFormat is an ES demuxer name.
 * Mastering-display and light-level strings use native x265/HWEnc units and syntax. Unknown numeric metadata is -1.
 * Profile/level and chromaLocation use FFmpeg values, not encoder CLI numbering (FFmpeg TOPLEFT=3 is chromaloc=2).
 * fullRange is meaningful only when rangeKnown is true; false alone does not establish limited-range signaling.
 */
public record VideoStreamInfo(int width, int height, FrameRate frameRate, int sarNumerator, int sarDenominator,
		boolean progressive, String inputFormat, String colorPrimaries, String transfer, String colorMatrix,
		boolean fullRange, String masterDisplay, String maxCll, int bitDepth, int chromaWidthShift,
		int chromaHeightShift, int profile, int level, int chromaLocation, boolean rangeKnown) {

	/** Compatibility constructor: legacy snapshots do not establish the newly added metadata. */
	public VideoStreamInfo(int width, int height, FrameRate frameRate, int sarNumerator, int sarDenominator,
			boolean progressive, String inputFormat, String colorPrimaries, String transfer, String colorMatrix,
			boolean fullRange, String masterDisplay, String maxCll) {
		this(width, height, frameRate, sarNumerator, sarDenominator, progressive, inputFormat, colorPrimaries, transfer,
				colorMatrix, fullRange, masterDisplay, maxCll, -1, -1, -1, -1, -1, -1, false);
	}

	public VideoStreamInfo {
		if (width <= 0 || height <= 0 || sarNumerator <= 0 || sarDenominator <= 0)
			throw new IllegalArgumentException("Source dimensions and sample aspect ratio must be positive");
	}

	public boolean hdr10() {
		return "smpte2084".equals(transfer);
	}
}
