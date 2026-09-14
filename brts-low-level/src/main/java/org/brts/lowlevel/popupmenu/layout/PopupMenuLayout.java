package org.brts.lowlevel.popupmenu.layout;

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