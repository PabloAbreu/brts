package org.brts.middle.menu;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/SetupMenuIgsBuilder.java' is part of BRTS.
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.brts.common.menu.TextStyle;
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
import org.brts.middle.menu.descriptor.AudioMenuItem;
import org.brts.middle.menu.descriptor.MenuItem;
import org.brts.middle.menu.descriptor.MiscMenuItem;
import org.brts.middle.menu.descriptor.NavigationRefs;
import org.brts.middle.menu.descriptor.SetupMenuDescriptor;
import org.brts.middle.menu.descriptor.SubtitleMenuItem;
import org.brts.middle.menu.render.ButtonImageRenderer;
import org.brts.middle.menu.render.ButtonImageRenderer.ButtonImages;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds a complete {@link IgsDisplaySet} from a {@link SetupMenuDescriptor}.
 * <p>
 * The builder:
 * <ol>
 * <li>Renders button images (three states) using {@link ButtonImageRenderer}</li>
 * <li>Builds a shared palette from all rendered images</li>
 * <li>RLE-encodes all images into {@link IgsObject}s</li>
 * <li>Lays out buttons in a vertical list per category on a single page</li>
 * <li>Wires D-pad neighbour references for navigation</li>
 * <li>Compiles navigation commands for each button</li>
 * </ol>
 */
@Slf4j
public class SetupMenuIgsBuilder {

	// Layout constants
	private static final int CATEGORY_START_Y = 100;

	private static final int BUTTON_SPACING_Y = 60;

	private static final int COLUMN_MARGIN_X = 80;

	private static final int CATEGORY_LABEL_HEIGHT = 40;

	// Object id counter
	private int nextObjectId = 0;

	// Button id counter
	private int nextButtonId = 1;

	// Collected data during build
	private final List<IgsObject> objects = new ArrayList<>();

	private final List<BufferedImage> allImages = new ArrayList<>();

	private final List<ButtonRecord> buttonRecords = new ArrayList<>();

	/**
	 * Internal record linking a descriptor button to its rendered images and IGS ids.
	 */
	private record ButtonRecord(int buttonId, int normalObjectId, int selectedObjectId, int activatedObjectId, int xPos,
			int yPos, int width, int height, List<NavigationCommand> navigationCommands, String description,
			String itemId, NavigationRefs nav) {
	}

	/**
	 * Builds the complete display set from the descriptor.
	 *
	 * @param descriptor the setup menu descriptor
	 * @return a fully-populated {@link IgsDisplaySet} ready for encoding
	 */
	public IgsDisplaySet build(SetupMenuDescriptor descriptor) {
		int screenW = descriptor.getScreenWidth();
		int screenH = descriptor.getScreenHeight();
		TextStyle globalStyle = resolveGlobalStyle(descriptor.getGlobalStyle());

		// ── Render all buttons ──────────────────────────────────────────────

		int currentY = CATEGORY_START_Y;

		// Audio items
		if (!descriptor.getAudioItems().isEmpty()) {
			currentY = renderAudioCategory(descriptor.getAudioItems(), globalStyle, COLUMN_MARGIN_X, currentY, screenW);
			currentY += CATEGORY_LABEL_HEIGHT;
		}

		// Subtitle items
		if (!descriptor.getSubtitleItems().isEmpty()) {
			currentY = renderSubtitleCategory(descriptor.getSubtitleItems(), globalStyle, COLUMN_MARGIN_X, currentY,
					screenW);
			currentY += CATEGORY_LABEL_HEIGHT;
		}

		// Misc items
		if (!descriptor.getMiscItems().isEmpty()) {
			renderMiscCategory(descriptor.getMiscItems(), globalStyle, COLUMN_MARGIN_X, currentY, screenW);
		}

		// ── Build palette from all rendered images ──────────────────────────

		IgsPalette palette = PaletteBuilder.buildFromImages(0, allImages.toArray(new BufferedImage[0]));

		// ── RLE-encode all images into IgsObjects ───────────────────────────

		objects.addAll(IgsMenuAssembler.buildObjectsFromImages(allImages, palette));

		// ── Wire button neighbour references ────────────────────────────────

		wireNeighbours();

		// ── Build IGS page / BOGs ───────────────────────────────────────────

		List<IgsBog> bogs = new ArrayList<>();
		for (ButtonRecord br : buttonRecords) {
			IgsButton btn = new IgsButton();
			btn.setId(br.buttonId);
			btn.setNumericSelectValue(0xFFFF);
			btn.setAutoAction(false);
			btn.setXPos(br.xPos);
			btn.setYPos(br.yPos);

			// Neighbours (already wired in wireNeighbours)
			btn.setUpperButtonIdRef(br.buttonId); // will be overwritten below
			btn.setLowerButtonIdRef(br.buttonId);
			btn.setLeftButtonIdRef(br.buttonId);
			btn.setRightButtonIdRef(br.buttonId);

			// Visual states
			IgsMenuAssembler.bindButtonVisualStates(btn, br.normalObjectId, br.selectedObjectId, br.activatedObjectId);

			btn.setNavigationCommands(br.navigationCommands);

			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(br.buttonId);
			bog.getButtons().add(btn);
			bogs.add(bog);
		}

		// Wire neighbour refs on the actual IgsButton objects
		wireNeighboursOnButtons(bogs);

		IgsPage page = new IgsPage();
		page.setId(0);
		page.setVersion(0);
		page.setUoMaskTable(new byte[8]);
		page.setAnimationFrameRateCode(0);
		page.setDefaultSelectedButtonIdRef(buttonRecords.isEmpty() ? 0xFFFF : buttonRecords.get(0).buttonId);
		page.setDefaultActivatedButtonIdRef(0xFFFF);
		page.setPaletteIdRef(0);
		page.setBogs(bogs);

		// ── Build Interactive Composition ────────────────────────────────────

		IgsInteractiveComposition ic = new IgsInteractiveComposition();
		ic.setStreamModel(IgsInteractiveComposition.STREAM_MODEL_IN_MUX);
		ic.setUiModel(IgsInteractiveComposition.UI_MODEL_ALWAYS_ON); // Always-On
		ic.setUserTimeoutDuration(0);
		ic.getPages().add(page);

		// ── Build ICS ───────────────────────────────────────────────────────

		IgsCompositionSegment ics = IgsMenuAssembler.buildCompositionSegment(ic, screenW, screenH);

		// ── Build Window Definition ─────────────────────────────────────────

		IgsWindowDefinition wds = IgsMenuAssembler.buildFullScreenWindowDefinition(screenW, screenH);

		// ── Assemble Display Set ────────────────────────────────────────────

		IgsDisplaySet displaySet = IgsMenuAssembler.assembleDisplaySet(ics, palette, wds, objects);

		log.info("IGS built: {} buttons, {} objects, palette with {} entries", buttonRecords.size(), objects.size(),
				palette.getEntries().size());

		return displaySet;
	}

	// ── Category rendering ──────────────────────────────────────────────────

	private int renderAudioCategory(List<AudioMenuItem> items, TextStyle globalStyle, int startX, int startY,
			int screenW) {
		int y = startY;
		for (AudioMenuItem item : items) {
			TextStyle style = resolveItemStyle(item.getStyle(), globalStyle);
			ButtonImages images = ButtonImageRenderer.renderTextButton(item.getDescription(), style);
			y = registerButton(item, images, NavigationCommandUtils.setAudio(item.getStreamNumber()), startX, screenW,
					y);
		}
		return y;
	}

	private int renderSubtitleCategory(List<SubtitleMenuItem> items, TextStyle globalStyle, int startX, int startY,
			int screenW) {
		int y = startY;
		for (SubtitleMenuItem item : items) {
			TextStyle style = resolveItemStyle(item.getStyle(), globalStyle);
			ButtonImages images = ButtonImageRenderer.renderTextButton(item.getDescription(), style);
			y = registerButton(item, images, NavigationCommandUtils.setSubtitle(item.getStreamNumber()), startX,
					screenW, y);
		}
		return y;
	}

	private int renderMiscCategory(List<MiscMenuItem> items, TextStyle globalStyle, int startX, int startY,
			int screenW) {
		int y = startY;
		for (MiscMenuItem item : items) {
			TextStyle style = resolveItemStyle(item.getStyle(), globalStyle);
			Path iconPath = item.getIcon() != null ? Path.of(item.getIcon()) : null;
			ButtonImages images = ButtonImageRenderer.renderButton(item.getDescription(), iconPath, style);
			List<NavigationCommand> navCmds = switch (item.getType()) {
			case LAUNCH -> NavigationCommandUtils.playPlaylist(Integer.parseInt(item.getTarget()));
			case GO_BACK ->
				NavigationCommandUtils.jumpTitle(item.getTarget() != null ? Integer.parseInt(item.getTarget()) : 0);
			case POPUP_OFF -> NavigationCommandUtils.popupOff();
			case RESUME -> NavigationCommandUtils.resume();
			};
			y = registerButton(item, images, navCmds, startX, screenW, y);
		}
		return y;
	}

	// ── Layout helpers ──────────────────────────────────────────────────────

	private int registerButton(MenuItem item, ButtonImages images, List<NavigationCommand> navCmds, int startX,
			int screenW, int y) {
		int x = centreX(images.width(), screenW, startX);
		int normalObjId = allocObjectSlot(images.normal());
		int selectedObjId = allocObjectSlot(images.selected());
		int activatedObjId = allocObjectSlot(images.activated());
		int btnId = nextButtonId++;
		buttonRecords.add(new ButtonRecord(btnId, normalObjId, selectedObjId, activatedObjId, x, y, images.width(),
				images.height(), navCmds, item.getDescription(), item.getId(), item.getNav()));
		return y + images.height() + BUTTON_SPACING_Y;
	}

	private int centreX(int buttonWidth, int screenWidth, int margin) {
		return Math.max(margin, (screenWidth - buttonWidth) / 2);
	}

	private int allocObjectSlot(BufferedImage image) {
		int id = nextObjectId++;
		allImages.add(image);
		return id;
	}

	// ── Navigation wiring ───────────────────────────────────────────────────

	private void wireNeighbours() {
		// Simple vertical list: up/down wrap, left/right = self
		// (navigation between categories could be enhanced later)
	}

	private void wireNeighboursOnButtons(List<IgsBog> bogs) {
		List<IgsButton> buttons = new ArrayList<>();
		for (IgsBog bog : bogs) {
			buttons.addAll(bog.getButtons());
		}

		// Build id → buttonId map for explicit nav references
		Map<String, Integer> idToButtonId = new LinkedHashMap<>();
		for (ButtonRecord br : buttonRecords) {
			if (br.itemId() != null) {
				idToButtonId.put(br.itemId(), br.buttonId());
			}
		}

		// Map buttonId → ButtonRecord for nav lookup
		Map<Integer, ButtonRecord> btnIdToRecord = new LinkedHashMap<>();
		for (ButtonRecord br : buttonRecords) {
			btnIdToRecord.put(br.buttonId(), br);
		}

		// Auto-wired defaults: vertical wrap, no left/right movement
		IgsMenuAssembler.wireVerticalWrapNeighbours(buttons);

		for (IgsButton btn : buttons) {
			int autoUp = btn.getUpperButtonIdRef();
			int autoDown = btn.getLowerButtonIdRef();
			int autoLeft = btn.getLeftButtonIdRef();
			int autoRight = btn.getRightButtonIdRef();

			// Apply explicit overrides from NavigationRefs where present
			ButtonRecord br = btnIdToRecord.get(btn.getId());
			NavigationRefs nav = br != null ? br.nav() : null;

			btn.setUpperButtonIdRef(resolveNavRef(nav != null ? nav.getUp() : null, autoUp, idToButtonId, "up", br));
			btn.setLowerButtonIdRef(
					resolveNavRef(nav != null ? nav.getDown() : null, autoDown, idToButtonId, "down", br));
			btn.setLeftButtonIdRef(
					resolveNavRef(nav != null ? nav.getLeft() : null, autoLeft, idToButtonId, "left", br));
			btn.setRightButtonIdRef(
					resolveNavRef(nav != null ? nav.getRight() : null, autoRight, idToButtonId, "right", br));
		}
	}

	/**
	 * Resolves a single directional nav reference. Returns the explicit target button id when found, the auto-wired
	 * fallback otherwise. Logs a warning for references that cannot be resolved.
	 */
	private int resolveNavRef(String ref, int autoValue, Map<String, Integer> idToButtonId, String direction,
			ButtonRecord source) {
		if (ref == null)
			return autoValue;
		Integer resolved = idToButtonId.get(ref);
		if (resolved != null)
			return resolved;
		log.warn("Unresolved nav.{} reference '{}' on button '{}' — falling back to auto-wired neighbour", direction,
				ref, source != null ? source.description() : "?");
		return autoValue;
	}

	// ── Style resolution ────────────────────────────────────────────────────

	private TextStyle resolveGlobalStyle(TextStyle descriptorStyle) {
		TextStyle base = new TextStyle();
		if (descriptorStyle != null) {
			base = descriptorStyle.mergeOver(base);
		}
		return base.withDefaults();
	}

	private TextStyle resolveItemStyle(TextStyle itemStyle, TextStyle resolvedGlobal) {
		if (itemStyle == null)
			return resolvedGlobal;
		return itemStyle.mergeOver(resolvedGlobal).withDefaults();
	}

}
