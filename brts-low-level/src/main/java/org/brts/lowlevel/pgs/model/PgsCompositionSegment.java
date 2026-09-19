package org.brts.lowlevel.pgs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/pgs/model/PgsCompositionSegment.java' is part of BRTS.
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
import lombok.ToString;
import org.brts.lowlevel.igs.model.CompositionDescriptor;
import org.brts.lowlevel.igs.model.CompositionObject;
import org.brts.lowlevel.igs.model.VideoDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Presentation Composition Segment (PCS) — the composition segment specific to PGS (Presentation Graphic Stream)
 * subtitle streams.
 * <p>
 * A PCS is simpler than the IGS Interactive Composition Segment (ICS): it contains no interactive elements (pages,
 * buttons, navigation) but instead carries a flat list of {@link CompositionObject} references that position
 * pre-rendered subtitle bitmaps on screen.
 * <p>
 * The shared structural elements ({@link VideoDescriptor}, {@link CompositionDescriptor}, {@link CompositionObject})
 * are reused from the IGS model package — they are format-identical between PGS and IGS.
 */
@Getter
@Setter
@ToString
public class PgsCompositionSegment {

	/** PTS from the PES header (90 kHz ticks). */
	private long pts;

	/** DTS from the PES header (90 kHz ticks, -1 if absent). */
	private long dts = -1;

	/** Video descriptor (screen dimensions, frame rate). */
	private VideoDescriptor videoDescriptor;

	/** Composition descriptor (number + state). */
	private CompositionDescriptor compositionDescriptor;

	/**
	 * Whether the display is a "palette update only" (no new objects). 0 = false, 0x80 = true.
	 */
	private boolean paletteUpdateFlag;

	/** Palette id referenced by this composition (8 bits). */
	private int paletteIdRef;

	/**
	 * Composition objects — each maps an object to a window at a specific screen position. For subtitle display there
	 * is typically one object; for clearing the screen this list is empty.
	 */
	private List<CompositionObject> compositionObjects = new ArrayList<>();

}
