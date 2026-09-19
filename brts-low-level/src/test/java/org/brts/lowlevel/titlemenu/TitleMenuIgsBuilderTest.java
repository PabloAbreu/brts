package org.brts.lowlevel.titlemenu;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/titlemenu/TitleMenuIgsBuilderTest.java' is part of BRTS.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.igs.model.IgsBog;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
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
		assertThat(displaySet.getCompositionSegment().getInteractiveComposition().getPages()).hasSize(2);

		// 3 BOGs (one per button)
		List<IgsBog> bogs = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(1).getBogs();
		assertThat(bogs).hasSize(3);
	}

	@Test
	void build_wiresNavigationCommands_jumpTitle() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(2);
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		TitleMenuIgsBuilder builder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = builder.build(layoutResult, descriptor);

		List<IgsBog> bogs = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(1).getBogs();

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

		List<IgsBog> bogs = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(1).getBogs();

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

	@Test
	void build_includesStartupPageWithAutoAction() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(1);
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		TitleMenuIgsBuilder builder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = builder.build(layoutResult, descriptor);

		var pages = displaySet.getCompositionSegment().getInteractiveComposition().getPages();
		assertThat(pages).hasSize(2);

		IgsButton startupButton = pages.get(0).getBogs().get(0).getButtons().get(0);
		assertThat(startupButton.isAutoAction()).isTrue();
		// NavigationCommandUtils.setButtonPage emits 2 MOVE + 1 SET_BUTTON_PAGE
		assertThat(startupButton.getNavigationCommands()).hasSize(3);
		assertThat(startupButton.getNavigationCommands().get(2).getMnemonic()).isEqualTo("SET_BUTTON_PAGE");
		assertThat(pages.get(1).getId()).isEqualTo(1);
		assertThat(pages.get(1).getDefaultSelectedButtonIdRef()).isEqualTo(1);
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

	@Test
	void build_placesSettingsButtonAtBottomRight() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(2);
		descriptor.setAudioItems(List.of(audioItem("English", 1), audioItem("French", 2)));
		descriptor.setSubtitleItems(List.of(subtitleItem("Off", 0), subtitleItem("English", 1)));
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		IgsDisplaySet displaySet = new TitleMenuIgsBuilder().build(layoutResult, descriptor);
		IgsButton settingsButton = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(1)
				.getBogs().get(2).getButtons().get(0);

		var rendered = org.brts.common.menu.TextRenderer.renderTextButton(
				org.brts.common.utils.BrtsI18NLabels.getLabel(org.brts.common.utils.BrtsI18NLabels.MENU_SETTINGS),
				new org.brts.common.menu.TextStyle().withDefaults(), 800);
		assertThat(settingsButton.getXPos()).isEqualTo(descriptor.getScreenWidth() - rendered.width() - 40);
		assertThat(settingsButton.getYPos()).isEqualTo(descriptor.getScreenHeight() - rendered.height() - 80);
	}

	@Test
	void build_withoutSettingsItems_producesOnlyStartupAndMenuPages() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(2);
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		IgsDisplaySet displaySet = new TitleMenuIgsBuilder().build(layoutResult, descriptor);

		assertThat(displaySet.getCompositionSegment().getInteractiveComposition().getPages()).hasSize(2);
	}

	@Test
	void build_withAudioAndSubtitleItems_addsSettingsButtonAndThreeExtraPages() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(2);
		descriptor.setAudioItems(List.of(audioItem("English", 1), audioItem("French", 2)));
		descriptor.setSubtitleItems(List.of(subtitleItem("Off", 0), subtitleItem("English", 1)));
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		IgsDisplaySet displaySet = new TitleMenuIgsBuilder().build(layoutResult, descriptor);

		var pages = displaySet.getCompositionSegment().getInteractiveComposition().getPages();
		// startup(0) + menu(1) + settings root(2) + audio list(3) + subtitle list(4)
		assertThat(pages).hasSize(5);

		// menu page (id=1) now has 3 bogs: 2 titles + 1 settings button
		List<IgsBog> menuBogs = pages.get(1).getBogs();
		assertThat(menuBogs).hasSize(3);
		IgsButton settingsButton = menuBogs.get(2).getButtons().get(0);
		assertThat(settingsButton.getNavigationCommands()).last().extracting(NavigationCommand::getMnemonic)
				.isEqualTo("SET_BUTTON_PAGE");

		// audio list page (id=3): 2 track buttons + 1 back button
		assertThat(pages.get(3).getBogs()).hasSize(3);
		// subtitle list page (id=4): 2 track buttons + 1 back button
		assertThat(pages.get(4).getBogs()).hasSize(3);
	}

	@Test
	void build_withOnlyAudioItems_collapsesToSingleSettingsPage() throws Exception {
		TitleMenuDescriptor descriptor = buildDescriptor(1);
		descriptor.setAudioItems(List.of(audioItem("English", 1), audioItem("French", 2)));
		TextListLayout layout = new TextListLayout();
		LayoutResult layoutResult = layout.layout(descriptor, Path.of("."));

		IgsDisplaySet displaySet = new TitleMenuIgsBuilder().build(layoutResult, descriptor);

		var pages = displaySet.getCompositionSegment().getInteractiveComposition().getPages();
		// startup(0) + menu(1) + audio list collapsed as entry page(2)
		assertThat(pages).hasSize(3);
		assertThat(pages.get(2).getBogs()).hasSize(3); // 2 tracks + back
	}

	private static org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem audioItem(String description,
			int streamNumber) {
		var item = new org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem();
		item.setDescription(description);
		item.setStreamNumber(streamNumber);
		return item;
	}

	private static org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem subtitleItem(String description,
			int streamNumber) {
		var item = new org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem();
		item.setDescription(description);
		item.setStreamNumber(streamNumber);
		return item;
	}

}
