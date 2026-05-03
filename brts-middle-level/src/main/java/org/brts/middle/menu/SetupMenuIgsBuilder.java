package org.brts.middle.menu;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.brts.middle.menu.descriptor.AudioMenuItem;
import org.brts.middle.menu.descriptor.MenuItem;
import org.brts.middle.menu.descriptor.MiscMenuItem;
import org.brts.middle.menu.descriptor.NavigationRefs;
import org.brts.middle.menu.descriptor.SetupMenuDescriptor;
import org.brts.middle.menu.descriptor.SubtitleMenuItem;
import org.brts.middle.menu.render.ButtonImageRenderer;
import org.brts.middle.menu.render.ButtonImageRenderer.ButtonImages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class SetupMenuIgsBuilder {

	private static final Logger log = LoggerFactory.getLogger(SetupMenuIgsBuilder.class);

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
			int yPos, int width, int height, List<ParsedNavigationCommand> navigationCommands, String description,
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

		for (int i = 0; i < allImages.size(); i++) {
			BufferedImage img = allImages.get(i);
			byte[] rle = RleEncoder.encode(img, palette);

			IgsObject obj = new IgsObject();
			obj.setId(i);
			obj.setVersion(0);
			obj.setWidth(img.getWidth());
			obj.setHeight(img.getHeight());
			obj.setRleData(rle);
			obj.setDataLength(rle.length + 4); // +4 for width(2)+height(2)

			SequenceDescriptor sd = new SequenceDescriptor();
			sd.setFirstInSequence(true);
			sd.setLastInSequence(true);
			obj.setSequenceDescriptor(sd);

			objects.add(obj);
		}

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
			btn.setNormalStartObjectIdRef(br.normalObjectId);
			btn.setNormalEndObjectIdRef(br.normalObjectId);
			btn.setSelectedStartObjectIdRef(br.selectedObjectId);
			btn.setSelectedEndObjectIdRef(br.selectedObjectId);
			btn.setSelectedSoundIdRef(0xFF);
			btn.setActivatedStartObjectIdRef(br.activatedObjectId);
			btn.setActivatedEndObjectIdRef(br.activatedObjectId);
			btn.setActivatedSoundIdRef(0xFF);

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
		ic.setStreamModel(1); // In-Mux (multiplexed with AV)
		ic.setUiModel(0); // Always-On
		ic.setUserTimeoutDuration(0xFF);
		ic.getPages().add(page);

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

		// ── Build Window Definition ─────────────────────────────────────────

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
			y = registerButton(item, images, compileAudioCommand(item.getStreamNumber()), startX, screenW, y);
		}
		return y;
	}

	private int renderSubtitleCategory(List<SubtitleMenuItem> items, TextStyle globalStyle, int startX, int startY,
			int screenW) {
		int y = startY;
		for (SubtitleMenuItem item : items) {
			TextStyle style = resolveItemStyle(item.getStyle(), globalStyle);
			ButtonImages images = ButtonImageRenderer.renderTextButton(item.getDescription(), style);
			y = registerButton(item, images, compileSubtitleCommand(item.getStreamNumber()), startX, screenW, y);
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
			y = registerButton(item, images, compileMiscCommand(item), startX, screenW, y);
		}
		return y;
	}

	// ── Layout helpers ──────────────────────────────────────────────────────

	private int registerButton(MenuItem item, ButtonImages images, List<ParsedNavigationCommand> navCmds, int startX,
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

		for (int i = 0; i < buttons.size(); i++) {
			IgsButton btn = buttons.get(i);

			// Auto-wired defaults: vertical wrap, no left/right movement
			int autoUp = buttons.get((i - 1 + buttons.size()) % buttons.size()).getId();
			int autoDown = buttons.get((i + 1) % buttons.size()).getId();
			int autoLeft = btn.getId();
			int autoRight = btn.getId();

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

	// ── Command compilation ─────────────────────────────────────────────────

	/**
	 * Compiles a SET_STREAM command for audio: writes the audio stream number into PSR1 (primary audio stream).
	 * <p>
	 * SET_STREAM's operand1 encodes audio/subtitle/IG stream numbers in a packed 32-bit value: bits[31:16] = primary
	 * audio (1-based), other bits left zero.
	 */
	private List<ParsedNavigationCommand> compileAudioCommand(int streamNumber) {
		// SET_STREAM: op1 = packed stream numbers
		// For primary audio: bits 31..16 = stream number, bits 15..0 = 0 (no change)
		// SET_STREAM operand format: [31:24]=primary_audio_flag|primary_audio_number
		// [23:16]=reserved [15:8]=PG_textST_flag|PG_textST_number [7:0]=IG_stream_number
		// For just setting audio: flag=1 in bit 31, number in bits 30..24
		// Simplified: operand1 = (1 << 31) | (streamNumber << 24)
		long op1 = (1L << 31) | ((long) (streamNumber & 0x7F) << 24);
		long op2 = 0;

		return List.of(ParsedNavigationCommand.compile("SET_STREAM", op1, true, op2, false));
	}

	/**
	 * Compiles a SET_STREAM command for subtitles: writes the subtitle stream number into PSR2 (PG/subtitle stream).
	 * <p>
	 * For subtitle: bits 15..8 encode PG/textST flag + number.
	 */
	private List<ParsedNavigationCommand> compileSubtitleCommand(int streamNumber) {
		// For subtitle: flag in bit 15, number in bits 14..8
		// streamNumber=0 means subtitles off
		long op1;
		if (streamNumber > 0) {
			op1 = (1L << 15) | ((long) (streamNumber & 0x7F) << 8);
		} else {
			op1 = 0; // subtitles off
		}
		long op2 = 0;

		return List.of(ParsedNavigationCommand.compile("SET_STREAM", op1, true, op2, false));
	}

	/**
	 * Compiles navigation commands for miscellaneous items.
	 */
	private List<ParsedNavigationCommand> compileMiscCommand(MiscMenuItem item) {
		return switch (item.getType()) {
		case LAUNCH -> {
			// PLAY_PL <playlist_number>
			int playlistNumber = Integer.parseInt(item.getTarget());
			yield List.of(ParsedNavigationCommand.compile("PLAY_PL", playlistNumber, true, 0, false));
		}
		case GO_BACK -> {
			// JUMP_TITLE <title_number>
			int titleNumber = item.getTarget() != null ? Integer.parseInt(item.getTarget()) : 0;
			yield List.of(ParsedNavigationCommand.compile("JUMP_TITLE", titleNumber, true, 0, false));
		}
		case POPUP_OFF -> {
			yield List.of(ParsedNavigationCommand.compile("POPUP_OFF", 0, false, 0, false));
		}
		case RESUME -> {
			yield List.of(ParsedNavigationCommand.compile("RESUME", 0, false, 0, false));
		}
		};
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
