package org.brts.lowlevel.popupmenu;

import java.util.List;

import org.brts.common.menu.TextStyle;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for popup menu generation.
 * <p>
 * Describes the audio and subtitle tracks to present as selectable buttons in the popup menu IGS overlay.
 */
@Getter
@Setter
public class PopupMenuConfig {

	/** Ordered list of audio tracks for the popup menu. */
	private List<TrackEntry> audioTracks;

	/** Ordered list of subtitle tracks for the popup menu. */
	private List<TrackEntry> subtitleTracks;

	/** 5-digit clip name for the IGS M2TS output (e.g. "00800"). */
	private String outputClipName;

	/** Screen width in pixels. */
	private int screenWidth = 1920;

	/** Screen height in pixels. */
	private int screenHeight = 1080;

	/**
	 * Optional text style for popup menu buttons. When {@code null}, defaults are resolved from {@code textStyle.*}
	 * properties in brts.conf (via {@link TextStyle#withDefaults()}).
	 */
	private TextStyle style;

	// -------------------------------------------------------------------------

	/**
	 * A single track entry for the popup menu.
	 */
	@Getter
	@Setter
	public static class TrackEntry {

		/**
		 * 1-based stream index within the playlist's stream numbering for the track type (audio or PG). This is the
		 * value passed to SET_STREAM.
		 */
		private int streamIndex;

		/** Human-readable display name for the button label. */
		private String displayName;

		/** ISO 639-2 language code (e.g. "eng", "fra"). */
		private String language;

	}

}
