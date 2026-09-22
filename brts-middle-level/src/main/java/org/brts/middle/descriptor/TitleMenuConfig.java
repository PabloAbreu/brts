package org.brts.middle.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/descriptor/TitleMenuConfig.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;
import org.brts.lowlevel.titlemenu.descriptor.BoundingBox;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.middle.menu.descriptor.AudioMenuItem;
import org.brts.middle.menu.descriptor.SubtitleMenuItem;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
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

	@JsonIgnore
	@AssertTrue(message = "title menu audioItems may contain at most one defaultStream")
	public boolean isAudioDefaultStreamConsistent() {
		return countDefaults(audioItems) <= 1;
	}

	@JsonIgnore
	@AssertTrue(message = "title menu subtitleItems may contain at most one defaultStream")
	public boolean isSubtitleDefaultStreamConsistent() {
		return countDefaults(subtitleItems) <= 1;
	}

	private static long countDefaults(List<? extends org.brts.middle.menu.descriptor.StreamMenuItem> items) {
		return items == null ? 0
				: items.stream().filter(org.brts.middle.menu.descriptor.StreamMenuItem::isDefaultStream).count();
	}

}
