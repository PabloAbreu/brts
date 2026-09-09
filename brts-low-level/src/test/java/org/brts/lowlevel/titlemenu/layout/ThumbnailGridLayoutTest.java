package org.brts.lowlevel.titlemenu.layout;

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