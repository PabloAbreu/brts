/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/layout/AbstractBottomRowPopupMenuLayout.java' is part of BRTS.
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

/** Shares bottom-row placement and persistent root controls between submenu arrangements. */
abstract class AbstractBottomRowPopupMenuLayout implements PopupMenuLayout {

	protected static final int SUBMENU_GAP_Y = 16;
	private static final int BUTTON_HEIGHT = 40;
	private static final int BUTTON_SPACING_X = 16;
	private static final int MARGIN_X = 40;
	private static final int MARGIN_BOTTOM = 80;

	@Override
	public final List<Page> layout(List<List<IgsMenuAssembler.LabeledButton>> pages, int screenW, int screenH) {
		List<IgsMenuAssembler.LabeledButton> rootButtons = pages.get(0);
		int rootY = screenH - MARGIN_BOTTOM - rowHeight(rootButtons);
		List<IgsMenuAssembler.PositionedButton> rootPage = layoutHorizontalRow(rootButtons, screenW, rootY, 0, 0);
		if (pages.size() == 1) {
			return List.of(new Page(rootPage));
		}

		int rootCount = rootButtons.size();
		int rootTop = rootPage.stream().mapToInt(IgsMenuAssembler.PositionedButton::y).min().orElse(screenH);
		List<Page> result = new ArrayList<>();
		result.add(new Page(rootPage, 1));
		for (int pageId = 1; pageId < pages.size(); pageId++) {
			List<IgsMenuAssembler.LabeledButton> buttons = pages.get(pageId);
			List<IgsMenuAssembler.PositionedButton> positioned = cloneRootRow(buttons.subList(0, rootCount), rootPage);
			positioned.addAll(
					layoutSubmenu(buttons.subList(rootCount, buttons.size()), screenW, rootTop, rootCount, pageId));
			result.add(new Page(positioned, rootCount + 1));
		}
		return result;
	}

	protected abstract List<IgsMenuAssembler.PositionedButton> layoutSubmenu(
			List<IgsMenuAssembler.LabeledButton> buttons, int screenW, int rootTop, int idOffset, int rootTargetId);

	private List<IgsMenuAssembler.PositionedButton> cloneRootRow(List<IgsMenuAssembler.LabeledButton> rootButtons,
			List<IgsMenuAssembler.PositionedButton> rootPage) {
		List<IgsMenuAssembler.PositionedButton> positioned = new ArrayList<>();
		for (int i = 0; i < rootButtons.size(); i++) {
			IgsMenuAssembler.PositionedButton rootPosition = rootPage.get(i);
			IgsMenuAssembler.LabeledButton button = rootButtons.get(i);
			int buttonId = i + 1;
			positioned.add(new IgsMenuAssembler.PositionedButton(button.images(), button.commands(),
					button.autoAction(), rootPosition.x(), rootPosition.y(), buttonId, buttonId, buttonId, buttonId));
		}
		return positioned;
	}

	protected static int rowHeight(List<IgsMenuAssembler.LabeledButton> buttons) {
		return buttons.stream().mapToInt(button -> button.images().height()).max().orElse(BUTTON_HEIGHT);
	}

	/** Centers a row, wrapping horizontally within its IDs and optionally returning down to a root control. */
	protected final List<IgsMenuAssembler.PositionedButton> layoutHorizontalRow(
			List<IgsMenuAssembler.LabeledButton> buttons, int screenW, int y, int idOffset, int rootTargetId) {
		int totalWidth = buttons.stream().mapToInt(button -> button.images().normal().getWidth()).sum()
				+ Math.max(0, buttons.size() - 1) * BUTTON_SPACING_X;
		if (totalWidth > screenW - 2 * MARGIN_X) {
			throw new IllegalArgumentException("Popup horizontal button row does not fit within the screen width");
		}
		List<IgsMenuAssembler.PositionedButton> positioned = new ArrayList<>();
		int x = Math.max(MARGIN_X, (screenW - totalWidth) / 2);
		for (int i = 0; i < buttons.size(); i++) {
			IgsMenuAssembler.LabeledButton button = buttons.get(i);
			int buttonId = idOffset + i + 1;
			int previous = idOffset + ((i - 1 + buttons.size()) % buttons.size()) + 1;
			int next = idOffset + (i + 1) % buttons.size() + 1;
			int lower = rootTargetId == 0 ? buttonId : rootTargetId;
			positioned.add(new IgsMenuAssembler.PositionedButton(button.images(), button.commands(),
					rootTargetId != 0 && button.autoAction(), x, y, buttonId, lower, previous, next));
			x += button.images().normal().getWidth() + BUTTON_SPACING_X;
		}
		return positioned;
	}
}
