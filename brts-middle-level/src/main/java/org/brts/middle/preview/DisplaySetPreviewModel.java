package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/DisplaySetPreviewModel.java' is part of BRTS.
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

import lombok.Getter;
import lombok.Setter;
import org.brts.lowlevel.igs.model.*;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the runtime state for an IGS Display Set preview session.
 * <p>
 * This model tracks the current page, which buttons are enabled/selected/activated, and pre-decoded button images. It
 * is the "engine" behind the Swing viewer.
 */
@Getter
@Setter
public class DisplaySetPreviewModel extends ScreenModel {

	// ── Static data (from the parsed display set) ───────────────────────────

	/** All pages in the interactive composition. */
	private List<IgsPage> pages = new ArrayList<>();

	/** Map from palette id → palette. */
	private Map<Integer, IgsPalette> palettes = new HashMap<>();

	/** Map from object id → pre-decoded ARGB image. */
	private Map<Integer, BufferedImage> objectImages = new HashMap<>();

	/** UI model: 0 = always-on, 1 = pop-up. */
	private int uiModel;

	// ── Mutable runtime state ───────────────────────────────────────────────

	/** Currently displayed page index (in {@link #pages}). */
	private int currentPageIndex;

	/** Id of the currently selected button (0xFFFF = none). */
	private int selectedButtonId = 0xFFFF;

	/** Id of the currently activated button (-1 = none). */
	private int activatedButtonId = -1;

	/**
	 * Per-BOG enabled button id. Key = bog index within the current page, Value = enabled button id.
	 */
	private Map<Integer, Integer> bogEnabledButtons = new HashMap<>();

	/** If non-null, a navigation command overlay message to display briefly. */
	private String commandOverlayMessage;

	/** Timestamp (epoch ms) when the overlay was set — cleared after ~2 s. */
	private long commandOverlayTimestamp;

	private boolean displayHints = true;

	// ── Convenience ─────────────────────────────────────────────────────────

	public IgsPage getCurrentPage() {
		if (currentPageIndex >= 0 && currentPageIndex < pages.size()) {
			return pages.get(currentPageIndex);
		}
		return null;
	}

	/**
	 * Initialises (or re-initialises) BOG enabled-button state for the current page.
	 */
	public void resetBogState() {
		bogEnabledButtons.clear();
		IgsPage page = getCurrentPage();
		if (page == null)
			return;
		for (int i = 0; i < page.getBogs().size(); i++) {
			IgsBog bog = page.getBogs().get(i);
			bogEnabledButtons.put(i, bog.getDefaultValidButtonIdRef());
		}
	}

	/**
	 * Initialise selected button to the page's default.
	 */
	public void resetSelectedButton() {
		IgsPage page = getCurrentPage();
		if (page != null && page.getDefaultSelectedButtonIdRef() != 0xFFFF) {
			selectedButtonId = page.getDefaultSelectedButtonIdRef();
		} else {
			// Fall back to first enabled button
			bogEnabledButtons.values().stream().findFirst().ifPresent(value -> selectedButtonId = value);
		}
	}

	public IgsButton getCurrentButton() {
		return findButtonOnCurrentPage(selectedButtonId);
	}

	public IgsButton findButtonOnCurrentPage(int buttonId) {
		IgsPage page = getCurrentPage();
		if (page == null)
			return null;
		for (IgsBog bog : page.getBogs()) {
			for (IgsButton btn : bog.getButtons()) {
				if (btn.getId() == buttonId)
					return btn;
			}
		}
		return null;
	}

}
