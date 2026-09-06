package org.brts.middle.descriptor;

import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;
import org.brts.lowlevel.titlemenu.descriptor.BoundingBox;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.middle.menu.descriptor.AudioMenuItem;
import org.brts.middle.menu.descriptor.SubtitleMenuItem;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for automatic title menu generation in the middle-level orchestrator.
 * <p>
 * When present on a {@link DiscDescriptor} with more than one title, a title selection menu is generated using the
 * low-level {@code TitleMenuGenerator}. The menu is wired as both First Play and Top Menu so that the user sees it on
 * disc insertion and can return to it during playback.
 */
@Getter
@Setter
public class TitleMenuConfig {

	/**
	 * Background media source for the title menu. Supports video, static image, or composition. Required.
	 */
	private BackgroundSource backgroundSource;

	/**
	 * Optional explicit base directory for resolving relative paths in the background source. When {@code null},
	 * auto-derived from the first resolvable path in {@link #backgroundSource} (videoPath or imagePath parent).
	 */
	private String baseDir;

	/**
	 * Layout type for menu buttons. Defaults to {@link LayoutType#TEXT_LIST}.
	 */
	private LayoutType layoutType = LayoutType.TEXT_LIST;

	/**
	 * 5-digit output name for the background M2TS/CLPI. Defaults to "00900".
	 */
	private String outputBackgroundName = "00900";

	/**
	 * 5-digit output name for the IGS menu M2TS/CLPI. Defaults to "00901".
	 */
	private String outputMenuName = "00901";

	/**
	 * 5-digit output name for the menu playlist MPLS. Defaults to "00900".
	 */
	private String outputPlaylistName = "00900";

	/**
	 * Number of times the background clip loops in the menu playlist. Defaults to 50.
	 */
	private int backgroundLoopCount = 50;

	/**
	 * Optional bounding box constraining button placement. When set and valid, layout margins are ignored and buttons
	 * are placed within this pixel rectangle. When {@code null} (default), the full screen minus margins is used.
	 */
	private BoundingBox boundingBox;

	/**
	 * Optional title menu style override. When non-null, merged over the disc-wide {@link DiscDescriptor#getStyle()} to
	 * produce the effective style for title menu buttons. When {@code null}, the disc-wide style (or brts.conf
	 * defaults) is used directly.
	 */
	private TextStyle style;

	/**
	 * Audio track selection items for an embedded settings submenu on the title menu (raw 1-based stream numbers,
	 * consistent across all titles' source media). When non-empty (together with {@link #subtitleItems}), a "Settings"
	 * button is added to the generated title menu.
	 */
	private List<AudioMenuItem> audioItems = new ArrayList<>();

	/** Subtitle track selection items for the embedded settings submenu (0 = subtitles off). */
	private List<SubtitleMenuItem> subtitleItems = new ArrayList<>();

}
