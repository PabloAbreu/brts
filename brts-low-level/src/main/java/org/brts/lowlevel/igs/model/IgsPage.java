package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/IgsPage.java' is part of BRTS.
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
 * A single interactive page in an IGS menu. Each page has its own palette, set of BOGs (button groups), animations, and
 * UO (User Operation) mask.
 */
@Getter
@Setter
@ToString
public class IgsPage {

	/** Page id (8 bits). */
	private int id;

	/** Page version (8 bits). */
	private int version;

	/** User Operation mask table (8 bytes raw). */
	private byte[] uoMaskTable = new byte[8];

	/** In-effects animation sequence. */
	private IgsEffectSequence inEffects = new IgsEffectSequence();

	/** Out-effects animation sequence. */
	private IgsEffectSequence outEffects = new IgsEffectSequence();

	/** Animation frame rate code (8 bits). */
	private int animationFrameRateCode;

	/** Default selected button id ref (16 bits). */
	private int defaultSelectedButtonIdRef;

	/** Default activated button id ref (16 bits). */
	private int defaultActivatedButtonIdRef;

	/** Palette id reference (8 bits). */
	private int paletteIdRef;

	/** Button overlap groups on this page. */
	private List<IgsBog> bogs = new ArrayList<>();

}
