package org.brts.middle.pid;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/pid/PidAllocator.java' is part of BRTS.
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

import org.brts.common.model.StreamCodingType;

/**
 * Automatic PID allocator for Blu-ray elementary streams.
 * <p>
 * Follows the Blu-ray default PID allocation convention:
 *
 * <pre>
 *   PMT              0x0100 (256)
 *   Video            0x1011 (4113)
 *   Primary audio    0x1100–0x110F
 *   Secondary audio  0x1A00–0x1A0F
 *   PG subtitles     0x1200–0x120F
 *   IG (menu)        0x1400–0x140F
 *   Text subtitles   0x1800–0x180F
 * </pre>
 *
 * Each call to {@code next*()} returns the next available PID in the respective range.
 */
public class PidAllocator {

	private int nextVideo = 0x1011;

	private int nextAudio = 0x1100;

	private int nextSecAudio = 0x1A00;

	private int nextPg = 0x1200;

	private int nextIg = 0x1400;

	private int nextTextSub = 0x1800;

	public int allocate(StreamCodingType type) {
		return switch (type) {
		case H264_AVC, H265_HEVC, MPEG2_VIDEO, VC1 -> nextVideo++;
		case DOLBY_AC3, DOLBY_AC3_PLUS, DOLBY_TRUEHD, DTS, DTS_HD, DTS_HD_MASTER_AUDIO, DTS_EXPRESS, LPCM ->
			nextAudio++;
		case PRESENTATION_GRAPHICS -> nextPg++;
		case INTERACTIVE_GRAPHICS -> nextIg++;
		case TEXT_SUBTITLE -> nextTextSub++;
		};
	}

	/** Allocates a secondary audio PID. */
	public int allocateSecondaryAudio() {
		return nextSecAudio++;
	}

	public void reset() {
		nextVideo = 0x1011;
		nextAudio = 0x1100;
		nextSecAudio = 0x1A00;
		nextPg = 0x1200;
		nextIg = 0x1400;
		nextTextSub = 0x1800;
	}

}
