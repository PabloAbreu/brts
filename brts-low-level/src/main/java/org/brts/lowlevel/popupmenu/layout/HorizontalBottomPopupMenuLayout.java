/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/layout/HorizontalBottomPopupMenuLayout.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.igs.IgsMenuAssembler;

/** Places the root page in a bottom row and keeps submenu pages as vertical lists above it. */
public final class HorizontalBottomPopupMenuLayout extends AbstractBottomRowPopupMenuLayout {

	private static final int BUTTON_MAX_WIDTH = 800;
	private static final int BUTTON_SPACING_Y = 8;

	@Override
	protected List<IgsMenuAssembler.PositionedButton> layoutSubmenu(List<IgsMenuAssembler.LabeledButton> buttons,
			int screenW, int rootTop, int idOffset, int rootTargetId) {
		int totalHeight = buttons.stream().mapToInt(button -> button.images().height()).sum()
				+ Math.max(0, buttons.size() - 1) * BUTTON_SPACING_Y;
		int startY = rootTop - SUBMENU_GAP_Y - totalHeight;
		if (startY < 0) {
			throw new IllegalArgumentException("Popup submenu button list does not fit above the bottom row");
		}
		int groupX = Math.max(40, Math.min((screenW - BUTTON_MAX_WIDTH) / 2, screenW - BUTTON_MAX_WIDTH - 40));
		List<IgsMenuAssembler.PositionedButton> positioned = new ArrayList<>();
		int y = startY;
		for (int i = 0; i < buttons.size(); i++) {
			int buttonId = idOffset + i + 1;
			int upper = i == 0 ? buttonId : buttonId - 1;
			int lower = i == buttons.size() - 1 ? rootTargetId : buttonId + 1;
			positioned.add(new IgsMenuAssembler.PositionedButton(buttons.get(i).images(), buttons.get(i).commands(),
					buttons.get(i).autoAction(), groupX, y, upper, lower, buttonId, buttonId));
			y += buttons.get(i).images().height() + BUTTON_SPACING_Y;
		}
		return positioned;
	}
}
