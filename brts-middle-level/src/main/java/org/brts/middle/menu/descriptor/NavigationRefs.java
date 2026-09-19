package org.brts.middle.menu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/descriptor/NavigationRefs.java' is part of BRTS.
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
 * Explicit directional navigation overrides for a single menu item.
 * <p>
 * Each field holds the {@code id} of the target item in the direction indicated, or {@code null} to let the auto-wiring
 * logic fill that direction.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * "nav": {
 *   "down": "sub-en",
 *   "up":   "misc-play"
 * }
 * }</pre>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class NavigationRefs {

	/** Id of the button to focus when the user presses Up. */
	private String up;

	/** Id of the button to focus when the user presses Down. */
	private String down;

	/** Id of the button to focus when the user presses Left. */
	private String left;

	/** Id of the button to focus when the user presses Right. */
	private String right;

}
