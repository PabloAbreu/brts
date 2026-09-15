package org.brts.lowlevel.popupmenu;

import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.TextStyle;
import org.brts.common.utils.composition.ImageReference;

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

	/** Built-in button arrangements supported by popup menus. */
	public enum Layout {
		VERTICAL_LIST, HORIZONTAL_BOTTOM
	}

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

	/** Button arrangement, defaulting to the historical vertical list. */
	private Layout layout = Layout.VERTICAL_LIST;

	/** Optional ordered decorative layers for popup-menu pages. */
	private PageBackgrounds backgrounds;

	// -------------------------------------------------------------------------

	/** Background layers grouped by the page role on which they are displayed. */
	@Getter
	@Setter
	public static class PageBackgrounds {

		/** Layers displayed on every generated page. */
		private List<BackgroundLayer> shared = new ArrayList<>();

		/** Layers displayed with the root controls, including pages that clone those controls. */
		private List<BackgroundLayer> root = new ArrayList<>();

		/** Layers displayed on audio-selection pages. */
		private List<BackgroundLayer> audio = new ArrayList<>();

		/** Layers displayed on subtitle-selection pages. */
		private List<BackgroundLayer> subtitles = new ArrayList<>();
	}

	/** One decorative image layer and its destination layout. */
	@Getter
	@Setter
	public static class BackgroundLayer {

		/** Static raster or synthetic SVG image source. */
		private ImageReference source;

		/** Placement and stretched output dimensions. */
		private BackgroundLayout layout;
	}

	/** Supported ways to calculate a background layer's destination rectangle. */
	public enum BackgroundLayoutMode {
		ABSOLUTE, SELECTABLE_BOUNDS, FULL_WIDTH_BOTTOM
	}

	/** Placement settings for one decorative background layer. */
	@Getter
	@Setter
	public static class BackgroundLayout {

		private BackgroundLayoutMode mode = BackgroundLayoutMode.ABSOLUTE;

		/** Absolute rectangle origin. */
		private int x;
		private int y;

		/** Output size for absolute placement, or banner height for full-width-bottom placement. */
		private Integer width;
		private Integer height;

		/** Margins added around selectable bounds. */
		private int marginTop;
		private int marginRight;
		private int marginBottom;
		private int marginLeft;

		/** Distance between a full-width banner and the bottom of the screen. */
		private int bottomOffset;
	}

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
