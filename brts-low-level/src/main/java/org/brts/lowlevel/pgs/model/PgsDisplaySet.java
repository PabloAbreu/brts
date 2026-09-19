package org.brts.lowlevel.pgs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/pgs/model/PgsDisplaySet.java' is part of BRTS.
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
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete PGS Display Set — the collection of all segments between a PCS (Presentation Composition Segment) and the
 * End-Of-Display marker.
 * <p>
 * A display set typically contains:
 * <ul>
 * <li>One PCS ({@link PgsCompositionSegment})</li>
 * <li>Zero or more PDS (Palette Definition Segments) — reuses {@link IgsPalette}</li>
 * <li>Zero or more WDS (Window Definition Segments) — reuses {@link IgsWindowDefinition}</li>
 * <li>Zero or more ODS (Object Definition Segments) — reuses {@link IgsObject}</li>
 * <li>One End-Of-Display segment</li>
 * </ul>
 * PDS, ODS, WDS, and END segments are structurally identical between PGS and IGS, so we reuse the IGS model classes
 * directly.
 */
@Getter
@Setter
@ToString
public class PgsDisplaySet {

	/** Whether this display set starts a new epoch. */
	private boolean epochStart;

	/** The Presentation Composition Segment. */
	private PgsCompositionSegment compositionSegment;

	/** Palette definitions in this display set. */
	private List<IgsPalette> palettes = new ArrayList<>();

	/** Object (bitmap) definitions in this display set. */
	private List<IgsObject> objects = new ArrayList<>();

	/** Window definitions in this display set. */
	private List<IgsWindowDefinition> windowDefinitions = new ArrayList<>();

}
