package org.brts.lowlevel.popupmenu;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/popupmenu/PopupMenuIgsBuilder.java' is part of BRTS.
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
 * Builds an {@link IgsDisplaySet} for a popup audio/subtitle selection menu.
 * <p>
 * The menu structure depends on how many selectable track groups exist:
 * <ul>
 * <li><b>Both groups (audio &gt; 1 and subtitles &gt; 1)</b>:
 * <ul>
 * <li>Page 0 (Root): "Audio &#9658;" + "Subtitles &#9658;" + "Exit" navigation buttons</li>
 * <li>Page 1 (Audio): persistent root controls + one button per audio track</li>
 * <li>Page 2 (Subtitles): persistent root controls + one button per subtitle track</li>
 * </ul>
 * </li>
 * <li><b>Audio only (audio &gt; 1, subtitles &le; 1)</b>: Page 0 lists audio tracks + "Exit" directly.</li>
 * <li><b>Subtitles only (subtitles &gt; 1, audio &le; 1)</b>: Page 0 lists subtitle tracks + "Exit" directly.</li>
 * <li><b>Neither</b>: {@link #build(PopupMenuConfig)} returns {@code null}.</li>
 * </ul>
 * <p>
 * The interactive composition uses {@code streamModel=IgsInteractiveComposition.STREAM_MODEL_OUT_OF_MUX} (Out-Of-Mux)
 * and {@code uiModel=IgsInteractiveComposition.UI_MODEL_POP_UP} (Pop-Up).
 */
@Slf4j
public class PopupMenuIgsBuilder {

	private static final int BUTTON_MAX_WIDTH = 800;
	private static final int ROOT_BUTTON_COUNT = 3;

	private record ButtonSpec(ButtonImages images, List<NavigationCommand> commands, boolean autoAction) {
	}

	private record SymbolicPage(PageRole role, boolean includesRoot, List<SymbolicButton> buttons) {
	}

	private static ButtonSpec render(String text, TextStyle style, List<NavigationCommand> commands, boolean autoAction)
			throws IOException {
		return new ButtonSpec(TextRenderer.renderTextButton(text, style, BUTTON_MAX_WIDTH), commands, autoAction);
	}

	/**
	 * Builds the popup menu IGS display set.
	 *
	 * @param config the popup menu configuration
	 * @return a fully-populated {@link IgsDisplaySet}, or {@code null} if neither audio nor subtitle tracks warrant a
	 *         menu (both groups have &le; 1 entry)
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
	 * @return the symbolic pages, or {@code null} if neither audio nor subtitle tracks warrant a menu
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

		boolean needsAudio = audioTracks.size() > 1;
		boolean needsSubs = subtitleTracks.size() > 1;

		if (!needsAudio && !needsSubs) {
			log.info("Popup menu skipped: neither audio ({}) nor subtitle ({}) tracks warrant a menu",
					audioTracks.size(), subtitleTracks.size());
			return null;
		}

		List<SymbolicPage> pages = new ArrayList<>();

		if (needsAudio && needsSubs) {
			// BOTH: root page (0) + persistent-root audio/subtitle pages (1/2)
			List<SymbolicButton> rootSpecs = new ArrayList<>();
			rootSpecs.add(new SymbolicButton(getLabel(MENU_TO_AUDIO), setButtonPage(1, ROOT_BUTTON_COUNT + 1)));
			rootSpecs.add(new SymbolicButton(getLabel(MENU_TO_SUBTITLES), setButtonPage(2, ROOT_BUTTON_COUNT + 1)));
			rootSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(new SymbolicPage(PageRole.ROOT, true, rootSpecs));

			List<SymbolicButton> audioSpecs = clonedRootSpecs();
			for (PopupMenuConfig.TrackEntry entry : audioTracks) {
				audioSpecs.add(new SymbolicButton(entry.getDisplayName(), setAudio(entry.getStreamIndex())));
			}
			pages.add(new SymbolicPage(PageRole.AUDIO, true, audioSpecs));

			List<SymbolicButton> subSpecs = clonedRootSpecs();
			for (PopupMenuConfig.TrackEntry entry : subtitleTracks) {
				subSpecs.add(new SymbolicButton(entry.getDisplayName(), setSubtitle(entry.getStreamIndex())));
			}
			pages.add(new SymbolicPage(PageRole.SUBTITLES, true, subSpecs));

		} else if (needsAudio) {
			// AUDIO_ONLY: direct track list on page 0
			List<SymbolicButton> audioSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : audioTracks) {
				audioSpecs.add(new SymbolicButton(entry.getDisplayName(), setAudio(entry.getStreamIndex())));
			}
			audioSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(new SymbolicPage(PageRole.AUDIO, false, audioSpecs));

		} else {
			// SUBS_ONLY: direct track list on page 0
			List<SymbolicButton> subSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : subtitleTracks) {
				subSpecs.add(new SymbolicButton(entry.getDisplayName(), setSubtitle(entry.getStreamIndex())));
			}
			subSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(new SymbolicPage(PageRole.SUBTITLES, false, subSpecs));
		}

		return pages;
	}

	private static List<SymbolicButton> clonedRootSpecs() {
		List<SymbolicButton> rootSpecs = new ArrayList<>();
		rootSpecs.add(new SymbolicButton(getLabel(MENU_TO_AUDIO), setButtonPage(0, 1), true));
		rootSpecs.add(new SymbolicButton(getLabel(MENU_TO_SUBTITLES), setButtonPage(0, 2), true));
		rootSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), setButtonPage(0, 3), true));
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
				specs.add(render(button.text(), style, button.commands(), button.autoAction()));
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
