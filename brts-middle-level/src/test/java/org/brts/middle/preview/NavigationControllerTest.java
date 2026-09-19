package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/test/java/org/brts/middle/preview/NavigationControllerTest.java' is part of BRTS.
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
	void moveToNeighbour_autoActionButton_activatesImmediately() {
		IgsButton start = button(1, List.of());
		start.setLowerButtonIdRef(2);
		IgsButton autoAction = button(2, NavigationCommandUtils.setButtonPage(1, 3));
		autoAction.setAutoAction(true);
		IgsPage root = page(0, 1, start, autoAction);
		IgsPage destination = page(1, 3, button(3, List.of()));

		DisplaySetPreviewModel model = modelOnPage0(root, destination);
		NavigationController nav = new NavigationController(model);

		NavigationResult result = nav.moveDown();

		assertThat(result.getType()).isEqualTo(NavigationResult.Type.ACTIVATED);
		assertThat(model.getCurrentPageIndex()).isEqualTo(1);
		assertThat(model.getSelectedButtonId()).isEqualTo(3);
	}

	@Test
	void goToPage_defaultAutoAction_activatesAndChainsToNonAutoActionButton() {
		IgsPage root = page(0, 1, button(1, List.of()));
		IgsButton firstAutoAction = button(2, NavigationCommandUtils.setButtonPage(2, 4));
		firstAutoAction.setAutoAction(true);
		IgsPage firstDestination = page(1, 2, firstAutoAction);
		IgsButton finalAutoAction = button(4, NavigationCommandUtils.setButtonPage(3, 6));
		finalAutoAction.setAutoAction(true);
		IgsPage secondDestination = page(2, 4, finalAutoAction);
		IgsPage finalDestination = page(3, 6, button(6, List.of()));

		DisplaySetPreviewModel model = modelOnPage0(root, firstDestination, secondDestination, finalDestination);
		NavigationController nav = new NavigationController(model);

		NavigationResult result = nav.goToPage(1);

		assertThat(result.getType()).isEqualTo(NavigationResult.Type.ACTIVATED);
		assertThat(model.getCurrentPageIndex()).isEqualTo(3);
		assertThat(model.getSelectedButtonId()).isEqualTo(6);
	}

	@Test
	void activateInitialAutoAction_runsAutoActionForInitialSelection() {
		IgsButton autoAction = button(1, NavigationCommandUtils.setButtonPage(1, 2));
		autoAction.setAutoAction(true);
		IgsPage startup = page(0, 1, autoAction);
		IgsPage menu = page(1, 2, button(2, List.of()));

		DisplaySetPreviewModel model = modelOnPage0(startup, menu);
		NavigationController nav = new NavigationController(model);

		NavigationResult result = nav.activateInitialAutoAction();

		assertThat(result.getType()).isEqualTo(NavigationResult.Type.ACTIVATED);
		assertThat(model.getCurrentPageIndex()).isEqualTo(1);
		assertThat(model.getSelectedButtonId()).isEqualTo(2);
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

	@Test
	void activate_setButtonPageTargetingButtonIdZero_doesNotThrow() {
		// Regression test: button id 0 is a legitimate, explicitly-set GPR value that must not be dropped as "unset".
		IgsButton trigger = button(1, NavigationCommandUtils.setButtonPage(1, 0));
		IgsPage root = page(0, 1, trigger);

		IgsButton zeroButton = button(0, List.of());
		IgsPage target = page(1, 0, zeroButton);

		DisplaySetPreviewModel model = modelOnPage0(root, target);
		NavigationController nav = new NavigationController(model);

		nav.activate();

		assertThat(model.getCurrentPageIndex()).isEqualTo(1);
		assertThat(model.getSelectedButtonId()).isEqualTo(0);
	}

	@Test
	void gprState_persistsAcrossActivations_withoutManualModelSync() {
		int gprPage = 999;
		IgsButton setGpr = button(1, List.of(NavigationCommand
				.fromParsed(ParsedNavigationCommand.compile(NavigationCommandMnemonic.MOVE, gprPage, false, 1, true))));
		IgsButton useGpr = button(2, List.of(NavigationCommand.fromParsed(ParsedNavigationCommand
				.compile(NavigationCommandMnemonic.SET_BUTTON_PAGE, 0L, true, gprPage | 0x8000_0000L, false))));
		IgsPage root = page(0, 1, setGpr, useGpr);
		IgsPage target = page(1, 3, button(3, List.of()));

		DisplaySetPreviewModel model = modelOnPage0(root, target);
		NavigationController nav = new NavigationController(model);

		nav.activate(); // MOVE GPR[999] = 1
		model.setSelectedButtonId(2);
		nav.activate(); // SET_BUTTON_PAGE page=GPR[999] → switches to page id 1, relying on shared GPR state

		assertThat(model.getCurrentPageIndex()).isEqualTo(1);
	}

}
