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
import org.brts.lowlevel.igs.PaletteBuilder;
import org.brts.lowlevel.igs.RleEncoder;
import org.brts.lowlevel.igs.model.CompositionDescriptor;
import org.brts.lowlevel.igs.model.IgsBog;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsCompositionSegment;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.igs.model.IgsInteractiveComposition;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPage;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindow;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;
import org.brts.lowlevel.igs.model.SequenceDescriptor;
import org.brts.lowlevel.igs.model.VideoDescriptor;
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

	private static final int BUTTON_MAX_WIDTH = 400;

	private static final int BUTTON_SPACING_Y = 8;

	private static final int MARGIN_BOTTOM = 80;

	private record ButtonSpec(ButtonImages images, List<NavigationCommand> commands) {
	}

	private TextStyle style;

	private ButtonSpec render(String text, List<NavigationCommand> commands) throws IOException {
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

		style = resolveStyle(config.getStyle());

		// ── Build per-page button specs with nav commands ────────────────────

		List<List<ButtonSpec>> pages = new ArrayList<>();

		if (needsAudio && needsSubs) {
			// BOTH: root page (0) + audio sub-page (1) + subtitle sub-page (2)
			List<ButtonSpec> rootSpecs = new ArrayList<>();
			setButtonPage(1, 1);
			rootSpecs.add(render(getLabel(MENU_TO_AUDIO), setButtonPage(1, 1)));
			rootSpecs.add(render(getLabel(MENU_TO_SUBTITLES), setButtonPage(2, 1)));
			rootSpecs.add(render(getLabel(MENU_EXIT), popupOff()));
			pages.add(rootSpecs);

			List<ButtonSpec> audioSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : audioTracks) {
				audioSpecs.add(render(entry.getDisplayName(), setAudio(entry.getStreamIndex())));
			}
			audioSpecs.add(render(getLabel(MENU_BACK), setButtonPage(0, 1)));
			audioSpecs.add(render(getLabel(MENU_EXIT), popupOff()));
			pages.add(audioSpecs);

			List<ButtonSpec> subSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : subtitleTracks) {
				subSpecs.add(render(entry.getDisplayName(), setSubtitle(entry.getStreamIndex())));
			}
			subSpecs.add(render(getLabel(MENU_BACK), setButtonPage(0, 1)));
			subSpecs.add(render(getLabel(MENU_EXIT), popupOff()));
			pages.add(subSpecs);

		} else if (needsAudio) {
			// AUDIO_ONLY: direct track list on page 0
			List<ButtonSpec> audioSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : audioTracks) {
				audioSpecs.add(render(entry.getDisplayName(), setAudio(entry.getStreamIndex())));
			}
			audioSpecs.add(render(getLabel(MENU_EXIT), popupOff()));
			pages.add(audioSpecs);

		} else {
			// SUBS_ONLY: direct track list on page 0
			List<ButtonSpec> subSpecs = new ArrayList<>();
			for (PopupMenuConfig.TrackEntry entry : subtitleTracks) {
				subSpecs.add(render(entry.getDisplayName(), setSubtitle(entry.getStreamIndex())));
			}
			subSpecs.add(render(getLabel(MENU_EXIT), popupOff()));
			pages.add(subSpecs);
		}

		// ── Collect all images for shared palette ────────────────────────────

		List<BufferedImage> allImages = new ArrayList<>();
		for (List<ButtonSpec> pageSpecs : pages) {
			for (ButtonSpec spec : pageSpecs) {
				allImages.add(spec.images().normal());
				allImages.add(spec.images().selected());
				allImages.add(spec.images().activated());
			}
		}

		IgsPalette palette = PaletteBuilder.buildFromImages(0, allImages.toArray(new BufferedImage[0]));

		// ── RLE-encode all images ───────────────────────────────────────────

		List<IgsObject> objects = new ArrayList<>();
		for (int i = 0; i < allImages.size(); i++) {
			BufferedImage img = allImages.get(i);
			byte[] rle = RleEncoder.encode(img, palette);

			IgsObject obj = new IgsObject();
			obj.setId(i);
			obj.setVersion(0);
			obj.setWidth(img.getWidth());
			obj.setHeight(img.getHeight());
			obj.setRleData(rle);
			obj.setDataLength(rle.length + 4);

			SequenceDescriptor sd = new SequenceDescriptor();
			sd.setFirstInSequence(true);
			sd.setLastInSequence(true);
			obj.setSequenceDescriptor(sd);

			objects.add(obj);
		}

		// ── Build pages ─────────────────────────────────────────────────────

		IgsInteractiveComposition ic = new IgsInteractiveComposition();
		ic.setStreamModel(IgsInteractiveComposition.STREAM_MODEL_OUT_OF_MUX); // Out-of-Mux
		ic.setUiModel(IgsInteractiveComposition.UI_MODEL_POP_UP); // Pop-Up
		ic.setUserTimeoutDuration(0); // No user timeout

		int objectBase = 0;
		for (int pageId = 0; pageId < pages.size(); pageId++) {
			List<ButtonSpec> specs = pages.get(pageId);
			ic.getPages().add(buildPage(pageId, specs, objectBase, screenW, screenH));
			objectBase += specs.size() * 3;
		}

		// ── Build ICS ───────────────────────────────────────────────────────

		VideoDescriptor vd = new VideoDescriptor();
		vd.setWidth(screenW);
		vd.setHeight(screenH);
		vd.setFrameRateCode(1); // 24000/1001

		CompositionDescriptor cd = new CompositionDescriptor();
		cd.setNumber(0);
		cd.setState(2); // Epoch start

		SequenceDescriptor sd = new SequenceDescriptor();
		sd.setFirstInSequence(true);
		sd.setLastInSequence(true);

		IgsCompositionSegment ics = new IgsCompositionSegment();
		ics.setVideoDescriptor(vd);
		ics.setCompositionDescriptor(cd);
		ics.setSequenceDescriptor(sd);
		ics.setInteractiveComposition(ic);

		// ── Window Definition ────────────────────────────────────────────────

		IgsWindow window = new IgsWindow();
		window.setId(0);
		window.setX(0);
		window.setY(0);
		window.setWidth(screenW);
		window.setHeight(screenH);

		IgsWindowDefinition wds = new IgsWindowDefinition();
		wds.getWindows().add(window);

		// ── Assemble Display Set ────────────────────────────────────────────

		IgsDisplaySet displaySet = new IgsDisplaySet();
		displaySet.setEpochStart(true);
		displaySet.setComplete(true);
		displaySet.setCompositionSegment(ics);
		displaySet.getPalettes().add(palette);
		displaySet.getWindowDefinitions().add(wds);
		displaySet.setObjects(objects);

		log.info("Popup menu IGS built: {} page(s), {} objects", pages.size(), objects.size());

		return displaySet;
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

			btn.setNormalStartObjectIdRef(normalObjId);
			btn.setNormalEndObjectIdRef(normalObjId);
			btn.setSelectedStartObjectIdRef(selectedObjId);
			btn.setSelectedEndObjectIdRef(selectedObjId);
			btn.setSelectedSoundIdRef(0xFF);
			btn.setActivatedStartObjectIdRef(activatedObjId);
			btn.setActivatedEndObjectIdRef(activatedObjId);
			btn.setActivatedSoundIdRef(0xFF);

			btn.setNavigationCommands(specs.get(i).commands());

			// D-pad wiring: vertical list with wrap
			btn.setUpperButtonIdRef(i == 0 ? totalButtons : i); // wrap to last
			btn.setLowerButtonIdRef(i == totalButtons - 1 ? 1 : i + 2); // wrap to first
			btn.setLeftButtonIdRef(buttonId); // stay
			btn.setRightButtonIdRef(buttonId); // stay

			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(buttonId);
			bog.getButtons().add(btn);
			bogs.add(bog);
		}

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
