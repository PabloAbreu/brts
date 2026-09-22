package org.brts.lowlevel.titlemenu.layout;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/layout/LayoutResult.java' is part of BRTS.
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
import java.util.ArrayList;
import java.util.List;

import org.brts.common.utils.composition.ImagesComposition;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;

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
	 * {@link org.brts.lowlevel.utils.composition.CompositedVideoGenerator} to produce the background M2TS instead of
	 * simply muxing the source media.
	 */
	private ImagesComposition backgroundComposition;

	/**
	 * Whether the background should be generated via composition (true) or comes from the source media directly
	 * (false).
	 */
	private boolean compositeBackground;

	/** Optional rendered settings button on the main title-selection page. */
	private PositionedButton settingsButton;

	/** Rendered settings submenu pages in display order. */
	private List<SettingsPage> settingsPages = new ArrayList<>();

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

		/** Zero-based row in the resolved visual grid. */
		private int gridRow;

		/** Zero-based column in the resolved visual grid. */
		private int gridColumn;

		/** Navigation commands for non-title buttons. Title buttons use JUMP_TITLE. */
		private List<NavigationCommand> navigationCommands;

	}

	/** A rendered IGS submenu page. */
	@Getter
	@Setter
	public static class SettingsPage {
		private int pageId;
		private List<PositionedButton> buttons;
	}

}
