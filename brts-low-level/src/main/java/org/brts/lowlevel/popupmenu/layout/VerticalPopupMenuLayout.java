package org.brts.lowlevel.popupmenu.layout;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/layout/VerticalPopupMenuLayout.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.igs.IgsMenuAssembler;

/** Places a persistent root column left of vertical submenu track lists. */
public final class VerticalPopupMenuLayout implements PopupMenuLayout {

	private static final int BUTTON_MAX_WIDTH = 800;
	private static final int BUTTON_SPACING_X = 40;
	private static final int BUTTON_SPACING_Y = 8;
	private static final int MARGIN_X = 40;
	private static final int MARGIN_BOTTOM = 80;

	@Override
	public List<Page> layout(List<List<IgsMenuAssembler.LabeledButton>> pages, int screenW, int screenH) {
		if (pages.size() == 1) {
			return List.of(new Page(layoutPage(pages.get(0), screenW, screenH)));
		}

		int rootCount = pages.get(0).size();
		int rootWidth = maxWidth(pages.get(0));
		int submenuWidth = pages.stream().skip(1).map(buttons -> buttons.subList(rootCount, buttons.size()))
				.mapToInt(VerticalPopupMenuLayout::maxWidth).max().orElse(0);
		int totalWidth = rootWidth + BUTTON_SPACING_X + submenuWidth;
		if (totalWidth > screenW - 2 * MARGIN_X) {
			throw new IllegalArgumentException("Popup root and submenu columns do not fit within the screen width");
		}
		int rootX = (screenW - totalWidth) / 2;
		int submenuX = rootX + rootWidth + BUTTON_SPACING_X;

		List<Page> result = new ArrayList<>();
		result.add(new Page(layoutColumn(pages.get(0), rootX, screenH, 0, 0), 1));
		for (int pageId = 1; pageId < pages.size(); pageId++) {
			List<IgsMenuAssembler.LabeledButton> buttons = pages.get(pageId);
			List<IgsMenuAssembler.LabeledButton> rootButtons = buttons.subList(0, rootCount);
			List<IgsMenuAssembler.LabeledButton> trackButtons = buttons.subList(rootCount, buttons.size());
			List<IgsMenuAssembler.PositionedButton> positioned = new ArrayList<>();
			positioned.addAll(layoutColumn(rootButtons, rootX, screenH, 0, 0));
			positioned.addAll(layoutColumn(trackButtons, submenuX, screenH, rootCount, pageId));
			result.add(new Page(positioned, rootCount + 1));
		}
		return result;
	}

	private static int maxWidth(List<IgsMenuAssembler.LabeledButton> buttons) {
		return buttons.stream().mapToInt(button -> button.images().normal().getWidth()).max().orElse(0);
	}

	private List<IgsMenuAssembler.PositionedButton> layoutColumn(List<IgsMenuAssembler.LabeledButton> buttons, int x,
			int screenH, int idOffset, int rootTargetId) {
		int totalHeight = totalHeight(buttons);
		if (totalHeight > screenH - MARGIN_BOTTOM) {
			throw new IllegalArgumentException("Popup vertical button list does not fit within the screen height");
		}
		int y = screenH - MARGIN_BOTTOM - totalHeight;
		List<IgsMenuAssembler.PositionedButton> positioned = new ArrayList<>();
		for (int i = 0; i < buttons.size(); i++) {
			int buttonId = idOffset + i + 1;
			int previous = idOffset + ((i - 1 + buttons.size()) % buttons.size()) + 1;
			int next = idOffset + (i + 1) % buttons.size() + 1;
			int left = rootTargetId == 0 ? buttonId : rootTargetId;
			positioned.add(new IgsMenuAssembler.PositionedButton(buttons.get(i).images(), buttons.get(i).commands(),
					buttons.get(i).autoAction(), x, y, previous, next, left, buttonId));
			y += buttons.get(i).images().height() + BUTTON_SPACING_Y;
		}
		return positioned;
	}

	private static int totalHeight(List<IgsMenuAssembler.LabeledButton> buttons) {
		return buttons.stream().mapToInt(button -> button.images().height()).sum()
				+ Math.max(0, buttons.size() - 1) * BUTTON_SPACING_Y;
	}

	private List<IgsMenuAssembler.PositionedButton> layoutPage(List<IgsMenuAssembler.LabeledButton> buttons,
			int screenW, int screenH) {
		int totalHeight = totalHeight(buttons);
		if (totalHeight > screenH - MARGIN_BOTTOM) {
			throw new IllegalArgumentException("Popup vertical button list does not fit within the screen height");
		}
		int startY = screenH - MARGIN_BOTTOM - totalHeight;
		int groupX = Math.max(MARGIN_X,
				Math.min((screenW - BUTTON_MAX_WIDTH) / 2, screenW - BUTTON_MAX_WIDTH - MARGIN_X));
		List<IgsMenuAssembler.PositionedButton> positioned = new ArrayList<>();
		int y = startY;
		for (int i = 0; i < buttons.size(); i++) {
			int buttonId = i + 1;
			positioned.add(new IgsMenuAssembler.PositionedButton(buttons.get(i).images(), buttons.get(i).commands(),
					groupX, y, ((i - 1 + buttons.size()) % buttons.size()) + 1, (i + 1) % buttons.size() + 1, buttonId,
					buttonId));
			y += buttons.get(i).images().height() + BUTTON_SPACING_Y;
		}
		return positioned;
	}
}
