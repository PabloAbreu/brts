package org.brts.highlevel.style;



import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;
import org.brts.lowlevel.titlemenu.descriptor.BoundingBox;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;

import lombok.Getter;
import lombok.Setter;

/**
 * Content of a style template's {@code style-template.json}. Asset paths are relative to the template folder.
 */
@Getter
@Setter
public class StyleTemplateManifest {

	private String name;

	private String description;

	/** Base text style for every menu of the disc. */
	private TextStyle style;

	/** Popup menu presentation; only layout, screen size, style and backgrounds are used. */
	private PopupMenuConfig popupMenu;

	private TitleMenuStyle titleMenu;

	@Getter
	@Setter
	public static class TitleMenuStyle {

		private TextStyle style;

		private LayoutType layoutType;

		private BoundingBox boundingBox;

		/** Default screen background (and optional audio) used when the descriptor provides none. */
		private BackgroundSource backgroundSource;

	}

}
