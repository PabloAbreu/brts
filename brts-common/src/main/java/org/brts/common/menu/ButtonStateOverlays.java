/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/menu/ButtonStateOverlays.java' is part of BRTS.
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
package org.brts.common.menu;

import org.brts.common.utils.composition.ImageReference;

import lombok.Getter;
import lombok.Setter;

/** Optional image overlays, composited on top of each button state. Unspecified states remain unchanged. */
@Getter
@Setter
public class ButtonStateOverlays {
	/** Overlay used in the normal state. */
	private ImageReference normal;
	/** Overlay used in the selected state. */
	private ImageReference selected;
	/** Overlay used in the activated state. */
	private ImageReference activated;

	public ButtonStateOverlays mergeOver(ButtonStateOverlays base) {
		ButtonStateOverlays merged = new ButtonStateOverlays();
		merged.normal = normal != null ? normal : base != null ? base.normal : null;
		merged.selected = selected != null ? selected : base != null ? base.selected : null;
		merged.activated = activated != null ? activated : base != null ? base.activated : null;
		return merged;
	}
}
