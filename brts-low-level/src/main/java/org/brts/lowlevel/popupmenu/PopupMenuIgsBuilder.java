package org.brts.lowlevel.popupmenu;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.TextRenderer;
import org.brts.common.menu.TextRenderer.ButtonImages;
import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand;
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

import lombok.extern.slf4j.Slf4j;

/**
 * Builds an {@link IgsDisplaySet} for a popup audio/subtitle selection menu.
 * <p>
 * The popup menu has two pages:
 * <ul>
 * <li><b>Page 0 (Audio)</b>: one button per audio track + "Subtitles &gt;" page-switch + "Exit" button</li>
 * <li><b>Page 1 (Subtitles)</b>: one button per subtitle track + "&lt; Audio" page-switch + "Exit" button</li>
 * </ul>
 * <p>
 * Navigation commands:
 * <ul>
 * <li>Audio buttons → SET_STREAM (primary audio stream number in bits 31:24 of operand 1)</li>
 * <li>Subtitle buttons → SET_STREAM (PG stream number in bits 15:8 of operand 1)</li>
 * <li>Page-switch buttons → SET_BUTTON_PAGE</li>
 * <li>Exit buttons → POPUP_OFF</li>
 * </ul>
 * <p>
 * The interactive composition uses {@code streamModel=0} (Out-Of-Mux) and {@code uiModel=1} (Pop-Up).
 */
@Slf4j
public class PopupMenuIgsBuilder {

	private static final int BUTTON_HEIGHT = 40;

	private static final int BUTTON_MAX_WIDTH = 400;

	private static final int BUTTON_SPACING_Y = 8;

	private static final int GROUP_SPACING_X = 60;

	private static final int MARGIN_BOTTOM = 80;

	/**
	 * Builds the popup menu IGS display set.
	 *
	 * @param config the popup menu configuration
	 * @return a fully-populated {@link IgsDisplaySet}
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

		TextStyle style = buildStyle();

		// ── Render all button images ────────────────────────────────────────

		// Page 0: audio buttons + "Subtitles >" + "Exit"
		List<ButtonImages> page0Images = new ArrayList<>();
		for (PopupMenuConfig.TrackEntry entry : audioTracks) {
			page0Images.add(TextRenderer.renderTextButton(entry.getDisplayName(), style, BUTTON_MAX_WIDTH));
		}
		ButtonImages page0SwitchImg = TextRenderer.renderTextButton("Subtitles \u25B6", style, BUTTON_MAX_WIDTH);
		page0Images.add(page0SwitchImg);
		ButtonImages page0ExitImg = TextRenderer.renderTextButton("Exit", style, BUTTON_MAX_WIDTH);
		page0Images.add(page0ExitImg);

		// Page 1: subtitle buttons + "< Audio" + "Exit"
		List<ButtonImages> page1Images = new ArrayList<>();
		for (PopupMenuConfig.TrackEntry entry : subtitleTracks) {
			page1Images.add(TextRenderer.renderTextButton(entry.getDisplayName(), style, BUTTON_MAX_WIDTH));
		}
		ButtonImages page1SwitchImg = TextRenderer.renderTextButton("\u25C0 Audio", style, BUTTON_MAX_WIDTH);
		page1Images.add(page1SwitchImg);
		ButtonImages page1ExitImg = TextRenderer.renderTextButton("Exit", style, BUTTON_MAX_WIDTH);
		page1Images.add(page1ExitImg);

		// ── Collect all images for shared palette ────────────────────────────

		List<BufferedImage> allImages = new ArrayList<>();
		for (ButtonImages bi : page0Images) {
			allImages.add(bi.normal());
			allImages.add(bi.selected());
			allImages.add(bi.activated());
		}
		for (ButtonImages bi : page1Images) {
			allImages.add(bi.normal());
			allImages.add(bi.selected());
			allImages.add(bi.activated());
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

		int page0ObjectBase = 0;
		int page1ObjectBase = page0Images.size() * 3;

		IgsPage page0 = buildPage(0, page0Images, audioTracks.size(), screenW, screenH, page0ObjectBase, true,
				audioTracks, subtitleTracks);
		IgsPage page1 = buildPage(1, page1Images, subtitleTracks.size(), screenW, screenH, page1ObjectBase, false,
				audioTracks, subtitleTracks);

		// ── Build Interactive Composition ────────────────────────────────────

		IgsInteractiveComposition ic = new IgsInteractiveComposition();
		ic.setStreamModel(0); // Out-of-Mux
		ic.setUiModel(1); // Pop-Up
		ic.setUserTimeoutDuration(0xFF);
		ic.getPages().add(page0);
		ic.getPages().add(page1);

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

		log.info("Popup menu IGS built: page0={} buttons, page1={} buttons, {} objects", page0Images.size(),
				page1Images.size(), objects.size());

		return displaySet;
	}

	// ── Page builder ────────────────────────────────────────────────────────

	private IgsPage buildPage(int pageId, List<ButtonImages> images, int trackButtonCount, int screenW, int screenH,
			int objectBase, boolean isAudioPage, List<PopupMenuConfig.TrackEntry> audioTracks,
			List<PopupMenuConfig.TrackEntry> subtitleTracks) {

		int totalButtons = images.size(); // track buttons + switch + exit
		int totalHeight = totalButtons * BUTTON_HEIGHT + (totalButtons - 1) * BUTTON_SPACING_Y;
		int startY = screenH - MARGIN_BOTTOM - totalHeight;

		// Centre the button group horizontally
		int groupX;
		if (isAudioPage) {
			groupX = screenW / 2 - BUTTON_MAX_WIDTH - GROUP_SPACING_X / 2;
		} else {
			groupX = screenW / 2 + GROUP_SPACING_X / 2;
		}
		// Clamp to reasonable bounds
		groupX = Math.max(40, Math.min(groupX, screenW - BUTTON_MAX_WIDTH - 40));

		List<IgsBog> bogs = new ArrayList<>();
		for (int i = 0; i < totalButtons; i++) {
			int buttonId = i + 1; // 1-based
			int normalObjId = objectBase + i * 3;
			int selectedObjId = objectBase + i * 3 + 1;
			int activatedObjId = objectBase + i * 3 + 2;

			int y = startY + i * (BUTTON_HEIGHT + BUTTON_SPACING_Y);

			// Determine navigation command
			List<ParsedNavigationCommand> navCmds = buildNavCommand(i, trackButtonCount, isAudioPage, audioTracks,
					subtitleTracks, pageId);

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

			btn.setNavigationCommands(navCmds);

			// D-pad wiring: vertical list
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

	// ── Navigation command builder ──────────────────────────────────────────

	private List<ParsedNavigationCommand> buildNavCommand(int buttonIndex, int trackButtonCount, boolean isAudioPage,
			List<PopupMenuConfig.TrackEntry> audioTracks, List<PopupMenuConfig.TrackEntry> subtitleTracks, int pageId) {

		if (buttonIndex < trackButtonCount) {
			// Track selection button
			if (isAudioPage) {
				int streamIndex = audioTracks.get(buttonIndex).getStreamIndex();
				// SET_STREAM: primary audio in bits 31:24 of operand1, enable flag in bit 31
				long op1 = (1L << 31) | ((long) (streamIndex & 0x7F) << 24);
				return List.of(ParsedNavigationCommand.compile("SET_STREAM", op1, true, 0, false));
			} else {
				int streamIndex = subtitleTracks.get(buttonIndex).getStreamIndex();
				// SET_STREAM: PG stream in bits 15:8 of operand1, enable flag in bit 15
				long op1 = (1L << 15) | ((long) (streamIndex & 0x7F) << 8);
				return List.of(ParsedNavigationCommand.compile("SET_STREAM", op1, true, 0, false));
			}
		} else if (buttonIndex == trackButtonCount) {
			// Page-switch button
			int targetPage = isAudioPage ? 1 : 0;
			// SET_BUTTON_PAGE: operand1 = page number, operand2 = button ID (1 = first button)
			return List.of(ParsedNavigationCommand.compile("SET_BUTTON_PAGE", targetPage, true, 1, true));
		} else {
			// Exit button
			return List.of(ParsedNavigationCommand.compile("POPUP_OFF", 0, false, 0, false));
		}
	}

	// ── Style ───────────────────────────────────────────────────────────────

	private TextStyle buildStyle() {
		TextStyle style = new TextStyle();
		style.setFontName("SansSerif");
		style.setFontSize(24);
		style.setFontStyle(0); // PLAIN
		style.setNormalColor("#C0FFFFFF"); // semi-transparent white
		style.setSelectedColor("#FFFFD700"); // gold
		style.setActivatedColor("#FFFF6600"); // orange
		style.setOutline(true);
		style.setOutlineColor("#FF000000");
		style.setOutlineWidth(1.5f);
		style.setBackgroundShape(TextStyle.BackgroundShape.ROUNDED_RECTANGLE);
		style.setBackgroundColor("#80000000"); // 50% black
		style.setPaddingX(16);
		style.setPaddingY(8);
		return style.withDefaults();
	}

}
