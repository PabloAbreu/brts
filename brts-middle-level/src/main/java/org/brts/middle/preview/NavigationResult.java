package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/NavigationResult.java' is part of BRTS.
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
import lombok.RequiredArgsConstructor;

/**
 * Result of a navigation action (move / activate / page-change).
 */
@Getter
@RequiredArgsConstructor
public class NavigationResult {

	public enum Type {

		/** Nothing happened (e.g. no neighbour). */
		NONE,
		/** Selection moved to a different button. */
		SELECTION_CHANGED,
		/** A button was activated (Enter pressed). */
		ACTIVATED,
		/** Page changed. */
		PAGE_CHANGED

	}

	private final Type type;

	private final int buttonOrPageId;

	private final String description;

	public static NavigationResult none(String reason) {
		return new NavigationResult(Type.NONE, -1, reason);
	}

}
