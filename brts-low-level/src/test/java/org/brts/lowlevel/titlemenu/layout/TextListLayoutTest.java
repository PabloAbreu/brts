package org.brts.lowlevel.titlemenu.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.junit.jupiter.api.Test;

class TextListLayoutTest {

	@Test
	void singleColumnLayout_positionsButtonsVertically() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(3, 1);
		TextListLayout layout = new TextListLayout();

		LayoutResult result = layout.layout(descriptor, Path.of("."));

		assertThat(result.getButtons()).hasSize(3);
		assertThat(result.isCompositeBackground()).isFalse();
		assertThat(result.getBackgroundComposition()).isNull();

		// All buttons should have the same X (centred)
		int x0 = result.getButtons().get(0).getX();
		assertThat(result.getButtons().get(1).getX()).isEqualTo(x0);
		assertThat(result.getButtons().get(2).getX()).isEqualTo(x0);

		// Y positions should be strictly increasing
		int y0 = result.getButtons().get(0).getY();
		int y1 = result.getButtons().get(1).getY();
		int y2 = result.getButtons().get(2).getY();
		assertThat(y1).isGreaterThan(y0);
		assertThat(y2).isGreaterThan(y1);
	}

	@Test
	void twoColumnLayout_distributesTitlesAcrossColumns() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(4, 2);
		TextListLayout layout = new TextListLayout();

		LayoutResult result = layout.layout(descriptor, Path.of("."));

		assertThat(result.getButtons()).hasSize(4);

		// With 4 titles in 2 columns: 2 per column
		// Column 0: buttons 0, 1; Column 1: buttons 2, 3
		int x0 = result.getButtons().get(0).getX();
		int x2 = result.getButtons().get(2).getX();
		assertThat(x2).isGreaterThan(x0); // second column is to the right
	}

	@Test
	void buttonImages_areNonNull() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(1, 1);
		TextListLayout layout = new TextListLayout();

		LayoutResult result = layout.layout(descriptor, Path.of("."));

		LayoutResult.PositionedButton btn = result.getButtons().get(0);
		assertThat(btn.getNormalImage()).isNotNull();
		assertThat(btn.getSelectedImage()).isNotNull();
		assertThat(btn.getActivatedImage()).isNotNull();
		assertThat(btn.getWidth()).isGreaterThan(0);
		assertThat(btn.getHeight()).isGreaterThan(0);
		assertThat(btn.getTitleNumber()).isEqualTo(1);
	}

	private TitleMenuDescriptor buildDescriptor(int titleCount, int columns) {
		TitleMenuDescriptor desc = new TitleMenuDescriptor();
		desc.setScreenWidth(1920);
		desc.setScreenHeight(1080);

		LayoutConfig layoutConfig = new LayoutConfig();
		layoutConfig.setType(LayoutType.TEXT_LIST);
		layoutConfig.setColumns(columns);
		desc.setLayout(layoutConfig);

		List<TitleEntry> titles = new java.util.ArrayList<>();
		for (int i = 0; i < titleCount; i++) {
			TitleEntry entry = new TitleEntry();
			entry.setTitleNumber(i + 1);
			entry.setDisplayName("Title " + (i + 1));
			titles.add(entry);
		}
		desc.setTitles(titles);

		return desc;
	}

}
