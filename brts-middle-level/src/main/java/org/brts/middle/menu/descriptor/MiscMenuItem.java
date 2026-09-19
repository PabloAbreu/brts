package org.brts.middle.menu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/descriptor/MiscMenuItem.java' is part of BRTS.
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
 * A miscellaneous navigation menu item (launch movie, go back, resume, etc.).
 *
 * <h2>Example — launch a movie</h2>
 *
 * <pre>{@code
 * {
 *   "type": "LAUNCH",
 *   "target": "00001",
 *   "description": "Lancer le film",
 *   "icon": "/path/to/play-icon.png"
 * }
 * }</pre>
 *
 * <h2>Example — go back to top menu</h2>
 *
 * <pre>{@code
 * {
 *   "type": "GO_BACK",
 *   "target": "0",
 *   "description": "Menu principal"
 * }
 * }</pre>
 */
@Getter
@Setter
public class MiscMenuItem extends MenuItem {

	/** The type of action this button performs. */
	private MiscMenuItemType type;

	/**
	 * Target identifier, interpreted depending on {@link #type}:
	 * <ul>
	 * <li>{@code LAUNCH}: playlist name (e.g. "00001" → MPLS 00001)</li>
	 * <li>{@code GO_BACK}: title number to jump to (e.g. "0" = top menu, "65535" = first play)</li>
	 * <li>{@code POPUP_OFF}: not used</li>
	 * <li>{@code RESUME}: not used</li>
	 * </ul>
	 */
	private String target;

	/**
	 * Path to a black-and-white PNG icon image. The icon will be loaded and colourised per button state (normal /
	 * selected / activated) to produce the final RLE images. Optional if {@code description} is provided.
	 */
	private String icon;

}
