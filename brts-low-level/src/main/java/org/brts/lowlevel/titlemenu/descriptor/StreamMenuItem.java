package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/descriptor/StreamMenuItem.java' is part of BRTS.
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

import org.brts.common.menu.TextStyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * An audio or subtitle track selection item for the title menu's embedded settings submenu.
 * <p>
 * The stream number refers to the same physical track position across all titles' source media. Audio stream numbers
 * are 1-based; subtitle stream number {@code 0} means subtitles off.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class StreamMenuItem {

	/** Human-readable label for the button. */
	private String description;

	/** Physical audio or subtitle stream number. */
	private int streamNumber;

	/** Optional style override merged over the title menu's global style. */
	private TextStyle style;

}
