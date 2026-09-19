package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/IgsCompositionSegment.java' is part of BRTS.
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

/**
 * Interactive Composition Segment (ICS) — the main segment in an IGS display set that defines the menu structure
 * (pages, buttons, navigation).
 */
@Getter
@Setter
@ToString
public class IgsCompositionSegment {

	/** PTS from the PES header (90 kHz ticks). */
	private long pts;

	/** DTS from the PES header (90 kHz ticks, -1 if absent). */
	private long dts = -1;

	/** Video descriptor (screen dimensions, frame rate). */
	private VideoDescriptor videoDescriptor;

	/** Composition descriptor (number + state). */
	private CompositionDescriptor compositionDescriptor;

	/** Sequence descriptor (first/last fragment). */
	private SequenceDescriptor sequenceDescriptor;

	/** The interactive composition data. */
	private IgsInteractiveComposition interactiveComposition;

}
