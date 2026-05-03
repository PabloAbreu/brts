package org.brts.lowlevel.titlemenu.layout;

import java.awt.image.BufferedImage;
import java.util.List;

import org.brts.common.utils.composition.ImagesComposition;

import lombok.Getter;
import lombok.Setter;

/**
 * Result produced by a {@link TitleMenuLayout} implementation.
 * <p>
 * Contains the positioned button images for IGS construction and optionally an {@link ImagesComposition} descriptor
 * when the background video must be generated via compositing (e.g. animated thumbnails).
 */
@Getter
@Setter
public class LayoutResult {

	/** Positioned buttons with their three-state images and screen coordinates. */
	private List<PositionedButton> buttons;

	/**
	 * Optional composition descriptor for background video generation. When non-null, the orchestrator should use
	 * {@link org.brts.common.utils.composition.CompositedVideoGenerator} to produce the background M2TS instead of
	 * simply muxing the source media.
	 */
	private ImagesComposition backgroundComposition;

	/**
	 * Whether the background should be generated via composition (true) or comes from the source media directly
	 * (false).
	 */
	private boolean compositeBackground;

	/**
	 * A single positioned button with its three-state rendered images.
	 */
	@Getter
	@Setter
	public static class PositionedButton {

		/** Zero-based index matching the order in TitleMenuDescriptor.titles. */
		private int titleIndex;

		/** Title number for JUMP_TITLE navigation command. */
		private int titleNumber;

		/** X position on screen (pixels from left). */
		private int x;

		/** Y position on screen (pixels from top). */
		private int y;

		/** Normal (idle) state button image (ARGB). */
		private BufferedImage normalImage;

		/** Selected (focused) state button image (ARGB). */
		private BufferedImage selectedImage;

		/** Activated (pressed) state button image (ARGB). */
		private BufferedImage activatedImage;

		/** Button width in pixels. */
		private int width;

		/** Button height in pixels. */
		private int height;

	}

}
