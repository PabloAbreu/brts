package org.brts.lowlevel.model.mpls;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/mpls/PlayMark.java' is part of BRTS.
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

import lombok.Getter;
import lombok.Setter;

/**
 * A chapter mark within an MPLS playlist. Each mark anchors a chapter to a specific PTS within a PlayItem.
 */
@Getter
@Setter
public class PlayMark {

	/** Mark type: 0x01 = chapter mark, 0x02 = index mark. */
	private int markType = 0x01;

	/** Zero-based index into the PlayItem list. */
	private int playItemRef;

	/** Mark time in 45 kHz ticks (relative to clip start in the MPLS STC domain). */
	private long markTimeTicks;

	/** Entry ES PID. Use 0xFFFF if not applicable. */
	private int entryEsPid = 0xFFFF;

	/** Duration of this mark in 45 kHz ticks (0 = to next mark). */
	private long durationTicks = 0;

}
