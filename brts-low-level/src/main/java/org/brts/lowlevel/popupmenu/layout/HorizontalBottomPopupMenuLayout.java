package org.brts.lowlevel.popupmenu.layout;

import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.igs.IgsMenuAssembler;

/** Places the root page in a bottom row and keeps submenu pages as vertical lists above it. */
public final class HorizontalBottomPopupMenuLayout implements PopupMenuLayout {

	private static final int BUTTON_HEIGHT = 40;
	private static final int BUTTON_MAX_WIDTH = 800;
	private static final int BUTTON_SPACING_X = 16;
	private static final int BUTTON_SPACING_Y = 8;
	private static final int MARGIN_BOTTOM = 80;

	@Override
	public List<Page> layout(List<List<IgsMenuAssembler.LabeledButton>> pages, int screenW, int screenH) {
		List<Page> result = new ArrayList<>();
		for (int pageId = 0; pageId < pages.size(); pageId++) {
			List<IgsMenuAssembler.LabeledButton> buttons = pages.get(pageId);
			result.add(new Page(pageId == 0 ? layoutHorizontal(buttons, screenW, screenH)
					: layoutVertical(buttons, screenW, screenH)));
		}
		return result;
	}

	private List<IgsMenuAssembler.PositionedButton> layoutHorizontal(List<IgsMenuAssembler.LabeledButton> buttons,
			int screenW, int screenH) {
		int totalWidth = buttons.stream().mapToInt(button -> button.images().normal().getWidth()).sum()
				+ Math.max(0, buttons.size() - 1) * BUTTON_SPACING_X;
		if (totalWidth > screenW - 80) {
			throw new IllegalArgumentException("Popup horizontal button row does not fit within the screen width");
		}
		int startX = Math.max(40, (screenW - totalWidth) / 2);
		int rowHeight = buttons.stream().mapToInt(button -> button.images().height()).max().orElse(BUTTON_HEIGHT);
		int y = screenH - MARGIN_BOTTOM - rowHeight;
		List<IgsMenuAssembler.PositionedButton> positioned = new ArrayList<>();
		int x = startX;
		for (int i = 0; i < buttons.size(); i++) {
			int buttonId = i + 1;
			int previous = ((i - 1 + buttons.size()) % buttons.size()) + 1;
			int next = (i + 1) % buttons.size() + 1;
			positioned.add(new IgsMenuAssembler.PositionedButton(buttons.get(i).images(), buttons.get(i).commands(), x,
					y, buttonId, buttonId, previous, next));
			x += buttons.get(i).images().normal().getWidth() + BUTTON_SPACING_X;
		}
		return positioned;
	}

	private List<IgsMenuAssembler.PositionedButton> layoutVertical(List<IgsMenuAssembler.LabeledButton> buttons,
			int screenW, int screenH) {
		int totalHeight = buttons.stream().mapToInt(button -> button.images().height()).sum()
				+ Math.max(0, buttons.size() - 1) * BUTTON_SPACING_Y;
		int rootReservedHeight = BUTTON_HEIGHT + 2 * BUTTON_SPACING_Y;
		if (totalHeight > screenH - MARGIN_BOTTOM - rootReservedHeight) {
			throw new IllegalArgumentException("Popup submenu button list does not fit above the bottom row");
		}
		int startY = screenH - MARGIN_BOTTOM - rootReservedHeight - totalHeight;
		int groupX = Math.max(40, Math.min((screenW - BUTTON_MAX_WIDTH) / 2, screenW - BUTTON_MAX_WIDTH - 40));
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