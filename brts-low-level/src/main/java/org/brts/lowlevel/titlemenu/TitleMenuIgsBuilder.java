package org.brts.lowlevel.titlemenu;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/TitleMenuIgsBuilder.java' is part of BRTS.
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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.bdmv.NavigationCommandUtils;
import org.brts.lowlevel.igs.IgsMenuAssembler;
import org.brts.lowlevel.igs.PaletteBuilder;
import org.brts.lowlevel.igs.model.IgsBog;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsCompositionSegment;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.igs.model.IgsInteractiveComposition;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPage;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.NavigationOverride;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.brts.lowlevel.titlemenu.layout.LayoutResult;
import org.brts.lowlevel.titlemenu.layout.LayoutResult.PositionedButton;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds a complete {@link IgsDisplaySet} from a title menu {@link LayoutResult}.
 * <p>
 * The builder:
 * <ol>
 * <li>Builds a shared palette from all button images</li>
 * <li>RLE-encodes all images into {@link IgsObject}s</li>
 * <li>Creates Button Object Groups (BOGs) with JUMP_TITLE navigation commands</li>
 * <li>Wires D-pad neighbour references (auto-calculated from grid positions, with optional overrides)</li>
 * <li>Assembles the full display set (ICS, palette, windows, objects)</li>
 * </ol>
 */
@Slf4j
public class TitleMenuIgsBuilder {
	// has no effect on decoding by players, but matches the button IDs used in the JUMP_TITLE
	// commands emitted by the layout algorithms
	private static final int BUTTON_BASE_ID = 1;

	/**
	 * Builds the complete display set from the layout result.
	 *
	 * @param layoutResult the positioned buttons from a layout implementation
	 * @param descriptor   the title menu descriptor (for screen dimensions and title entries)
	 * @return a fully-populated {@link IgsDisplaySet} ready for encoding via
	 *         {@link org.brts.lowlevel.igs.IgsMuxer#encodeDisplaySet}
	 * @throws IOException on encoding errors
	 */
	public IgsDisplaySet build(LayoutResult layoutResult, TitleMenuDescriptor descriptor) throws IOException {
		int screenW = descriptor.getScreenWidth();
		int screenH = descriptor.getScreenHeight();
		List<PositionedButton> buttons = layoutResult.getButtons();
		boolean hasStartupPage = !buttons.isEmpty();

		// ── Collect all button images for palette building ───────────────────

		List<BufferedImage> allImages = new ArrayList<>();
		for (PositionedButton btn : buttons) {
			allImages.add(btn.getNormalImage());
			allImages.add(btn.getSelectedImage());
			allImages.add(btn.getActivatedImage());
		}

		// ── Build BOGs with navigation commands ─────────────────────────────

		List<IgsBog> bogs = new ArrayList<>();
		for (int i = 0; i < buttons.size(); i++) {
			PositionedButton pb = buttons.get(i);
			int buttonId = i + BUTTON_BASE_ID; // 1-based button IDs

			// Object IDs: 3 images per button (normal, selected, activated)
			int normalObjId = i * 3;
			int selectedObjId = i * 3 + 1;
			int activatedObjId = i * 3 + 2;

			// JUMP_TITLE navigation command
			List<NavigationCommand> navCmds = NavigationCommandUtils.jumpTitle(pb.getTitleNumber());

			IgsButton btn = new IgsButton();
			btn.setId(buttonId);
			btn.setNumericSelectValue(0xFFFF);
			btn.setAutoAction(false);
			btn.setXPos(pb.getX());
			btn.setYPos(pb.getY());

			// Visual states
			IgsMenuAssembler.bindButtonVisualStates(btn, normalObjId, selectedObjId, activatedObjId);

			btn.setNavigationCommands(navCmds);

			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(buttonId);
			bog.getButtons().add(btn);
			bogs.add(bog);
		}

		// ── Wire D-pad neighbours ───────────────────────────────────────────

		wireNeighbours(bogs, buttons, descriptor);

		// ── Optional Settings button + submenu pages ──────────────────────────

		List<IgsPage> settingsPages = new ArrayList<>();
		if (layoutResult.getSettingsButton() != null) {
			PositionedButton settingsLayout = layoutResult.getSettingsButton();
			int settingsButtonId = buttons.size() + BUTTON_BASE_ID;
			int settingsObjBase = allImages.size();
			allImages.add(settingsLayout.getNormalImage());
			allImages.add(settingsLayout.getSelectedImage());
			allImages.add(settingsLayout.getActivatedImage());

			IgsButton settingsBtn = new IgsButton();
			settingsBtn.setId(settingsButtonId);
			settingsBtn.setNumericSelectValue(0xFFFF);
			settingsBtn.setAutoAction(false);
			settingsBtn.setXPos(settingsLayout.getX());
			settingsBtn.setYPos(settingsLayout.getY());
			IgsMenuAssembler.bindButtonVisualStates(settingsBtn, settingsObjBase, settingsObjBase + 1,
					settingsObjBase + 2);
			settingsBtn.setNavigationCommands(settingsLayout.getNavigationCommands());
			// self-loop by default; linked to the last title button below when present
			settingsBtn.setUpperButtonIdRef(settingsButtonId);
			settingsBtn.setLowerButtonIdRef(settingsButtonId);
			settingsBtn.setLeftButtonIdRef(settingsButtonId);
			settingsBtn.setRightButtonIdRef(settingsButtonId);
			if (!bogs.isEmpty()) {
				IgsButton lastTitleBtn = bogs.get(bogs.size() - 1).getButtons().get(0);
				lastTitleBtn.setLowerButtonIdRef(settingsButtonId);
				settingsBtn.setUpperButtonIdRef(lastTitleBtn.getId());
			}

			IgsBog settingsBog = new IgsBog();
			settingsBog.setDefaultValidButtonIdRef(settingsButtonId);
			settingsBog.getButtons().add(settingsBtn);
			bogs.add(settingsBog);

			int settingsObjectBase = allImages.size();
			for (LayoutResult.SettingsPage page : layoutResult.getSettingsPages()) {
				settingsPages.add(buildSettingsPage(page, settingsObjectBase, allImages));
				settingsObjectBase += page.getButtons().size() * 3;
			}
		}

		// ── Build palette ───────────────────────────────────────────────────

		IgsPalette palette = PaletteBuilder.buildFromImages(0, allImages.toArray(new BufferedImage[0]));

		// ── RLE-encode all images into IgsObjects ───────────────────────────

		List<IgsObject> objects = IgsMenuAssembler.buildObjectsFromImages(allImages, palette);

		// ── Build IGS pages ─────────────────────────────────────────────────

		IgsPage menuPage = buildMenuPage(bogs, hasStartupPage ? 1 : 0, buttons.isEmpty() ? 0xFFFF : BUTTON_BASE_ID);

		// ── Build Interactive Composition ────────────────────────────────────

		IgsInteractiveComposition ic = new IgsInteractiveComposition();
		// Out-Of-Mux per spec (matches SubPath type 3). Required by strict players
		// (e.g. PowerDVD); also gates emission of composition_timeout_pts /
		// selection_timeout_pts in the encoded ICS.
		ic.setStreamModel(IgsInteractiveComposition.STREAM_MODEL_OUT_OF_MUX);
		ic.setUiModel(IgsInteractiveComposition.UI_MODEL_ALWAYS_ON);
		ic.setUserTimeoutDuration(0); // No user timeout
		if (hasStartupPage) {
			ic.getPages().add(buildStartupPage());
		}
		ic.getPages().add(menuPage);
		ic.getPages().addAll(settingsPages);

		// ── Build ICS ───────────────────────────────────────────────────────

		IgsCompositionSegment ics = IgsMenuAssembler.buildCompositionSegment(ic, screenW, screenH);

		// ── Window Definition ────────────────────────────────────────────────

		IgsWindowDefinition wds = IgsMenuAssembler.buildFullScreenWindowDefinition(screenW, screenH);

		// ── Assemble Display Set ────────────────────────────────────────────

		IgsDisplaySet displaySet = IgsMenuAssembler.assembleDisplaySet(ics, palette, wds, objects);

		log.info("Title menu IGS built: {} buttons, {} objects, palette with {} entries", buttons.size(),
				objects.size(), palette.getEntries().size());

		return displaySet;
	}

	// ── D-pad navigation wiring ─────────────────────────────────────────────

	private void wireNeighbours(List<IgsBog> bogs, List<PositionedButton> buttons, TitleMenuDescriptor descriptor) {
		LayoutConfig config = descriptor.getLayout();
		int columns = config.effectiveColumns();
		int count = buttons.size();
		List<TitleEntry> titles = descriptor.getTitles();

		for (int i = 0; i < bogs.size(); i++) {
			IgsButton btn = bogs.get(i).getButtons().get(0);

			// Grid-based auto-wiring
			int col = i % columns;
			int row = i / columns;
			int rows = (int) Math.ceil((double) count / columns);

			// Up: same column, previous row (wrap to last row)
			int upRow = (row - 1 + rows) % rows;
			int upIdx = Math.min(upRow * columns + col, count - 1);

			// Down: same column, next row (wrap to first row)
			int downRow = (row + 1) % rows;
			int downIdx = Math.min(downRow * columns + col, count - 1);

			// Left: previous column (wrap to last column in same row)
			int leftCol = (col - 1 + columns) % columns;
			int leftIdx = Math.min(row * columns + leftCol, count - 1);

			// Right: next column (wrap to first column in same row)
			int rightCol = (col + 1) % columns;
			int rightIdx = Math.min(row * columns + rightCol, count - 1);

			// Apply explicit overrides from descriptor
			if (i < titles.size()) {
				NavigationOverride nav = titles.get(i).getNav();
				if (nav != null) {
					if (nav.getUp() != null)
						upIdx = clamp(nav.getUp(), 0, count - 1);
					if (nav.getDown() != null)
						downIdx = clamp(nav.getDown(), 0, count - 1);
					if (nav.getLeft() != null)
						leftIdx = clamp(nav.getLeft(), 0, count - 1);
					if (nav.getRight() != null)
						rightIdx = clamp(nav.getRight(), 0, count - 1);
				}
			}

			btn.setUpperButtonIdRef(upIdx + BUTTON_BASE_ID); // 1-based
			btn.setLowerButtonIdRef(downIdx + BUTTON_BASE_ID);
			btn.setLeftButtonIdRef(leftIdx + BUTTON_BASE_ID);
			btn.setRightButtonIdRef(rightIdx + BUTTON_BASE_ID);
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	/**
	 * Builds the startup page that auto-switches into the actual title-menu page.
	 *
	 * As is, this page is useless. But it serves as a template for inserting custom commands that need to run before
	 * the menu is shown.
	 *
	 * @return a startup page with a single auto-action button that issues SET_BUTTON_PAGE to page 1, button 1
	 */
	private static IgsPage buildStartupPage() {
		IgsButton btn = new IgsButton();
		btn.setId(BUTTON_BASE_ID);
		btn.setNumericSelectValue(0xFFFF);
		btn.setAutoAction(true);
		btn.setXPos(0);
		btn.setYPos(0);
		btn.setUpperButtonIdRef(BUTTON_BASE_ID);
		btn.setLowerButtonIdRef(BUTTON_BASE_ID);
		btn.setLeftButtonIdRef(BUTTON_BASE_ID);
		btn.setRightButtonIdRef(BUTTON_BASE_ID);
		btn.setNormalStartObjectIdRef(0xFFFF);
		btn.setNormalEndObjectIdRef(0xFFFF);
		btn.setSelectedSoundIdRef(0xFF);
		btn.setSelectedStartObjectIdRef(0xFFFF);
		btn.setSelectedEndObjectIdRef(0xFFFF);
		btn.setActivatedSoundIdRef(0xFF);
		btn.setActivatedStartObjectIdRef(0xFFFF);
		btn.setActivatedEndObjectIdRef(0xFFFF);

		btn.setNavigationCommands(NavigationCommandUtils.setButtonPage(1, BUTTON_BASE_ID));

		IgsBog bog = new IgsBog();
		bog.setDefaultValidButtonIdRef(BUTTON_BASE_ID);
		bog.getButtons().add(btn);

		IgsPage page = new IgsPage();
		page.setId(0);
		page.setVersion(0);
		page.setUoMaskTable(new byte[8]);
		page.setAnimationFrameRateCode(0);
		page.setDefaultSelectedButtonIdRef(BUTTON_BASE_ID);
		page.setDefaultActivatedButtonIdRef(0xFFFF);
		page.setPaletteIdRef(0);
		page.getBogs().add(bog);
		return page;
	}

	private static IgsPage buildMenuPage(List<IgsBog> bogs, int pageId, int defaultSelectedButtonIdRef) {
		IgsPage page = new IgsPage();
		page.setId(pageId);
		page.setVersion(0);
		page.setUoMaskTable(new byte[8]);
		page.setAnimationFrameRateCode(0);
		page.setDefaultSelectedButtonIdRef(defaultSelectedButtonIdRef);
		page.setDefaultActivatedButtonIdRef(0xFFFF);
		page.setPaletteIdRef(0);
		page.setBogs(bogs);
		return page;
	}

	private static IgsPage buildSettingsPage(LayoutResult.SettingsPage page, int objectBase,
			List<BufferedImage> allImages) {
		List<IgsBog> bogs = new ArrayList<>();
		for (int i = 0; i < page.getButtons().size(); i++) {
			PositionedButton layoutButton = page.getButtons().get(i);
			int buttonId = i + BUTTON_BASE_ID;
			int buttonObjectBase = objectBase + i * 3;
			allImages.add(layoutButton.getNormalImage());
			allImages.add(layoutButton.getSelectedImage());
			allImages.add(layoutButton.getActivatedImage());

			IgsButton button = new IgsButton();
			button.setId(buttonId);
			button.setNumericSelectValue(0xFFFF);
			button.setAutoAction(false);
			button.setXPos(layoutButton.getX());
			button.setYPos(layoutButton.getY());
			IgsMenuAssembler.bindButtonVisualStates(button, buttonObjectBase, buttonObjectBase + 1,
					buttonObjectBase + 2);
			button.setNavigationCommands(layoutButton.getNavigationCommands());

			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(buttonId);
			bog.getButtons().add(button);
			bogs.add(bog);
		}
		IgsMenuAssembler.wireVerticalWrapNeighbours(bogs.stream().map(bog -> bog.getButtons().get(0)).toList());
		return buildMenuPage(bogs, page.getPageId(), BUTTON_BASE_ID);
	}

}
