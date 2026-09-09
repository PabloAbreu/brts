package org.brts.lowlevel.titlemenu.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.brts.common.menu.TextStyle;
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

	@Test
	void allButtons_haveSameWidth() throws Exception {
		// Intentionally varied label lengths to catch non-uniform sizing
		TitleMenuDescriptor desc = new TitleMenuDescriptor();
		desc.setScreenWidth(1920);
		desc.setScreenHeight(1080);
		LayoutConfig layoutConfig = new LayoutConfig();
		layoutConfig.setType(LayoutType.TEXT_LIST);
		layoutConfig.setColumns(1);
		desc.setLayout(layoutConfig);
		List<TitleEntry> titles = new java.util.ArrayList<>();
		for (String name : new String[] { "A", "A Much Longer Title Than The Others", "Mid Length" }) {
			TitleEntry entry = new TitleEntry();
			entry.setTitleNumber(titles.size() + 1);
			entry.setDisplayName(name);
			titles.add(entry);
		}
		desc.setTitles(titles);

		LayoutResult result = new TextListLayout().layout(desc, Path.of("."));

		int expectedWidth = result.getButtons().get(0).getWidth();
		for (LayoutResult.PositionedButton btn : result.getButtons()) {
			assertThat(btn.getWidth()).isEqualTo(expectedWidth);
			assertThat(btn.getNormalImage().getWidth()).isEqualTo(expectedWidth);
		}
	}

	@Test
	void allButtons_haveSameHeight_whenSpaceAllows() throws Exception {
		// Three short buttons, plenty of vertical space → uniform height expected
		TitleMenuDescriptor descriptor = buildDescriptor(3, 1);
		LayoutResult result = new TextListLayout().layout(descriptor, Path.of("."));

		int expectedHeight = result.getButtons().get(0).getHeight();
		for (LayoutResult.PositionedButton btn : result.getButtons()) {
			assertThat(btn.getHeight()).isEqualTo(expectedHeight);
			assertThat(btn.getNormalImage().getHeight()).isEqualTo(expectedHeight);
		}
	}

	@Test
	void layout_rendersSettingsAudioAndSubtitleButtons() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(1, 1);
		var audio = new org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem();
		audio.setDescription("English");
		audio.setStreamNumber(1);
		var subtitle = new org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem();
		subtitle.setDescription("French");
		subtitle.setStreamNumber(2);
		descriptor.setAudioItems(List.of(audio));
		descriptor.setSubtitleItems(List.of(subtitle));

		LayoutResult result = new TextListLayout().layout(descriptor, Path.of("."));

		assertThat(result.getSettingsButton()).isNotNull();
		assertThat(result.getSettingsButton().getNormalImage()).isNotNull();
		assertThat(result.getSettingsPages()).hasSize(3);
		assertThat(result.getSettingsPages().get(1).getButtons()).hasSize(2);
		assertThat(result.getSettingsPages().get(1).getButtons().get(0).getNormalImage()).isNotNull();
		assertThat(result.getSettingsPages().get(1).getButtons().get(0).getNavigationCommands()).isNotEmpty();
		assertThat(result.getSettingsPages().get(2).getButtons()).hasSize(2);
		assertThat(result.getSettingsPages().get(2).getButtons().get(0).getNormalImage()).isNotNull();
	}

	@Test
	void settingsItems_overrideGlobalTextStyle() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(1, 1);
		TextStyle globalStyle = new TextStyle();
		globalStyle.setFontSize(12);
		descriptor.getLayout().setTitleStyle(globalStyle);

		var audio = new org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem();
		audio.setDescription("English");
		audio.setStreamNumber(1);
		TextStyle itemStyle = new TextStyle();
		itemStyle.setFontSize(48);
		audio.setStyle(itemStyle);
		descriptor.setAudioItems(List.of(audio));

		LayoutResult result = new TextListLayout().layout(descriptor, Path.of("."));
		List<LayoutResult.PositionedButton> audioButtons = result.getSettingsPages().get(0).getButtons();

		assertThat(audioButtons.get(0).getHeight()).isGreaterThan(audioButtons.get(1).getHeight());
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
