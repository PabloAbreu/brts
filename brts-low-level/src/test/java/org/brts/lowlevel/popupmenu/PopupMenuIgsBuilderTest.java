package org.brts.lowlevel.popupmenu;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.igs.model.IgsPage;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.TrackEntry;
import org.junit.jupiter.api.Test;

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

	private static List<IgsButton> buttons(IgsPage page) {
		return page.getBogs().stream().map(bog -> bog.getButtons().get(0)).toList();
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