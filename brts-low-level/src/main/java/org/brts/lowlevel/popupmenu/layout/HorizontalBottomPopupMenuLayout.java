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
	private static final int SUBMENU_GAP_Y = 16;
	private static final int MARGIN_BOTTOM = 80;

	@Override
	public List<Page> layout(List<List<IgsMenuAssembler.LabeledButton>> pages, int screenW, int screenH) {
		if (pages.size() == 1) {
			return List.of(new Page(layoutHorizontal(pages.get(0), screenW, screenH)));
		}

		int rootCount = pages.get(0).size();
		List<IgsMenuAssembler.PositionedButton> rootPage = layoutHorizontal(pages.get(0), screenW, screenH);
		int rootTop = rootPage.stream().mapToInt(IgsMenuAssembler.PositionedButton::y).min().orElse(screenH);
		List<Page> result = new ArrayList<>();
		result.add(new Page(rootPage, 1));
		for (int pageId = 1; pageId < pages.size(); pageId++) {
			List<IgsMenuAssembler.LabeledButton> buttons = pages.get(pageId);
			List<IgsMenuAssembler.PositionedButton> positioned = cloneRootRow(buttons.subList(0, rootCount), rootPage);
			positioned.addAll(
					layoutVertical(buttons.subList(rootCount, buttons.size()), screenW, rootTop, rootCount, pageId));
			result.add(new Page(positioned, rootCount + 1));
		}
		return result;
	}

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