package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/descriptor/TitleMenuDescriptor.java' is part of BRTS.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Root descriptor for the title menu feature.
 * <p>
 * Describes the title entries, layout configuration, background media source, and output file names for generating a
 * Blu-ray title selection menu.
 *
 * <h2>Output structure</h2>
 * <ul>
 * <li>{@code <outputBackgroundName>.m2ts} + {@code .clpi} — background video (with optional music)</li>
 * <li>{@code <outputMenuName>.m2ts} + {@code .clpi} — out-of-mux IGS interactive graphics</li>
 * <li>{@code <outputPlaylistName>.mpls} — playlist referencing both via SubPath type 3</li>
 * </ul>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TitleMenuDescriptor {

	/** Screen width in pixels. Defaults to 1920. */
	private int screenWidth = 1920;

	/** Screen height in pixels. Defaults to 1080. */
	private int screenHeight = 1080;

	/** Background media source configuration. */
	private BackgroundSource backgroundMedia;

	/** Ordered list of title entries to display as menu buttons. */
	private List<TitleEntry> titles = new ArrayList<>();

	/** Layout configuration controlling button arrangement, style, and thumbnail parameters. */
	private LayoutConfig layout = new LayoutConfig();

	/** 5-digit output name for the background M2TS/CLPI (e.g. "00900"). */
	private String outputBackgroundName = "00900";

	/** 5-digit output name for the out-of-mux menu M2TS/CLPI (e.g. "00901"). */
	private String outputMenuName = "00901";

	/** 5-digit output name for the playlist MPLS (e.g. "00900"). */
	private String outputPlaylistName = "00900";

	/** Number of times the background clip is repeated in the playlist. Defaults to 50. */
	private int backgroundLoopCount = 50;

	/**
	 * Audio track selection items for the embedded settings submenu. When non-empty (together with
	 * {@link #subtitleItems}), a "Settings" button is added to the title menu leading to extra IGS pages.
	 */
	private List<StreamMenuItem> audioItems = new ArrayList<>();

	/** Subtitle track selection items for the embedded settings submenu. */
	private List<StreamMenuItem> subtitleItems = new ArrayList<>();

}
