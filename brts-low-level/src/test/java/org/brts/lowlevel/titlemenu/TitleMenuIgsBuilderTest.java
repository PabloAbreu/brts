package org.brts.lowlevel.titlemenu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.igs.model.IgsBog;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.brts.lowlevel.titlemenu.layout.LayoutResult;
import org.brts.lowlevel.titlemenu.layout.TextListLayout;
import org.junit.jupiter.api.Test;

class TitleMenuIgsBuilderTest {

	@Test
	void build_createsCorrectNumberOfButtonsAndObjects() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(3);
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		TitleMenuIgsBuilder builder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = builder.build(layoutResult, descriptor);

		assertThat(displaySet).isNotNull();
		assertThat(displaySet.isEpochStart()).isTrue();
		assertThat(displaySet.isComplete()).isTrue();

		// 3 buttons × 3 states = 9 objects
		assertThat(displaySet.getObjects()).hasSize(9);

		// 3 BOGs (one per button)
		List<IgsBog> bogs = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(0).getBogs();
		assertThat(bogs).hasSize(3);
	}

	@Test
	void build_wiresNavigationCommands_jumpTitle() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(2);
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		TitleMenuIgsBuilder builder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = builder.build(layoutResult, descriptor);

		List<IgsBog> bogs = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(0).getBogs();

		// First button should have JUMP_TITLE with title number 1
		IgsButton btn1 = bogs.get(0).getButtons().get(0);
		assertThat(btn1.getNavigationCommands()).hasSize(1);
		assertThat(btn1.getNavigationCommands().get(0).getMnemonic()).isEqualTo("JUMP_TITLE");

		// Second button should have JUMP_TITLE with title number 2
		IgsButton btn2 = bogs.get(1).getButtons().get(0);
		assertThat(btn2.getNavigationCommands()).hasSize(1);
		assertThat(btn2.getNavigationCommands().get(0).getMnemonic()).isEqualTo("JUMP_TITLE");
	}

	@Test
	void build_wiresDpadNeighbours_verticalList() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(3);
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		TitleMenuIgsBuilder builder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = builder.build(layoutResult, descriptor);

		List<IgsBog> bogs = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(0).getBogs();

		// Button 1 (id=1): up wraps to button 3, down goes to button 2
		IgsButton btn1 = bogs.get(0).getButtons().get(0);
		assertThat(btn1.getUpperButtonIdRef()).isEqualTo(3); // wraps to last
		assertThat(btn1.getLowerButtonIdRef()).isEqualTo(2); // next

		// Button 2 (id=2): up = button 1, down = button 3
		IgsButton btn2 = bogs.get(1).getButtons().get(0);
		assertThat(btn2.getUpperButtonIdRef()).isEqualTo(1);
		assertThat(btn2.getLowerButtonIdRef()).isEqualTo(3);

		// Button 3 (id=3): up = button 2, down wraps to button 1
		IgsButton btn3 = bogs.get(2).getButtons().get(0);
		assertThat(btn3.getUpperButtonIdRef()).isEqualTo(2);
		assertThat(btn3.getLowerButtonIdRef()).isEqualTo(1);
	}

	@Test
	void build_screenDimensions_setCorrectly() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(1);
		descriptor.setScreenWidth(1280);
		descriptor.setScreenHeight(720);

		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		TitleMenuIgsBuilder builder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = builder.build(layoutResult, descriptor);

		assertThat(displaySet.getCompositionSegment().getVideoDescriptor().getWidth()).isEqualTo(1280);
		assertThat(displaySet.getCompositionSegment().getVideoDescriptor().getHeight()).isEqualTo(720);
	}

	private TitleMenuDescriptor buildDescriptor(int titleCount) {
		TitleMenuDescriptor desc = new TitleMenuDescriptor();
		desc.setScreenWidth(1920);
		desc.setScreenHeight(1080);

		LayoutConfig layoutConfig = new LayoutConfig();
		layoutConfig.setType(LayoutType.TEXT_LIST);
		layoutConfig.setColumns(1);
		desc.setLayout(layoutConfig);

		List<TitleEntry> titles = new ArrayList<>();
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
