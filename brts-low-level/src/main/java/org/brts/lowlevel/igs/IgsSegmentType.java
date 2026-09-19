package org.brts.lowlevel.igs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/IgsSegmentType.java' is part of BRTS.
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
import lombok.RequiredArgsConstructor;

/**
 * Segment types found in IGS (Interactive Graphic Stream) and PGS (Presentation Graphic Stream) elementary streams.
 * <p>
 * Each segment in the raw ES begins with a 1-byte type, a 2-byte length, and then type-specific data. The constants
 * here match the values defined in the Blu-ray specification and implemented by libbluray.
 */
@RequiredArgsConstructor
@Getter
public enum IgsSegmentType {

	/** Palette Definition Segment (PDS). */
	PALETTE_DEFINITION(0x14),

	/** Object Definition Segment (ODS). */
	OBJECT_DEFINITION(0x15),

	/** Presentation Composition Segment (PCS) — used in PGS streams. */
	PG_COMPOSITION(0x16),

	/** Window Definition Segment (WDS). */
	WINDOW_DEFINITION(0x17),

	/** Interactive Composition Segment (ICS) — used in IGS streams. */
	IG_COMPOSITION(0x18),

	/** End of Display Set marker. */
	END_OF_DISPLAY(0x80);

	private final int code;

	/**
	 * Resolves a segment-type byte to an enum constant.
	 *
	 * @param b the raw byte value (0x14 – 0x80)
	 * @return the matching constant, or {@code null} if unknown
	 */
	public static IgsSegmentType fromByte(int b) {
		for (IgsSegmentType t : values()) {
			if (t.code == b)
				return t;
		}
		return null;
	}

}
