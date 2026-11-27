/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/popupmenu/layout/PopupMenuLayoutTest.java' is part of BRTS.
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

package org.brts.lowlevel.popupmenu.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.brts.lowlevel.bdmv.NavigationCommandUtils.setAudio;

import java.awt.image.BufferedImage;
import java.util.List;

import org.brts.common.menu.TextRenderer.ButtonImages;
import org.brts.lowlevel.igs.IgsMenuAssembler;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.junit.jupiter.api.Test;

class PopupMenuLayoutTest {

	private static final int SCREEN_WIDTH = 1920;
	private static final int SCREEN_HEIGHT = 1080;

	@Test
	void defaultsToVerticalLayout() {
		assertThat(new PopupMenuConfig().getLayout()).isEqualTo(PopupMenuConfig.Layout.VERTICAL_LIST);
	}

	@Test
	void horizontalLayoutPlacesRootButtonsInBottomRowAndWrapsLeftAndRight() {
		List<IgsMenuAssembler.LabeledButton> buttons = labeledButtons(3, 160);
		PopupMenuLayout.Page page = new HorizontalBottomPopupMenuLayout()
				.layout(List.of(buttons), SCREEN_WIDTH, SCREEN_HEIGHT).get(0);

		assertThat(page.buttons()).hasSize(3);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::y).containsOnly(960);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::x).containsExactly(704, 880, 1056);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::upperButtonIdRef).containsExactly(1, 2,
				3);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::lowerButtonIdRef).containsExactly(1, 2,
				3);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::leftButtonIdRef).containsExactly(3, 1,
				2);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::rightButtonIdRef).containsExactly(2, 3,
				1);
	}

	@Test
	void horizontalLayoutKeepsRootVisibleAndMovesDownTowardIt() {
		List<IgsMenuAssembler.LabeledButton> root = labeledButtons(3, 160);
		List<IgsMenuAssembler.LabeledButton> tracks = labeledButtons(2, 200);
		PopupMenuLayout.Page page = new HorizontalBottomPopupMenuLayout()
				.layout(List.of(root, compositePage(root, tracks)), SCREEN_WIDTH, SCREEN_HEIGHT).get(1);

		assertThat(page.buttons().subList(0, 3)).extracting(IgsMenuAssembler.PositionedButton::x).containsExactly(704,
				880, 1056);
		assertThat(page.buttons().subList(0, 3)).extracting(IgsMenuAssembler.PositionedButton::y).containsOnly(960);
		assertThat(page.buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::y).containsExactly(856,
				904);
		assertThat(page.buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::upperButtonIdRef)
				.containsExactly(4, 4);
		assertThat(page.buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::lowerButtonIdRef)
				.containsExactly(5, 1);
		assertThat(page.defaultSelectedButtonIdRef()).isEqualTo(4);
	}

	@Test
	void verticalLayoutPlacesPersistentRootLeftOfSubmenuAndMovesLeftTowardIt() {
		List<IgsMenuAssembler.LabeledButton> root = labeledButtons(3, 160);
		List<IgsMenuAssembler.LabeledButton> tracks = labeledButtons(2, 200);
		List<PopupMenuLayout.Page> pages = new VerticalPopupMenuLayout().layout(
				List.of(root, compositePage(root, tracks), compositePage(root, tracks)), SCREEN_WIDTH, SCREEN_HEIGHT);

		assertThat(pages.get(0).buttons()).extracting(IgsMenuAssembler.PositionedButton::x).containsOnly(760);
		assertThat(pages.get(1).buttons().subList(0, 3)).extracting(IgsMenuAssembler.PositionedButton::x)
				.containsOnly(760);
		assertThat(pages.get(1).buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::x)
				.containsOnly(960);
		assertThat(pages.get(1).buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::leftButtonIdRef)
				.containsOnly(1);
		assertThat(pages.get(2).buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::leftButtonIdRef)
				.containsOnly(2);
		assertThat(pages.get(1).defaultSelectedButtonIdRef()).isEqualTo(4);
	}

	@Test
	void verticalLayoutPreservesOriginalCoordinatesAndNavigation() {
		List<IgsMenuAssembler.LabeledButton> buttons = labeledButtons(2, 200);
		PopupMenuLayout.Page page = new VerticalPopupMenuLayout().layout(List.of(buttons), SCREEN_WIDTH, SCREEN_HEIGHT)
				.get(0);

		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::x).containsOnly(560);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::y).containsExactly(912, 960);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::upperButtonIdRef).containsExactly(2,
				1);
		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::lowerButtonIdRef).containsExactly(2,
				1);
	}

	@Test
	void horizontalLayoutRejectsRowsThatDoNotFit() {
		List<IgsMenuAssembler.LabeledButton> buttons = labeledButtons(3, 700);

		assertThatThrownBy(
				() -> new HorizontalBottomPopupMenuLayout().layout(List.of(buttons), SCREEN_WIDTH, SCREEN_HEIGHT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Popup horizontal button row does not fit within the screen width");
	}

	@Test
	void verticalLayoutUsesRenderedHeights() {
		ButtonImages images = buttonImages(200, 60);
		List<IgsMenuAssembler.LabeledButton> buttons = List.of(new IgsMenuAssembler.LabeledButton(images, List.of()),
				new IgsMenuAssembler.LabeledButton(images, List.of()));

		PopupMenuLayout.Page page = new VerticalPopupMenuLayout().layout(List.of(buttons), SCREEN_WIDTH, SCREEN_HEIGHT)
				.get(0);

		assertThat(page.buttons()).extracting(IgsMenuAssembler.PositionedButton::y).containsExactly(872, 940);
	}

	@Test
	void horizontalSubmenuLayoutSharesSinglePageAndRootRowBehavior() {
		List<IgsMenuAssembler.LabeledButton> root = labeledButtons(3, 160);
		List<PopupMenuLayout.Page> expected = new HorizontalBottomPopupMenuLayout().layout(List.of(root), SCREEN_WIDTH,
				SCREEN_HEIGHT);
		assertThat(new HorizontalBottomHorizontalPopupMenuLayout().layout(List.of(root), SCREEN_WIDTH, SCREEN_HEIGHT))
				.isEqualTo(expected);
		assertThat(new HorizontalBottomHorizontalPopupMenuLayout()
				.layout(List.of(root, compositePage(root, labeledButtons(2, 200))), SCREEN_WIDTH, SCREEN_HEIGHT).get(0))
				.isEqualTo(expected.get(0));
	}

	@Test
	void horizontalSubmenusWrapWithinTheirIdsAndReturnDownToMatchingRootControl() {
		List<IgsMenuAssembler.LabeledButton> root = labeledButtons(3, 160);
		List<IgsMenuAssembler.LabeledButton> clonedRoot = root.stream()
				.map(button -> new IgsMenuAssembler.LabeledButton(button.images(), button.commands(), true)).toList();
		List<IgsMenuAssembler.LabeledButton> tracks = List.of(
				new IgsMenuAssembler.LabeledButton(buttonImages(200, 40), setAudio(1), true),
				new IgsMenuAssembler.LabeledButton(buttonImages(200, 40), setAudio(2)));
		List<PopupMenuLayout.Page> pages = new HorizontalBottomHorizontalPopupMenuLayout().layout(
				List.of(root, compositePage(clonedRoot, tracks), compositePage(clonedRoot, tracks)), SCREEN_WIDTH,
				SCREEN_HEIGHT);

		assertThat(pages.get(0).defaultSelectedButtonIdRef()).isEqualTo(1);
		for (int pageId = 1; pageId < pages.size(); pageId++) {
			PopupMenuLayout.Page page = pages.get(pageId);
			List<IgsMenuAssembler.PositionedButton> rootRow = page.buttons().subList(0, 3);
			List<IgsMenuAssembler.PositionedButton> submenu = page.buttons().subList(3, 5);
			assertThat(rootRow).extracting(IgsMenuAssembler.PositionedButton::x).containsExactly(704, 880, 1056);
			assertThat(rootRow).extracting(IgsMenuAssembler.PositionedButton::y).containsOnly(960);
			assertThat(rootRow).allMatch(IgsMenuAssembler.PositionedButton::autoAction);
			assertThat(rootRow).extracting(IgsMenuAssembler.PositionedButton::upperButtonIdRef).containsExactly(1, 2,
					3);
			assertThat(rootRow).extracting(IgsMenuAssembler.PositionedButton::lowerButtonIdRef).containsExactly(1, 2,
					3);
			assertThat(rootRow).extracting(IgsMenuAssembler.PositionedButton::leftButtonIdRef).containsExactly(1, 2, 3);
			assertThat(rootRow).extracting(IgsMenuAssembler.PositionedButton::rightButtonIdRef).containsExactly(1, 2,
					3);
			assertThat(submenu).extracting(IgsMenuAssembler.PositionedButton::x).containsExactly(752, 968);
			assertThat(submenu).extracting(IgsMenuAssembler.PositionedButton::y).containsOnly(904);
			assertThat(submenu).extracting(IgsMenuAssembler.PositionedButton::upperButtonIdRef).containsExactly(4, 5);
			assertThat(submenu).extracting(IgsMenuAssembler.PositionedButton::lowerButtonIdRef).containsOnly(pageId);
			assertThat(submenu).extracting(IgsMenuAssembler.PositionedButton::leftButtonIdRef).containsExactly(5, 4);
			assertThat(submenu).extracting(IgsMenuAssembler.PositionedButton::rightButtonIdRef).containsExactly(5, 4);
			assertThat(page.defaultSelectedButtonIdRef()).isEqualTo(4);
			for (int i = 0; i < tracks.size(); i++) {
				assertThat(submenu.get(i).images()).isSameAs(tracks.get(i).images());
				assertThat(submenu.get(i).commands()).isSameAs(tracks.get(i).commands());
				assertThat(submenu.get(i).autoAction()).isEqualTo(tracks.get(i).autoAction());
			}
		}
	}

	@Test
	void horizontalSubmenusUseRenderedWidthsAndMaximumRowHeights() {
		List<IgsMenuAssembler.LabeledButton> root = List.of(
				new IgsMenuAssembler.LabeledButton(buttonImages(160, 40), List.of()),
				new IgsMenuAssembler.LabeledButton(buttonImages(240, 60), List.of()));
		List<IgsMenuAssembler.LabeledButton> tracks = List.of(
				new IgsMenuAssembler.LabeledButton(buttonImages(100, 80), List.of()),
				new IgsMenuAssembler.LabeledButton(buttonImages(300, 40), List.of()));
		PopupMenuLayout.Page page = new HorizontalBottomHorizontalPopupMenuLayout()
				.layout(List.of(root, compositePage(root, tracks)), SCREEN_WIDTH, SCREEN_HEIGHT).get(1);

		assertThat(page.buttons().subList(0, 2)).extracting(IgsMenuAssembler.PositionedButton::x).containsExactly(752,
				928);
		assertThat(page.buttons().subList(0, 2)).extracting(IgsMenuAssembler.PositionedButton::y).containsOnly(940);
		assertThat(page.buttons().subList(2, 4)).extracting(IgsMenuAssembler.PositionedButton::x).containsExactly(752,
				868);
		assertThat(page.buttons().subList(2, 4)).extracting(IgsMenuAssembler.PositionedButton::y).containsOnly(844);
	}

	@Test
	void singleEntryHorizontalSubmenuHasSelfNeighboursAndReturnsDownToRoot() {
		List<IgsMenuAssembler.LabeledButton> root = labeledButtons(3, 160);
		PopupMenuLayout.Page page = new HorizontalBottomHorizontalPopupMenuLayout()
				.layout(List.of(root, compositePage(root, labeledButtons(1, 200))), SCREEN_WIDTH, SCREEN_HEIGHT).get(1);
		IgsMenuAssembler.PositionedButton entry = page.buttons().get(3);
		assertThat(entry.x()).isEqualTo(860);
		assertThat(entry.y()).isEqualTo(904);
		assertThat(entry.upperButtonIdRef()).isEqualTo(4);
		assertThat(entry.leftButtonIdRef()).isEqualTo(4);
		assertThat(entry.rightButtonIdRef()).isEqualTo(4);
		assertThat(entry.lowerButtonIdRef()).isEqualTo(1);
	}

	@Test
	void horizontalSubmenuAcceptsExactWidthAndHeightFit() {
		List<IgsMenuAssembler.LabeledButton> root = labeledButtons(3, 160);
		PopupMenuLayout.Page page = new HorizontalBottomHorizontalPopupMenuLayout()
				.layout(List.of(root, compositePage(root, labeledButtons(2, 912))), SCREEN_WIDTH, 176).get(1);
		assertThat(page.buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::x).containsExactly(40,
				968);
		assertThat(page.buttons().subList(3, 5)).extracting(IgsMenuAssembler.PositionedButton::y).containsOnly(0);
	}

	@Test
	void horizontalSubmenuRejectsWidthAndHeightOverflow() {
		List<IgsMenuAssembler.LabeledButton> root = labeledButtons(3, 160);
		HorizontalBottomHorizontalPopupMenuLayout layout = new HorizontalBottomHorizontalPopupMenuLayout();
		assertThatThrownBy(() -> layout.layout(List.of(root, compositePage(root, labeledButtons(2, 913))), SCREEN_WIDTH,
				SCREEN_HEIGHT)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Popup horizontal button row does not fit within the screen width");
		assertThatThrownBy(
				() -> layout.layout(List.of(root, compositePage(root, labeledButtons(2, 200))), SCREEN_WIDTH, 175))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Popup submenu button row does not fit above the bottom row");
		assertThatThrownBy(() -> layout.layout(List.of(labeledButtons(3, 700)), SCREEN_WIDTH, SCREEN_HEIGHT))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Popup horizontal button row does not fit within the screen width");
	}

	private static List<IgsMenuAssembler.LabeledButton> labeledButtons(int count, int width) {
		ButtonImages images = buttonImages(width, 40);
		return java.util.stream.IntStream.range(0, count)
				.mapToObj(index -> new IgsMenuAssembler.LabeledButton(images, List.of())).toList();
	}

	private static List<IgsMenuAssembler.LabeledButton> compositePage(List<IgsMenuAssembler.LabeledButton> root,
			List<IgsMenuAssembler.LabeledButton> tracks) {
		return java.util.stream.Stream.concat(root.stream(), tracks.stream()).toList();
	}

	private static ButtonImages buttonImages(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		return new ButtonImages(image, image, image, width, height);
	}
}
