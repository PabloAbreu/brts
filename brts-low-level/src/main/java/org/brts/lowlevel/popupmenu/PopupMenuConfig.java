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
		ABSOLUTE, SELECTABLE_BOUNDS, FULL_WIDTH_BOTTOM, FULL_HEIGHT_LEFT
	}

	/** Placement settings for one decorative background layer. */
	@Getter
	@Setter
	public static class BackgroundLayout {

		/** Background layout calculation mode. */
		private BackgroundLayoutMode mode = BackgroundLayoutMode.ABSOLUTE;

		/** Absolute rectangle origin : x coordinate */
		private int x;
		/** Absolute rectangle origin : y coordinate */
		private int y;

		/**
		 * Output size for absolute placement, banner width for full-height-left placement, or banner height for
		 * full-width-bottom placement.
		 */
		private Integer width;
		/** Output size : height */
		private Integer height;

		/**
		 * Margins added around selectable bounds; for a full-width bottom banner, top/bottom pad the configured height
		 * and left/right inset it horizontally; for a full-height left banner, left/right pad the configured width and
		 * top/bottom inset it vertically.
		 */
		private int marginTop;
		/** Rectangular margin on the right side */
		private int marginRight;
		/** Rectangular margin on the bottom side */
		private int marginBottom;
		/** Rectangular margin on the left side */
		private int marginLeft;

		/**
		 * Distance between the outer edge of a full-width/full-height banner and the bottom/left edge of the screen.
		 */
		private int edgeOffset;
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
