package org.brts.middle.preview;

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
public class DisplaySetPreviewModel {

	// ── Static data (from the parsed display set) ───────────────────────────

	/** Video descriptor (screen width / height). */
	private VideoDescriptor videoDescriptor;

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

	/** GPR register bank, persisted across button activations to simulate a real player's register file. */
	private Map<Integer, Long> gprRegisters = new HashMap<>();

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

	public int getScreenWidth() {
		return videoDescriptor != null ? videoDescriptor.getWidth() : 1920;
	}

	public int getScreenHeight() {
		return videoDescriptor != null ? videoDescriptor.getHeight() : 1080;
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
			for (var entry : bogEnabledButtons.entrySet()) {
				selectedButtonId = entry.getValue();
				return;
			}
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
