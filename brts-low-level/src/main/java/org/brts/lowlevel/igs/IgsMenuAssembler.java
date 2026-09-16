package org.brts.lowlevel.igs;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.TextRenderer;
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

/**
 * Shared assembly helpers for menu-style IGS builders (popup, title, setup menus), factoring out the boilerplate that
 * is otherwise duplicated across every builder: RLE-encoding button images into objects, building the
 * composition/window segments, wiring a simple vertical button list, and binding button visual states.
 */
public final class IgsMenuAssembler {

	private IgsMenuAssembler() {
	}

	/** A {@link SequenceDescriptor} marking both the first and last (i.e. only) fragment. */
	public static SequenceDescriptor newSingleSequence() {
		SequenceDescriptor sequence = new SequenceDescriptor();
		sequence.setFirstInSequence(true);
		sequence.setLastInSequence(true);
		return sequence;
	}

	/**
	 * RLE-encodes each image against the given palette, producing one {@link IgsObject} per image with id equal to its
	 * index in the list.
	 */
	public static List<IgsObject> buildObjectsFromImages(List<BufferedImage> images, IgsPalette palette) {
		List<IgsObject> objects = new ArrayList<>();
		for (int objectId = 0; objectId < images.size(); objectId++) {
			BufferedImage image = images.get(objectId);
			byte[] rle = RleEncoder.encode(image, palette);

			IgsObject object = new IgsObject();
			object.setId(objectId);
			object.setVersion(0);
			object.setWidth(image.getWidth());
			object.setHeight(image.getHeight());
			object.setRleData(rle);
			object.setDataLength(rle.length + 4); // +4 for width(2)+height(2)
			object.setSequenceDescriptor(newSingleSequence());
			objects.addAll(IgsPgsCodec.fragmentObject(object));
		}
		return objects;
	}

	/** Builds the composition segment (video/composition descriptors + epoch start) wrapping the given composition. */
	public static IgsCompositionSegment buildCompositionSegment(IgsInteractiveComposition composition, int screenW,
			int screenH) {
		VideoDescriptor videoDescriptor = new VideoDescriptor();
		videoDescriptor.setWidth(screenW);
		videoDescriptor.setHeight(screenH);
		videoDescriptor.setFrameRateCode(1);

		CompositionDescriptor compositionDescriptor = new CompositionDescriptor();
		compositionDescriptor.setNumber(0);
		compositionDescriptor.setState(2); // Epoch start

		IgsCompositionSegment segment = new IgsCompositionSegment();
		segment.setVideoDescriptor(videoDescriptor);
		segment.setCompositionDescriptor(compositionDescriptor);
		segment.setSequenceDescriptor(newSingleSequence());
		segment.setInteractiveComposition(composition);
		return segment;
	}

	/** A single window covering the whole screen. */
	public static IgsWindowDefinition buildFullScreenWindowDefinition(int screenW, int screenH) {
		IgsWindow window = new IgsWindow();
		window.setId(0);
		window.setX(0);
		window.setY(0);
		window.setWidth(screenW);
		window.setHeight(screenH);

		IgsWindowDefinition definition = new IgsWindowDefinition();
		definition.getWindows().add(window);
		return definition;
	}

	/** Assembles a complete, single-epoch display set from its already-built parts. */
	public static IgsDisplaySet assembleDisplaySet(IgsCompositionSegment compositionSegment, IgsPalette palette,
			IgsWindowDefinition windowDefinition, List<IgsObject> objects) {
		IgsDisplaySet displaySet = new IgsDisplaySet();
		displaySet.setEpochStart(true);
		displaySet.setComplete(true);
		displaySet.setCompositionSegment(compositionSegment);
		displaySet.getPalettes().add(palette);
		displaySet.getWindowDefinitions().add(windowDefinition);
		displaySet.setObjects(objects);
		return displaySet;
	}

	/** Binds the normal/selected/activated object id refs (start==end) and sets both sound id refs to "no sound". */
	public static void bindButtonVisualStates(IgsButton button, int normalObjId, int selectedObjId,
			int activatedObjId) {
		button.setNormalStartObjectIdRef(normalObjId);
		button.setNormalEndObjectIdRef(normalObjId);
		button.setSelectedStartObjectIdRef(selectedObjId);
		button.setSelectedEndObjectIdRef(selectedObjId);
		button.setSelectedSoundIdRef(0xFF);
		button.setActivatedStartObjectIdRef(activatedObjId);
		button.setActivatedEndObjectIdRef(activatedObjId);
		button.setActivatedSoundIdRef(0xFF);
	}

	/**
	 * Wires a simple vertical button list: up/down wrap around the list, left/right stay on the same button.
	 *
	 * @param buttonsInOrder the buttons in vertical (top-to-bottom) order
	 */
	public static void wireVerticalWrapNeighbours(List<IgsButton> buttonsInOrder) {
		int total = buttonsInOrder.size();
		for (int i = 0; i < total; i++) {
			IgsButton button = buttonsInOrder.get(i);
			int selfId = button.getId();
			button.setUpperButtonIdRef(buttonsInOrder.get((i - 1 + total) % total).getId());
			button.setLowerButtonIdRef(buttonsInOrder.get((i + 1) % total).getId());
			button.setLeftButtonIdRef(selfId);
			button.setRightButtonIdRef(selfId);
		}
	}

	/** A single labeled button ready for page assembly: its rendered state images and its navigation commands. */
	public record LabeledButton(TextRenderer.ButtonImages images, List<NavigationCommand> commands,
			boolean autoAction) {

		public LabeledButton(TextRenderer.ButtonImages images, List<NavigationCommand> commands) {
			this(images, commands, false);
		}
	}

	/** A rendered button with its screen position and directional navigation references. */
	public record PositionedButton(TextRenderer.ButtonImages images, List<NavigationCommand> commands,
			boolean autoAction, int x, int y, int upperButtonIdRef, int lowerButtonIdRef, int leftButtonIdRef,
			int rightButtonIdRef) {

		public PositionedButton(TextRenderer.ButtonImages images, List<NavigationCommand> commands, int x, int y,
				int upperButtonIdRef, int lowerButtonIdRef, int leftButtonIdRef, int rightButtonIdRef) {
			this(images, commands, false, x, y, upperButtonIdRef, lowerButtonIdRef, leftButtonIdRef, rightButtonIdRef);
		}
	}

	/** A non-interactive image rendered before the selectable buttons on a page. */
	public record PositionedDecoration(BufferedImage image, int x, int y) {
	}

	/** Builds a page from buttons whose layout and directional navigation have already been resolved. */
	public static IgsPage buildPositionedPage(int pageId, List<PositionedButton> buttons, int objectBase) {
		return buildPositionedPage(pageId, buttons, objectBase, buttons.isEmpty() ? 0xFFFF : 1);
	}

	/** Builds a page with an explicit initially-selected button. */
	public static IgsPage buildPositionedPage(int pageId, List<PositionedButton> buttons, int objectBase,
			int defaultSelectedButtonIdRef) {
		return buildPositionedPage(pageId, buttons, List.of(), objectBase, defaultSelectedButtonIdRef);
	}

	/** Builds a page with decorative images painted before its selectable buttons. */
	public static IgsPage buildPositionedPage(int pageId, List<PositionedButton> buttons,
			List<PositionedDecoration> decorations, int objectBase, int defaultSelectedButtonIdRef) {
		List<IgsBog> bogs = new ArrayList<>();
		for (int i = 0; i < decorations.size(); i++) {
			PositionedDecoration decoration = decorations.get(i);
			int buttonId = buttons.size() + i + 1;
			IgsButton button = new IgsButton();
			button.setId(buttonId);
			button.setNumericSelectValue(0xFFFF);
			button.setXPos(decoration.x());
			button.setYPos(decoration.y());
			button.setUpperButtonIdRef(buttonId);
			button.setLowerButtonIdRef(buttonId);
			button.setLeftButtonIdRef(buttonId);
			button.setRightButtonIdRef(buttonId);
			bindButtonVisualStates(button, objectBase + i, objectBase + i, objectBase + i);

			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(buttonId);
			bog.getButtons().add(button);
			bogs.add(bog);
		}

		int selectableObjectBase = objectBase + decorations.size();
		for (int i = 0; i < buttons.size(); i++) {
			PositionedButton positionedButton = buttons.get(i);
			int buttonId = i + 1;
			IgsButton button = new IgsButton();
			button.setId(buttonId);
			button.setNumericSelectValue(0xFFFF);
			button.setAutoAction(positionedButton.autoAction());
			button.setXPos(positionedButton.x());
			button.setYPos(positionedButton.y());
			button.setUpperButtonIdRef(positionedButton.upperButtonIdRef());
			button.setLowerButtonIdRef(positionedButton.lowerButtonIdRef());
			button.setLeftButtonIdRef(positionedButton.leftButtonIdRef());
			button.setRightButtonIdRef(positionedButton.rightButtonIdRef());
			bindButtonVisualStates(button, selectableObjectBase + i * 3, selectableObjectBase + i * 3 + 1,
					selectableObjectBase + i * 3 + 2);
			button.setNavigationCommands(positionedButton.commands());

			IgsBog bog = new IgsBog();
			bog.setDefaultValidButtonIdRef(buttonId);
			bog.getButtons().add(button);
			bogs.add(bog);
		}

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

	/**
	 * Builds a simple vertically-stacked, wrap-wired button list page (used by popup and title-menu settings submenus):
	 * buttons are centered horizontally and stacked bottom-up ending at {@code marginBottom}, sharing object ids
	 * starting at {@code objectBase} (3 per button: normal/selected/activated).
	 */
	public static IgsPage buildVerticalButtonListPage(int pageId, List<LabeledButton> buttons, int objectBase,
			int screenW, int screenH, int buttonHeight, int buttonMaxWidth, int buttonSpacingY, int marginBottom) {
		int totalButtons = buttons.size();
		int totalHeight = totalButtons * buttonHeight + (totalButtons - 1) * buttonSpacingY;
		int startY = screenH - marginBottom - totalHeight;
		int groupX = Math.max(40, Math.min((screenW - buttonMaxWidth) / 2, screenW - buttonMaxWidth - 40));

		List<PositionedButton> positionedButtons = new ArrayList<>();
		for (int i = 0; i < totalButtons; i++) {
			int buttonId = i + 1; // 1-based
			int y = startY + i * (buttonHeight + buttonSpacingY);
			int upperButtonId = ((i - 1 + totalButtons) % totalButtons) + 1;
			int lowerButtonId = ((i + 1) % totalButtons) + 1;
			positionedButtons.add(new PositionedButton(buttons.get(i).images(), buttons.get(i).commands(), groupX, y,
					upperButtonId, lowerButtonId, buttonId, buttonId));
		}
		return buildPositionedPage(pageId, positionedButtons, objectBase);
	}

}
