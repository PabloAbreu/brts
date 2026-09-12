package org.brts.middle.preview;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.brts.lowlevel.bdmv.NavigationCommandMnemonic;
import org.brts.lowlevel.bdmv.NavigationCommandUtils;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand;
import org.brts.lowlevel.igs.model.IgsBog;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsPage;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NavigationController}, focused on simulating {@code SET_BUTTON_PAGE} on activation.
 */
class NavigationControllerTest {

	private static IgsButton button(int id, List<NavigationCommand> commands) {
		IgsButton btn = new IgsButton();
		btn.setId(id);
		btn.setNavigationCommands(commands);
		return btn;
	}

	private static IgsPage page(int id, int defaultSelectedButtonId, IgsButton... buttons) {
		IgsPage page = new IgsPage();
		page.setId(id);
		page.setDefaultSelectedButtonIdRef(defaultSelectedButtonId);
		for (IgsButton btn : buttons) {
			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(btn.getId());
			bog.getButtons().add(btn);
			page.getBogs().add(bog);
		}
		return page;
	}

	private static DisplaySetPreviewModel modelOnPage0(IgsPage... pages) {
		DisplaySetPreviewModel model = new DisplaySetPreviewModel();
		model.setPages(List.of(pages));
		model.setCurrentPageIndex(0);
		model.resetBogState();
		model.setSelectedButtonId(1);
		return model;
	}

	@Test
	void activate_setButtonPage_switchesPageAndSelectsExplicitButton() {
		IgsButton toAudio = button(1, NavigationCommandUtils.setButtonPage(1, 2));
		IgsPage root = page(0, 1, toAudio);

		IgsButton defaultBtn = button(5, List.of());
		IgsButton audioTrack = button(2, List.of());
		IgsPage audioPage = page(1, 5, defaultBtn, audioTrack);

		DisplaySetPreviewModel model = modelOnPage0(root, audioPage);
		NavigationController nav = new NavigationController(model);

		nav.activate();

		assertThat(model.getCurrentPageIndex()).isEqualTo(1);
		assertThat(model.getSelectedButtonId()).isEqualTo(2);
	}

	@Test
	void activate_setButtonPageOnly_reselectsButtonOnSamePage() {
		// Button-only SET_BUTTON_PAGE (page flag off) is not emitted by any production helper today, but must decode
		// and apply correctly since the HDMV opcode supports it independently of NavigationCommandUtils.
		ParsedNavigationCommand parsed = ParsedNavigationCommand.compile(NavigationCommandMnemonic.SET_BUTTON_PAGE,
				7L | 0x8000_0000L, true, 0L, true);
		IgsButton trigger = button(1, List.of(NavigationCommand.fromParsed(parsed)));
		IgsButton target = button(7, List.of());
		IgsPage root = page(0, 1, trigger, target);

		DisplaySetPreviewModel model = modelOnPage0(root);
		NavigationController nav = new NavigationController(model);

		nav.activate();

		assertThat(model.getCurrentPageIndex()).isEqualTo(0);
		assertThat(model.getSelectedButtonId()).isEqualTo(7);
	}

	@Test
	void activate_withoutSetButtonPage_doesNotChangePageOrSelection() {
		IgsButton exit = button(1, NavigationCommandUtils.popupOff());
		IgsPage root = page(0, 1, exit);

		DisplaySetPreviewModel model = modelOnPage0(root);
		NavigationController nav = new NavigationController(model);

		NavigationResult result = nav.activate();

		assertThat(result.getType()).isEqualTo(NavigationResult.Type.ACTIVATED);
		assertThat(model.getCurrentPageIndex()).isEqualTo(0);
		assertThat(model.getSelectedButtonId()).isEqualTo(1);
	}

	@Test
	void goToPage_withoutExplicitButton_usesPageDefault() {
		IgsPage root = page(0, 1, button(1, List.of()));
		IgsPage other = page(1, 9, button(9, List.of()));

		DisplaySetPreviewModel model = modelOnPage0(root, other);
		NavigationController nav = new NavigationController(model);

		nav.goToPage(1);

		assertThat(model.getCurrentPageIndex()).isEqualTo(1);
		assertThat(model.getSelectedButtonId()).isEqualTo(9);
	}

}
