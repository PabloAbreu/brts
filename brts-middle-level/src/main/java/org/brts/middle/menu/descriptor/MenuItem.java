package org.brts.middle.menu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/descriptor/MenuItem.java' is part of BRTS.
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
 * Common base for all setup-menu item types.
 * <p>
 * Consolidates the fields shared by every item: a stable cross-reference id, a human-readable label, an optional
 * per-item style override, and optional explicit D-pad navigation overrides.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class MenuItem {

	/**
	 * Optional stable identifier for cross-item navigation references. When set, other items can reference this button
	 * by id in their {@link NavigationRefs}.
	 */
	private String id;

	/**
	 * Human-readable label for the button.
	 */
	private String description;

	/**
	 * Optional per-item style override. Non-null fields are merged over the global style.
	 */
	private TextStyle style;

	/**
	 * Optional explicit directional navigation overrides. Directions not specified here are filled by auto-wiring.
	 */
	private NavigationRefs nav;

}
