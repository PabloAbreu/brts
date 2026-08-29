package org.brts.lowlevel.igs;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.brts.lowlevel.igs.model.CompositionDescriptor;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsCompositionSegment;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.igs.model.IgsInteractiveComposition;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindow;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;
import org.brts.lowlevel.igs.model.SequenceDescriptor;
import org.brts.lowlevel.igs.model.VideoDescriptor;

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
			objects.add(object);
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

}
