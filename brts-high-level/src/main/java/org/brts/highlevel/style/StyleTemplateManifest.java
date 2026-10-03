package org.brts.highlevel.style;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/main/java/org/brts/highlevel/style/StyleTemplateManifest.java' is part of BRTS.
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
