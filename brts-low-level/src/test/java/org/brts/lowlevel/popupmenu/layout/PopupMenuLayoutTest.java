package org.brts.lowlevel.popupmenu.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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