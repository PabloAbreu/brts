package org.brts.lowlevel.titlemenu.layout;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/titlemenu/layout/ThumbnailGridLayoutTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.junit.jupiter.api.Test;

class ThumbnailGridLayoutTest {

	@Test
	void titleStyle_overridesGlobalSelectedColor() throws Exception {
		TitleMenuDescriptor descriptor = new TitleMenuDescriptor();
		descriptor.setScreenWidth(1920);
		descriptor.setScreenHeight(1080);

		LayoutConfig layout = new LayoutConfig();
		layout.setType(LayoutType.THUMBNAIL_GRID);
		layout.setColumns(1);
		TextStyle globalStyle = new TextStyle();
		globalStyle.setSelectedColor("#FF0000FF");
		layout.setTitleStyle(globalStyle);
		descriptor.setLayout(layout);

		TitleEntry title = new TitleEntry();
		title.setTitleNumber(1);
		title.setDisplayName("Title");
		TextStyle titleStyle = new TextStyle();
		titleStyle.setSelectedColor("#FFFF0000");
		title.setStyle(titleStyle);
		descriptor.setTitles(List.of(title));

		LayoutResult result = new ThumbnailGridLayout().layout(descriptor, Path.of("."));
		BufferedImage selectedImage = result.getButtons().get(0).getSelectedImage();

		assertThat(selectedImage.getRGB(0, 0)).isEqualTo(0xFFFF0000);
	}

}
