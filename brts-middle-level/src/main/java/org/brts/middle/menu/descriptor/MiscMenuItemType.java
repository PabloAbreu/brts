package org.brts.middle.menu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/descriptor/MiscMenuItemType.java' is part of BRTS.
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

/**
 * Enumeration of miscellaneous menu item types.
 * <p>
 * Each type maps to a specific Blu-ray HDMV navigation command:
 * <ul>
 * <li>{@link #LAUNCH} → {@code PLAY_PL} (play a playlist)</li>
 * <li>{@link #GO_BACK} → {@code JUMP_TITLE} (jump to a title, typically the top menu)</li>
 * <li>{@link #POPUP_OFF} → {@code POPUP_OFF} (dismiss the pop-up menu)</li>
 * <li>{@link #RESUME} → {@code RESUME} (resume from saved location)</li>
 * </ul>
 */
public enum MiscMenuItemType {

	/** Launch playback of a movie/playlist. */
	LAUNCH,

	/** Navigate back to a previous menu (title). */
	GO_BACK,

	/** Dismiss the pop-up menu overlay. */
	POPUP_OFF,

	/** Resume playback from the last saved position. */
	RESUME

}
