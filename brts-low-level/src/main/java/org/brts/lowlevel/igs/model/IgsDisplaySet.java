package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/IgsDisplaySet.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;

/**
 * A complete IGS Display Set — the collection of all segments between an ICS (or PCS) and the End-Of-Display marker.
 * <p>
 * A display set typically contains:
 * <ul>
 * <li>One ICS (Interactive Composition Segment)</li>
 * <li>Zero or more PDS (Palette Definition Segments)</li>
 * <li>Zero or more ODS (Object Definition Segments)</li>
 * <li>Zero or more WDS (Window Definition Segments)</li>
 * <li>One End-Of-Display segment</li>
 * </ul>
 */
@Getter
@Setter
@ToString
public class IgsDisplaySet {

	/** Whether this display set starts a new epoch. */
	private boolean epochStart;

	/** Whether the display set is complete (End-Of-Display received). */
	private boolean complete;

	/** The Interactive Composition Segment (non-null for IGS). */
	private IgsCompositionSegment compositionSegment;

	/** Palette definitions in this display set. */
	private List<IgsPalette> palettes = new ArrayList<>();

	/**
	 * Physical ODS fragments, except in demuxer results where fragments are reassembled into logical bitmap objects.
	 */
	private List<IgsObject> objects = new ArrayList<>();

	/** Window definitions in this display set. */
	private List<IgsWindowDefinition> windowDefinitions = new ArrayList<>();

}
