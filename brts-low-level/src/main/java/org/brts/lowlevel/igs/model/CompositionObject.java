package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/CompositionObject.java' is part of BRTS.
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
 * Composition object reference — maps a graphic object to a window at a specific screen position. Used in both PCS and
 * ICS effect definitions.
 */
@Getter
@Setter
@ToString
public class CompositionObject {

	/** Referenced object id (16 bits). */
	private int objectIdRef;

	/** Referenced window id (8 bits). */
	private int windowIdRef;

	/** Whether this object is forced on (1 bit). */
	private boolean forcedOn;

	/** Horizontal position (16 bits). */
	private int x;

	/** Vertical position (16 bits). */
	private int y;

	/** Whether cropping is enabled (1 bit). */
	private boolean cropFlag;

	/** Crop horizontal offset (valid when cropFlag=true). */
	private int cropX;

	/** Crop vertical offset. */
	private int cropY;

	/** Crop width. */
	private int cropWidth;

	/** Crop height. */
	private int cropHeight;

}
