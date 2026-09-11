package org.brts.lowlevel.popupmenu.layout;

import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.igs.IgsMenuAssembler;

/** The original vertically stacked popup-menu layout. */
public final class VerticalPopupMenuLayout implements PopupMenuLayout {

	private static final int BUTTON_HEIGHT = 40;
	private static final int BUTTON_MAX_WIDTH = 800;
	private static final int BUTTON_SPACING_Y = 8;
	private static final int MARGIN_BOTTOM = 80;

	@Override
	public List<Page> layout(List<List<IgsMenuAssembler.LabeledButton>> pages, int screenW, int screenH) {
		List<Page> result = new ArrayList<>();
		for (List<IgsMenuAssembler.LabeledButton> buttons : pages) {
			result.add(new Page(layoutPage(buttons, screenW, screenH)));
		}
		return result;
	}

	private List<IgsMenuAssembler.PositionedButton> layoutPage(List<IgsMenuAssembler.LabeledButton> buttons,
			int screenW, int screenH) {
		int totalHeight = buttons.stream().mapToInt(button -> button.images().height()).sum()
				+ Math.max(0, buttons.size() - 1) * BUTTON_SPACING_Y;
		if (totalHeight > screenH - MARGIN_BOTTOM) {
			throw new IllegalArgumentException("Popup vertical button list does not fit within the screen height");
		}
		int startY = screenH - MARGIN_BOTTOM - totalHeight;
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