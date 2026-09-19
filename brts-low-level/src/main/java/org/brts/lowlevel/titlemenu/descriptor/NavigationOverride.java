package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/descriptor/NavigationOverride.java' is part of BRTS.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Explicit directional navigation overrides for a title menu button.
 * <p>
 * Each field holds the zero-based index of the target title entry in the direction indicated, or {@code null} to let
 * the auto-wiring logic fill that direction based on grid position.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NavigationOverride {

	/** Index of the button to focus when the user presses Up, or null for auto. */
	private Integer up;

	/** Index of the button to focus when the user presses Down, or null for auto. */
	private Integer down;

	/** Index of the button to focus when the user presses Left, or null for auto. */
	private Integer left;

	/** Index of the button to focus when the user presses Right, or null for auto. */
	private Integer right;

}
