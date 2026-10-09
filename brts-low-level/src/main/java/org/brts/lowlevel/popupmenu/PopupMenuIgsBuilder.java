/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/PopupMenuIgsBuilder.java' is part of BRTS.
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

package org.brts.lowlevel.popupmenu;

import static org.brts.common.utils.BrtsI18NLabels.*;
import static org.brts.lowlevel.bdmv.NavigationCommandUtils.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.TextRenderer;
import org.brts.common.menu.TextRenderer.ButtonImages;
import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.igs.IgsMenuAssembler;
import org.brts.lowlevel.igs.PaletteBuilder;
import org.brts.lowlevel.igs.model.IgsCompositionSegment;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.igs.model.IgsInteractiveComposition;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.brts.lowlevel.popupmenu.layout.HorizontalBottomPopupMenuLayout;
import org.brts.lowlevel.popupmenu.layout.PopupMenuLayout;
import org.brts.lowlevel.popupmenu.layout.VerticalPopupMenuLayout;
import org.brts.lowlevel.popupmenu.PopupMenuBackgroundRenderer.PageRole;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds an {@link IgsDisplaySet} for a popup audio, subtitle, and title selection menu.
 * <p>
 * The menu structure depends on how many selectable track groups exist:
 * <ul>
 * <li><b>Multiple groups</b>: a root page followed by one page per eligible audio, subtitle, or title group. Each
 * submenu keeps the root controls visible.</li>
 * <li><b>One group</b>: its entries and an Exit button are shown directly on page 0.</li>
 * <li><b>Current title</b>: shown in muted colors without a navigation command.</li>
 * <li><b>No groups</b>: {@link #build(PopupMenuConfig)} returns {@code null}.</li>
 * </ul>
 * <p>
 * The interactive composition uses {@code streamModel=IgsInteractiveComposition.STREAM_MODEL_OUT_OF_MUX} (Out-Of-Mux)
 * and {@code uiModel=IgsInteractiveComposition.UI_MODEL_POP_UP} (Pop-Up).
 */
@Slf4j
public class PopupMenuIgsBuilder {

	private static final int BUTTON_MAX_WIDTH = 800;
	private static final String MUTED_TITLE_COLOR = "#FF808080";

	private record ButtonSpec(ButtonImages images, List<NavigationCommand> commands, boolean autoAction) {
	}

	private record SymbolicPage(PageRole role, boolean includesRoot, List<SymbolicButton> buttons) {
	}

	private static ButtonSpec render(String text, TextStyle style, List<NavigationCommand> commands, boolean autoAction,
			boolean muted) throws IOException {
		TextStyle effectiveStyle = muted ? mutedStyle(style) : style;
		return new ButtonSpec(TextRenderer.renderTextButton(text, effectiveStyle, BUTTON_MAX_WIDTH), commands,
				autoAction);
	}

	private static TextStyle mutedStyle(TextStyle style) {
		TextStyle muted = new TextStyle();
		muted.setNormalColor(MUTED_TITLE_COLOR);
		muted.setSelectedColor(MUTED_TITLE_COLOR);
		muted.setActivatedColor(MUTED_TITLE_COLOR);
		return muted.mergeOver(style);
	}

	/**
	 * Builds the popup menu IGS display set.
	 *
	 * @param config the popup menu configuration
	 * @return a fully-populated {@link IgsDisplaySet}, or {@code null} if no track or title group warrants a menu
	 * @throws IOException on rendering errors
	 */
	public IgsDisplaySet build(PopupMenuConfig config) throws IOException {
		int screenW = config.effectiveScreenWidth();
		int screenH = config.effectiveScreenHeight();
		List<SymbolicPage> symbolicPageSpecs = buildSymbolicPageSpecs(config);
		if (symbolicPageSpecs == null)
			return null;
		List<List<SymbolicButton>> symbolicPages = symbolicPageSpecs.stream().map(SymbolicPage::buttons).toList();
		List<List<ButtonSpec>> pages = renderPages(symbolicPages, config.getStyle());
		List<PopupMenuLayout.Page> positionedPages = positionPages(pages, config.getLayout(), screenW, screenH);
		PopupMenuBackgroundRenderer backgroundRenderer = new PopupMenuBackgroundRenderer();
		List<List<IgsMenuAssembler.PositionedDecoration>> decorations = new ArrayList<>();
		for (int pageId = 0; pageId < positionedPages.size(); pageId++) {
			SymbolicPage pageSpec = symbolicPageSpecs.get(pageId);
			decorations.add(backgroundRenderer.render(config.getBackgrounds(), pageSpec.role(), pageSpec.includesRoot(),
					positionedPages.get(pageId).buttons(), screenW, screenH));
		}
		List<BufferedImage> images = collectImages(pages, decorations);
		IgsPalette palette = PaletteBuilder.buildFromImages(0, images.toArray(new BufferedImage[0]));
		List<IgsObject> objects = IgsMenuAssembler.buildObjectsFromImages(images, palette);
		IgsInteractiveComposition composition = buildInteractiveComposition(positionedPages, decorations);
		IgsCompositionSegment compositionSegment = IgsMenuAssembler.buildCompositionSegment(composition, screenW,
				screenH);
		IgsWindowDefinition windowDefinition = IgsMenuAssembler.buildFullScreenWindowDefinition(screenW, screenH);
		IgsDisplaySet displaySet = IgsMenuAssembler.assembleDisplaySet(compositionSegment, palette, windowDefinition,
				objects);

		log.info("Popup menu IGS built: {} page(s), {} objects", pages.size(), objects.size());

		return displaySet;
	}

	private static List<BufferedImage> collectImages(List<List<ButtonSpec>> pages,
			List<List<IgsMenuAssembler.PositionedDecoration>> decorations) {
		List<BufferedImage> images = new ArrayList<>();
		for (int pageId = 0; pageId < pages.size(); pageId++) {
			decorations.get(pageId).forEach(decoration -> images.add(decoration.image()));
			List<ButtonSpec> pageSpecs = pages.get(pageId);
			for (ButtonSpec spec : pageSpecs) {
				images.add(spec.images().normal());
				images.add(spec.images().selected());
				images.add(spec.images().activated());
			}
		}
		return images;
	}

	private IgsInteractiveComposition buildInteractiveComposition(List<PopupMenuLayout.Page> positionedPages,
			List<List<IgsMenuAssembler.PositionedDecoration>> decorations) {
		IgsInteractiveComposition composition = new IgsInteractiveComposition();
		composition.setStreamModel(IgsInteractiveComposition.STREAM_MODEL_OUT_OF_MUX);
		composition.setUiModel(IgsInteractiveComposition.UI_MODEL_POP_UP);
		composition.setUserTimeoutDuration(0);

		int objectBase = 0;
		for (int pageId = 0; pageId < positionedPages.size(); pageId++) {
			List<IgsMenuAssembler.PositionedButton> buttons = positionedPages.get(pageId).buttons();
			List<IgsMenuAssembler.PositionedDecoration> pageDecorations = decorations.get(pageId);
			composition.getPages().add(IgsMenuAssembler.buildPositionedPage(pageId, buttons, pageDecorations,
					objectBase, positionedPages.get(pageId).defaultSelectedButtonIdRef()));
			objectBase += pageDecorations.size() + buttons.size() * 3;
		}
		return composition;
	}

	private List<PopupMenuLayout.Page> positionPages(List<List<ButtonSpec>> pages, PopupMenuConfig.Layout layout,
			int screenW, int screenH) {
		List<List<IgsMenuAssembler.LabeledButton>> labeledPages = pages.stream()
				.map(specs -> specs.stream()
						.map(s -> new IgsMenuAssembler.LabeledButton(s.images(), s.commands(), s.autoAction()))
						.toList())
				.toList();
		PopupMenuLayout layoutStrategy = layout == PopupMenuConfig.Layout.HORIZONTAL_BOTTOM
				? new HorizontalBottomPopupMenuLayout()
				: new VerticalPopupMenuLayout();
		return layoutStrategy.layout(labeledPages, screenW, screenH);
	}

	// ── Symbolic page building ──────────────────────────────────────────────

	/**
	 * Builds the page/button structure with labels and navigation commands, without rendering any images.
	 *
	 * @return the symbolic pages, or {@code null} if no selection group warrants a menu
	 */
	List<List<SymbolicButton>> buildSymbolicPages(PopupMenuConfig config) {
		List<SymbolicPage> pages = buildSymbolicPageSpecs(config);
		return pages == null ? null : pages.stream().map(SymbolicPage::buttons).toList();
	}

	private List<SymbolicPage> buildSymbolicPageSpecs(PopupMenuConfig config) {
		List<PopupMenuConfig.TrackEntry> audioTracks = config.getAudioTracks() != null ? config.getAudioTracks()
				: List.of();
		List<PopupMenuConfig.TrackEntry> subtitleTracks = config.getSubtitleTracks() != null
				? config.getSubtitleTracks()
				: List.of();
		List<PopupMenuConfig.TitleEntry> titles = config.getTitles() != null ? config.getTitles() : List.of();
		validateTitles(titles, config.getCurrentTitleNumber());

		List<SymbolicPage> pages = new ArrayList<>();
		if (audioTracks.size() > 1) {
			log.debug("Adding audio page");
			pages.add(new SymbolicPage(PageRole.AUDIO, false, audioButtons(audioTracks)));
		}
		if (subtitleTracks.size() > 1) {
			log.debug("Adding subtitle page");
			pages.add(new SymbolicPage(PageRole.SUBTITLES, false, subtitleButtons(subtitleTracks)));
		}
		if (titles.size() > 1) {
			log.debug("Adding titles page");
			pages.add(new SymbolicPage(PageRole.TITLES, false, titleButtons(titles, config.getCurrentTitleNumber())));
		}

		if (pages.isEmpty()) {
			log.info("Popup menu skipped: audio ({}), subtitle ({}), and title ({}) entries do not warrant a menu",
					audioTracks.size(), subtitleTracks.size(), titles.size());
			return null;
		}
		if (pages.size() == 1) {
			List<SymbolicButton> directButtons = new ArrayList<>(pages.get(0).buttons());
			directButtons.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			SymbolicPage onlyPage = pages.get(0);
			return List.of(new SymbolicPage(onlyPage.role(), false, directButtons));
		}

		int rootControlCount = pages.size() + 1;
		List<SymbolicButton> rootButtons = new ArrayList<>();
		for (int groupIndex = 0; groupIndex < pages.size(); groupIndex++) {
			SymbolicPage page = pages.get(groupIndex);
			rootButtons.add(
					new SymbolicButton(groupLabel(page.role()), setButtonPage(groupIndex + 1, rootControlCount + 1)));
		}
		rootButtons.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
		List<SymbolicPage> result = new ArrayList<>();
		result.add(new SymbolicPage(PageRole.ROOT, true, rootButtons));
		for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
			SymbolicPage page = pages.get(pageIndex);
			List<SymbolicButton> buttons = clonedRootSpecs(pages);
			buttons.addAll(page.buttons());
			result.add(new SymbolicPage(page.role(), true, buttons));
		}
		return result;
	}

	private static void validateTitles(List<PopupMenuConfig.TitleEntry> titles, Integer currentTitleNumber) {
		if (titles.isEmpty()) {
			return;
		}
		if (currentTitleNumber == null) {
			throw new IllegalArgumentException("currentTitleNumber is required when popup titles are configured");
		}
		boolean currentTitleFound = false;
		for (PopupMenuConfig.TitleEntry title : titles) {
			if (title == null || title.getTitleNumber() < 1) {
				throw new IllegalArgumentException("Popup title entries must have a positive titleNumber");
			}
			currentTitleFound |= title.getTitleNumber() == currentTitleNumber;
		}
		if (!currentTitleFound) {
			throw new IllegalArgumentException(
					"currentTitleNumber " + currentTitleNumber + " is not present in the popup title list");
		}
	}

	private static List<SymbolicButton> audioButtons(List<PopupMenuConfig.TrackEntry> tracks) {
		return tracks.stream()
				.map(entry -> new SymbolicButton(entry.getDisplayName(), setAudio(entry.getStreamIndex())))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	private static List<SymbolicButton> subtitleButtons(List<PopupMenuConfig.TrackEntry> tracks) {
		return tracks.stream()
				.map(entry -> new SymbolicButton(entry.getDisplayName(), setSubtitle(entry.getStreamIndex())))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	private static List<SymbolicButton> titleButtons(List<PopupMenuConfig.TitleEntry> titles, int currentTitleNumber) {
		return titles.stream()
				.map(entry -> entry.getTitleNumber() == currentTitleNumber
						? new SymbolicButton(entry.getDisplayName(), List.of(), false, true)
						: new SymbolicButton(entry.getDisplayName(), jumpTitle(entry.getTitleNumber())))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
	}

	private static String groupLabel(PageRole role) {
		return switch (role) {
		case AUDIO -> getLabel(MENU_TO_AUDIO);
		case SUBTITLES -> getLabel(MENU_TO_SUBTITLES);
		case TITLES -> getLabel(MENU_TO_TITLES);
		case ROOT -> throw new IllegalArgumentException("Root page is not a selectable popup group");
		};
	}

	private static List<SymbolicButton> clonedRootSpecs(List<SymbolicPage> pages) {
		List<SymbolicButton> rootSpecs = new ArrayList<>();
		for (int i = 0; i < pages.size(); i++) {
			rootSpecs.add(new SymbolicButton(groupLabel(pages.get(i).role()), setButtonPage(0, i + 1), true));
		}
		rootSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), setButtonPage(0, pages.size() + 1), true));
		return rootSpecs;
	}

	/**
	 * Renders symbolic pages into button images using the configured style.
	 */
	List<List<ButtonSpec>> renderPages(List<List<SymbolicButton>> symbolicPages, TextStyle inputStyle)
			throws IOException {
		TextStyle style = resolveStyle(inputStyle);
		List<List<ButtonSpec>> pages = new ArrayList<>();
		for (List<SymbolicButton> symbolicPage : symbolicPages) {
			List<ButtonSpec> specs = new ArrayList<>();
			for (SymbolicButton button : symbolicPage) {
				specs.add(render(button.text(), style, button.commands(), button.autoAction(), button.muted()));
			}
			pages.add(specs);
		}
		return pages;
	}

	// ── Style ───────────────────────────────────────────────────────────────

	private TextStyle resolveStyle(TextStyle input) {
		if (input != null) {
			return input.withDefaults();
		}
		return new TextStyle().withDefaults();
	}

}
