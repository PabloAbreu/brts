package org.brts.lowlevel.popupmenu;

import static org.brts.common.utils.BrtsI18NLabels.MENU_BACK;
import static org.brts.common.utils.BrtsI18NLabels.MENU_EXIT;
import static org.brts.common.utils.BrtsI18NLabels.MENU_TO_AUDIO;
import static org.brts.common.utils.BrtsI18NLabels.MENU_TO_SUBTITLES;
import static org.brts.common.utils.BrtsI18NLabels.getLabel;
import static org.brts.lowlevel.bdmv.NavigationCommandUtils.popupOff;
import static org.brts.lowlevel.bdmv.NavigationCommandUtils.setAudio;
import static org.brts.lowlevel.bdmv.NavigationCommandUtils.setButtonPage;
import static org.brts.lowlevel.bdmv.NavigationCommandUtils.setSubtitle;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.TextRenderer;
import org.brts.common.menu.TextRenderer.ButtonImages;
import org.brts.common.menu.TextStyle;
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

import lombok.extern.slf4j.Slf4j;

/**
 * Builds an {@link IgsDisplaySet} for a popup audio/subtitle selection menu.
 * <p>
 * The menu structure depends on how many selectable track groups exist:
 * <ul>
 * <li><b>Both groups (audio &gt; 1 and subtitles &gt; 1)</b>:
 * <ul>
 * <li>Page 0 (Root): "Audio &#9658;" + "Subtitles &#9658;" + "Exit" navigation buttons</li>
 * <li>Page 1 (Audio): one button per audio track + "&#9668; Back" + "Exit"</li>
 * <li>Page 2 (Subtitles): one button per subtitle track + "&#9668; Back" + "Exit"</li>
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

	private static final int BUTTON_HEIGHT = 40;

	private static final int BUTTON_MAX_WIDTH = 800;

	private static final int BUTTON_SPACING_Y = 8;

	private static final int MARGIN_BOTTOM = 80;

	private record ButtonSpec(ButtonImages images, List<NavigationCommand> commands) {
	}

	private static ButtonSpec render(String text, TextStyle style, List<NavigationCommand> commands)
			throws IOException {
		return new ButtonSpec(TextRenderer.renderTextButton(text, style, BUTTON_MAX_WIDTH), commands);
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
		int screenW = config.getScreenWidth();
		int screenH = config.getScreenHeight();
		List<List<SymbolicButton>> symbolicPages = buildSymbolicPages(config);
		if (symbolicPages == null)
			return null;
		List<List<ButtonSpec>> pages = renderPages(symbolicPages, config.getStyle());
		List<BufferedImage> images = collectImages(pages);
		IgsPalette palette = PaletteBuilder.buildFromImages(0, images.toArray(new BufferedImage[0]));
		List<IgsObject> objects = IgsMenuAssembler.buildObjectsFromImages(images, palette);
		IgsInteractiveComposition composition = buildInteractiveComposition(pages, screenW, screenH);
		IgsCompositionSegment compositionSegment = IgsMenuAssembler.buildCompositionSegment(composition, screenW,
				screenH);
		IgsWindowDefinition windowDefinition = IgsMenuAssembler.buildFullScreenWindowDefinition(screenW, screenH);
		IgsDisplaySet displaySet = IgsMenuAssembler.assembleDisplaySet(compositionSegment, palette, windowDefinition,
				objects);

		log.info("Popup menu IGS built: {} page(s), {} objects", pages.size(), objects.size());

		return displaySet;
	}

	private static List<BufferedImage> collectImages(List<List<ButtonSpec>> pages) {
		List<BufferedImage> images = new ArrayList<>();
		for (List<ButtonSpec> pageSpecs : pages) {
			for (ButtonSpec spec : pageSpecs) {
				images.add(spec.images().normal());
				images.add(spec.images().selected());
				images.add(spec.images().activated());
			}
		}
		return images;
	}

	private IgsInteractiveComposition buildInteractiveComposition(List<List<ButtonSpec>> pages, int screenW,
			int screenH) {
		IgsInteractiveComposition composition = new IgsInteractiveComposition();
		composition.setStreamModel(IgsInteractiveComposition.STREAM_MODEL_OUT_OF_MUX);
		composition.setUiModel(IgsInteractiveComposition.UI_MODEL_POP_UP);
		composition.setUserTimeoutDuration(0);

		int objectBase = 0;
		for (int pageId = 0; pageId < pages.size(); pageId++) {
			List<ButtonSpec> specs = pages.get(pageId);
			composition.getPages().add(buildPage(pageId, specs, objectBase, screenW, screenH));
			objectBase += specs.size() * 3;
		}
		return composition;
	}

	// ── Symbolic page building ──────────────────────────────────────────────

	/**
	 * Builds the page/button structure with labels and navigation commands, without rendering any images.
	 *
	 * @return the symbolic pages, or {@code null} if neither audio nor subtitle tracks warrant a menu
	 */
	List<List<SymbolicButton>> buildSymbolicPages(PopupMenuConfig config) {
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

		List<List<SymbolicButton>> pages = new ArrayList<>();

		if (needsAudio && needsSubs) {
			// BOTH: root page (0) + audio sub-page (1) + subtitle sub-page (2)
			List<SymbolicButton> rootSpecs = new ArrayList<>();
			rootSpecs.add(new SymbolicButton(getLabel(MENU_TO_AUDIO), setButtonPage(1, 1)));
			rootSpecs.add(new SymbolicButton(getLabel(MENU_TO_SUBTITLES), setButtonPage(2, 1)));
			rootSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(rootSpecs);

			List<SymbolicButton> audioSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : audioTracks) {
				audioSpecs.add(new SymbolicButton(entry.getDisplayName(), setAudio(entry.getStreamIndex())));
			}
			audioSpecs.add(new SymbolicButton(getLabel(MENU_BACK), setButtonPage(0, 1)));
			audioSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(audioSpecs);

			List<SymbolicButton> subSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : subtitleTracks) {
				subSpecs.add(new SymbolicButton(entry.getDisplayName(), setSubtitle(entry.getStreamIndex())));
			}
			subSpecs.add(new SymbolicButton(getLabel(MENU_BACK), setButtonPage(0, 1)));
			subSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(subSpecs);

		} else if (needsAudio) {
			// AUDIO_ONLY: direct track list on page 0
			List<SymbolicButton> audioSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : audioTracks) {
				audioSpecs.add(new SymbolicButton(entry.getDisplayName(), setAudio(entry.getStreamIndex())));
			}
			audioSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(audioSpecs);

		} else {
			// SUBS_ONLY: direct track list on page 0
			List<SymbolicButton> subSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : subtitleTracks) {
				subSpecs.add(new SymbolicButton(entry.getDisplayName(), setSubtitle(entry.getStreamIndex())));
			}
			subSpecs.add(new SymbolicButton(getLabel(MENU_EXIT), popupOff()));
			pages.add(subSpecs);
		}

		return pages;
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
				specs.add(render(button.text(), style, button.commands()));
			}
			pages.add(specs);
		}
		return pages;
	}

	// ── Page builder ────────────────────────────────────────────────────────

	private IgsPage buildPage(int pageId, List<ButtonSpec> specs, int objectBase, int screenW, int screenH) {
		int totalButtons = specs.size();
		int totalHeight = totalButtons * BUTTON_HEIGHT + (totalButtons - 1) * BUTTON_SPACING_Y;
		int startY = screenH - MARGIN_BOTTOM - totalHeight;
		int groupX = Math.max(40, Math.min((screenW - BUTTON_MAX_WIDTH) / 2, screenW - BUTTON_MAX_WIDTH - 40));

		List<IgsBog> bogs = new ArrayList<>();
		for (int i = 0; i < totalButtons; i++) {
			int buttonId = i + 1; // 1-based
			int normalObjId = objectBase + i * 3;
			int selectedObjId = objectBase + i * 3 + 1;
			int activatedObjId = objectBase + i * 3 + 2;

			int y = startY + i * (BUTTON_HEIGHT + BUTTON_SPACING_Y);

			IgsButton btn = new IgsButton();
			btn.setId(buttonId);
			btn.setNumericSelectValue(0xFFFF);
			btn.setAutoAction(false);
			btn.setXPos(groupX);
			btn.setYPos(y);

			IgsMenuAssembler.bindButtonVisualStates(btn, normalObjId, selectedObjId, activatedObjId);

			btn.setNavigationCommands(specs.get(i).commands());

			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(buttonId);
			bog.getButtons().add(btn);
			bogs.add(bog);
		}

		IgsMenuAssembler.wireVerticalWrapNeighbours(bogs.stream().map(b -> b.getButtons().get(0)).toList());

		IgsPage page = new IgsPage();
		page.setId(pageId);
		page.setVersion(0);
		page.setUoMaskTable(new byte[8]);
		page.setAnimationFrameRateCode(0);
		page.setDefaultSelectedButtonIdRef(1);
		page.setDefaultActivatedButtonIdRef(0xFFFF);
		page.setPaletteIdRef(0);
		page.setBogs(bogs);

		return page;
	}

	// ── Style ───────────────────────────────────────────────────────────────

	private TextStyle resolveStyle(TextStyle input) {
		if (input != null) {
			return input.withDefaults();
		}
		return new TextStyle().withDefaults();
	}

}
