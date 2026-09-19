package org.brts.common.m2ts.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/model/M2tsChapter.java' is part of BRTS.
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
 * A chapter (mark) within an M2TS clip, used both for MPLS PlayMark generation and for EP_map anchor placement.
 */
@Getter
@Setter
public class M2tsChapter {

	/** Chapter index (0-based). */
	private int index;

	/**
	 * Chapter start presentation time in 90 kHz ticks (relative to the clip PTS origin).
	 */
	private long ptsTicks;

	/** Optional human-readable title / label. */
	private String title;

	public M2tsChapter() {
	}

	public M2tsChapter(int index, long ptsTicks) {
		this.index = index;
		this.ptsTicks = ptsTicks;
	}

	public M2tsChapter(int index, long ptsTicks, String title) {
		this.index = index;
		this.ptsTicks = ptsTicks;
		this.title = title;
	}

}
