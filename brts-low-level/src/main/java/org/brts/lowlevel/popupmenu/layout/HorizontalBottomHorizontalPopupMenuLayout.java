/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/layout/HorizontalBottomHorizontalPopupMenuLayout.java' is part of BRTS.
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

package org.brts.lowlevel.popupmenu.layout;

import java.util.List;

import org.brts.lowlevel.igs.IgsMenuAssembler;

/** Places the root page in a bottom row and keeps submenu pages as horizontal lists above it. */
public final class HorizontalBottomHorizontalPopupMenuLayout extends AbstractBottomRowPopupMenuLayout {

	@Override
	protected List<IgsMenuAssembler.PositionedButton> layoutSubmenu(List<IgsMenuAssembler.LabeledButton> buttons,
			int screenW, int rootTop, int idOffset, int rootTargetId) {
		int y = rootTop - SUBMENU_GAP_Y - rowHeight(buttons);
		if (y < 0) {
			throw new IllegalArgumentException("Popup submenu button row does not fit above the bottom row");
		}
		return layoutHorizontalRow(buttons, screenW, y, idOffset, rootTargetId);
	}
}
