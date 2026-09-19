package org.brts.lowlevel.popupmenu;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/popupmenu/PopupMenuIgsBuilderTest.java' is part of BRTS.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.brts.common.utils.composition.ImageReference;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPage;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.BackgroundLayer;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.BackgroundLayout;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.BackgroundLayoutMode;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.PageBackgrounds;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.TrackEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PopupMenuIgsBuilderTest {

	@Test
	void buildsPersistentRootOnAudioAndSubtitlePages() throws IOException {
		PopupMenuConfig config = configWithTracks(2, 2);
		config.setLayout(PopupMenuConfig.Layout.VERTICAL_LIST);

		IgsDisplaySet displaySet = new PopupMenuIgsBuilder().build(config);
		List<IgsPage> pages = displaySet.getCompositionSegment().getInteractiveComposition().getPages();

		assertThat(pages).hasSize(3);
		assertThat(buttons(pages.get(0))).hasSize(3).noneMatch(IgsButton::isAutoAction);
		assertThat(buttons(pages.get(1))).hasSize(5);
		assertThat(buttons(pages.get(2))).hasSize(5);
		assertThat(buttons(pages.get(1)).subList(0, 3)).allMatch(IgsButton::isAutoAction);
		assertThat(buttons(pages.get(2)).subList(0, 3)).allMatch(IgsButton::isAutoAction);
		assertThat(buttons(pages.get(1)).subList(3, 5)).noneMatch(IgsButton::isAutoAction);
		assertThat(pages.get(1).getDefaultSelectedButtonIdRef()).isEqualTo(4);
		assertThat(pages.get(2).getDefaultSelectedButtonIdRef()).isEqualTo(4);
	}

	@Test
	void clonedRootButtonsReturnToMatchingRootSelection() throws IOException {
		List<IgsPage> pages = new PopupMenuIgsBuilder().build(configWithTracks(2, 2)).getCompositionSegment()
				.getInteractiveComposition().getPages();
		List<IgsButton> rootButtons = buttons(pages.get(0));
		List<IgsButton> buttons = buttons(pages.get(1));

		assertThat(rootButtons.subList(0, 2))
				.allSatisfy(button -> assertThat(button.getNavigationCommands().get(0).getOperand2()).isEqualTo(4));

		for (int i = 0; i < 3; i++) {
			assertThat(buttons.get(i).getNavigationCommands()).hasSize(3);
			assertThat(buttons.get(i).getNavigationCommands().get(0).getOperand2()).isEqualTo(i + 1);
			assertThat(buttons.get(i).getNavigationCommands().get(1).getOperand2()).isZero();
		}
		assertThat(buttons.subList(3, 5)).allSatisfy(button -> assertThat(button.getNavigationCommands())
				.singleElement().satisfies(command -> assertThat(command.getMnemonic()).isEqualTo("SET_STREAM")));
	}

	@Test
	void keepsSingleGroupMenuAsOnePageWithoutAutoActionButtons() throws IOException {
		IgsDisplaySet displaySet = new PopupMenuIgsBuilder().build(configWithTracks(2, 1));
		List<IgsPage> pages = displaySet.getCompositionSegment().getInteractiveComposition().getPages();

		assertThat(pages).hasSize(1);
		assertThat(buttons(pages.get(0))).hasSize(3).noneMatch(IgsButton::isAutoAction);
		assertThat(pages.get(0).getDefaultSelectedButtonIdRef()).isEqualTo(1);
	}

	@Test
	void rendersInheritedBackgroundsBeforeSelectableButtons() throws IOException {
		PopupMenuConfig config = configWithTracks(2, 2);
		PageBackgrounds backgrounds = new PageBackgrounds();
		backgrounds.setShared(List.of(svgLayer("#101010", 10)));
		backgrounds.setRoot(List.of(svgLayer("#202020", 20)));
		backgrounds.setAudio(List.of(svgLayer("#303030", 30)));
		backgrounds.setSubtitles(List.of(svgLayer("#404040", 40)));
		config.setBackgrounds(backgrounds);

		IgsDisplaySet displaySet = new PopupMenuIgsBuilder().build(config);
		List<IgsPage> pages = displaySet.getCompositionSegment().getInteractiveComposition().getPages();

		assertThat(buttons(pages.get(0))).extracting(IgsButton::getXPos).startsWith(10, 20);
		assertThat(buttons(pages.get(1))).extracting(IgsButton::getXPos).startsWith(10, 20, 30);
		assertThat(buttons(pages.get(2))).extracting(IgsButton::getXPos).startsWith(10, 20, 40);
		assertThat(buttons(pages.get(0))).extracting(IgsButton::getId).containsExactly(4, 5, 1, 2, 3);
		assertThat(buttons(pages.get(1))).extracting(IgsButton::getId).startsWith(6, 7, 8, 1, 2, 3, 4, 5);
		assertThat(pages.get(0).getDefaultSelectedButtonIdRef()).isEqualTo(1);

		IgsButton firstDecoration = buttons(pages.get(0)).get(0);
		assertThat(firstDecoration.getNavigationCommands()).isEmpty();
		assertThat(firstDecoration.getUpperButtonIdRef()).isEqualTo(firstDecoration.getId());
		assertThat(firstDecoration.getNormalStartObjectIdRef()).isEqualTo(firstDecoration.getSelectedStartObjectIdRef())
				.isEqualTo(firstDecoration.getActivatedStartObjectIdRef()).isZero();
		assertThat(buttons(pages.get(0)).get(2).getNormalStartObjectIdRef()).isEqualTo(2);
		assertThat(displaySet.getObjects()).hasSize(47);
	}

	@Test
	void fitsBackgroundAroundRenderedSelectableBounds() throws IOException {
		PopupMenuConfig config = configWithTracks(2, 1);
		BackgroundLayer layer = svgLayer("#101010", 0);
		layer.getLayout().setMode(BackgroundLayoutMode.SELECTABLE_BOUNDS);
		layer.getLayout().setMarginTop(7);
		layer.getLayout().setMarginRight(11);
		layer.getLayout().setMarginBottom(13);
		layer.getLayout().setMarginLeft(17);
		PageBackgrounds backgrounds = new PageBackgrounds();
		backgrounds.setAudio(List.of(layer));
		config.setBackgrounds(backgrounds);

		IgsDisplaySet displaySet = new PopupMenuIgsBuilder().build(config);
		IgsPage page = displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(0);
		List<IgsButton> pageButtons = buttons(page);
		IgsButton decoration = pageButtons.get(0);
		List<IgsButton> selectable = pageButtons.subList(1, pageButtons.size());
		int left = selectable.stream().mapToInt(IgsButton::getXPos).min().orElseThrow();
		int top = selectable.stream().mapToInt(IgsButton::getYPos).min().orElseThrow();
		int right = selectable.stream().mapToInt(button -> button.getXPos() + object(displaySet, button).getWidth())
				.max().orElseThrow();
		int bottom = selectable.stream().mapToInt(button -> button.getYPos() + object(displaySet, button).getHeight())
				.max().orElseThrow();
		IgsObject decorationObject = object(displaySet, decoration);

		assertThat(decoration.getXPos()).isEqualTo(left - 17);
		assertThat(decoration.getYPos()).isEqualTo(top - 7);
		assertThat(decorationObject.getWidth()).isEqualTo(right - left + 28);
		assertThat(decorationObject.getHeight()).isEqualTo(bottom - top + 20);
	}

	@Test
	void stretchesRasterAcrossFullWidthBottomBanner(@TempDir Path tempDir) throws IOException {
		BufferedImage sourceImage = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
		sourceImage.setRGB(0, 0, 0x80FF0000);
		Path sourcePath = tempDir.resolve("background.png");
		ImageIO.write(sourceImage, "png", sourcePath.toFile());
		ImageReference source = new ImageReference();
		source.setSourcePath(sourcePath.toString());
		BackgroundLayout layout = new BackgroundLayout();
		layout.setMode(BackgroundLayoutMode.FULL_WIDTH_BOTTOM);
		layout.setHeight(120);
		layout.setEdgeOffset(25);
		layout.setMarginTop(7);
		layout.setMarginRight(11);
		layout.setMarginBottom(13);
		layout.setMarginLeft(17);
		BackgroundLayer layer = new BackgroundLayer();
		layer.setSource(source);
		layer.setLayout(layout);
		PageBackgrounds backgrounds = new PageBackgrounds();
		backgrounds.setAudio(List.of(layer));
		PopupMenuConfig config = configWithTracks(2, 1);
		config.setBackgrounds(backgrounds);

		IgsDisplaySet displaySet = new PopupMenuIgsBuilder().build(config);
		IgsButton decoration = buttons(displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(0))
				.get(0);
		IgsObject decorationObject = object(displaySet, decoration);

		assertThat(decoration.getXPos()).isEqualTo(17);
		assertThat(decoration.getYPos()).isEqualTo(915);
		assertThat(decorationObject.getWidth()).isEqualTo(1892);
		assertThat(decorationObject.getHeight()).isEqualTo(140);
	}

	@Test
	void stretchesRasterAcrossFullHeightLeftBanner(@TempDir Path tempDir) throws IOException {
		BufferedImage sourceImage = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
		sourceImage.setRGB(0, 0, 0x80FF0000);
		Path sourcePath = tempDir.resolve("background.png");
		ImageIO.write(sourceImage, "png", sourcePath.toFile());
		ImageReference source = new ImageReference();
		source.setSourcePath(sourcePath.toString());
		BackgroundLayout layout = new BackgroundLayout();
		layout.setMode(BackgroundLayoutMode.FULL_HEIGHT_LEFT);
		layout.setWidth(120);
		layout.setEdgeOffset(25);
		layout.setMarginTop(7);
		layout.setMarginRight(11);
		layout.setMarginBottom(13);
		layout.setMarginLeft(17);
		BackgroundLayer layer = new BackgroundLayer();
		layer.setSource(source);
		layer.setLayout(layout);
		PageBackgrounds backgrounds = new PageBackgrounds();
		backgrounds.setAudio(List.of(layer));
		PopupMenuConfig config = configWithTracks(2, 1);
		config.setBackgrounds(backgrounds);

		IgsDisplaySet displaySet = new PopupMenuIgsBuilder().build(config);
		IgsButton decoration = buttons(displaySet.getCompositionSegment().getInteractiveComposition().getPages().get(0))
				.get(0);
		IgsObject decorationObject = object(displaySet, decoration);

		assertThat(decoration.getXPos()).isEqualTo(25);
		assertThat(decoration.getYPos()).isEqualTo(7);
		assertThat(decorationObject.getWidth()).isEqualTo(148);
		assertThat(decorationObject.getHeight()).isEqualTo(1060);
	}

	@Test
	void rejectsBackgroundOutsideScreen() {
		PopupMenuConfig config = configWithTracks(2, 1);
		BackgroundLayer layer = svgLayer("#101010", 1915);
		PageBackgrounds backgrounds = new PageBackgrounds();
		backgrounds.setAudio(List.of(layer));
		config.setBackgrounds(backgrounds);

		assertThatThrownBy(() -> new PopupMenuIgsBuilder().build(config)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("outside the 1920x1080 screen");
	}

	@Test
	void rendersSvgBackgroundAssetsFromFile() throws IOException {
		PopupMenuConfig config = configWithTracks(2, 2);
		config.setLayout(PopupMenuConfig.Layout.HORIZONTAL_BOTTOM);

		ImageReference bannerSource = new ImageReference();
		ImageReference.SyntheticImageSource bannerSvg = new ImageReference.SyntheticImageSource();
		bannerSvg.setType("svg");
		bannerSvg.setSrcPath("../assets/popup-menu-banner.svg");
		bannerSource.setSyntheticImage(bannerSvg);

		BackgroundLayout bannerLayout = new BackgroundLayout();
		bannerLayout.setMode(BackgroundLayoutMode.FULL_WIDTH_BOTTOM);
		bannerLayout.setHeight(220);
		bannerLayout.setEdgeOffset(20);

		BackgroundLayer bannerLayer = new BackgroundLayer();
		bannerLayer.setSource(bannerSource);
		bannerLayer.setLayout(bannerLayout);

		ImageReference cardSource = new ImageReference();
		ImageReference.SyntheticImageSource cardSvg = new ImageReference.SyntheticImageSource();
		cardSvg.setType("svg");
		cardSvg.setSrcPath("../assets/popup-panel-card.svg");
		cardSource.setSyntheticImage(cardSvg);

		BackgroundLayout cardLayout = new BackgroundLayout();
		cardLayout.setMode(BackgroundLayoutMode.SELECTABLE_BOUNDS);
		cardLayout.setMarginTop(16);
		cardLayout.setMarginRight(24);
		cardLayout.setMarginBottom(16);
		cardLayout.setMarginLeft(24);

		BackgroundLayer cardLayer = new BackgroundLayer();
		cardLayer.setSource(cardSource);
		cardLayer.setLayout(cardLayout);

		PageBackgrounds backgrounds = new PageBackgrounds();
		backgrounds.setShared(List.of(bannerLayer));
		backgrounds.setAudio(List.of(cardLayer));
		backgrounds.setSubtitles(List.of(cardLayer));
		config.setBackgrounds(backgrounds);

		IgsDisplaySet displaySet = new PopupMenuIgsBuilder().build(config);
		assertThat(displaySet).isNotNull();
		assertThat(displaySet.getObjects()).isNotEmpty();
	}

	private static List<IgsButton> buttons(IgsPage page) {
		return page.getBogs().stream().map(bog -> bog.getButtons().get(0)).toList();
	}

	private static IgsObject object(IgsDisplaySet displaySet, IgsButton button) {
		return displaySet.getObjects().get(button.getNormalStartObjectIdRef());
	}

	private static BackgroundLayer svgLayer(String color, int x) {
		ImageReference.SyntheticImageSource svg = new ImageReference.SyntheticImageSource();
		svg.setType("svg");
		svg.setData("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"2\" height=\"2\">"
				+ "<rect width=\"2\" height=\"2\" fill=\"" + color + "\"/></svg>");
		ImageReference source = new ImageReference();
		source.setSyntheticImage(svg);
		BackgroundLayout layout = new BackgroundLayout();
		layout.setMode(BackgroundLayoutMode.ABSOLUTE);
		layout.setX(x);
		layout.setY(10);
		layout.setWidth(10);
		layout.setHeight(10);
		BackgroundLayer layer = new BackgroundLayer();
		layer.setSource(source);
		layer.setLayout(layout);
		return layer;
	}

	private static PopupMenuConfig configWithTracks(int audioCount, int subtitleCount) {
		PopupMenuConfig config = new PopupMenuConfig();
		config.setAudioTracks(tracks("Audio", audioCount));
		config.setSubtitleTracks(tracks("Subtitle", subtitleCount));
		return config;
	}

	private static List<TrackEntry> tracks(String prefix, int count) {
		return java.util.stream.IntStream.rangeClosed(1, count).mapToObj(index -> {
			TrackEntry entry = new TrackEntry();
			entry.setStreamIndex(index);
			entry.setDisplayName(prefix + " " + index);
			return entry;
		}).toList();
	}
}
