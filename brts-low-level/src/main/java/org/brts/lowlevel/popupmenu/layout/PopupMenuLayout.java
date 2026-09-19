package org.brts.lowlevel.popupmenu.layout;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/layout/PopupMenuLayout.java' is part of BRTS.
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

import java.util.List;

import org.brts.lowlevel.igs.IgsMenuAssembler;

/** Calculates positions and directional navigation for rendered popup-menu pages. */
public interface PopupMenuLayout {

	List<PopupMenuLayout.Page> layout(List<List<IgsMenuAssembler.LabeledButton>> pages, int screenW, int screenH);

	record Page(List<IgsMenuAssembler.PositionedButton> buttons, int defaultSelectedButtonIdRef) {

		public Page(List<IgsMenuAssembler.PositionedButton> buttons) {
			this(buttons, buttons.isEmpty() ? 0xFFFF : 1);
		}
	}
}
