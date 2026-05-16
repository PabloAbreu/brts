package org.brts.middle.descriptor;

import org.brts.lowlevel.titlemenu.descriptor.LayoutType;

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
	 * Path to a video file (MKV or M2TS) used as the menu background. Required.
	 */
	private String backgroundVideoPath;

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

}
