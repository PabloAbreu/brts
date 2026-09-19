package org.brts.middle.menu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/descriptor/SetupMenuDescriptor.java' is part of BRTS.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Root descriptor for generating a Blu-ray setup/settings menu M2TS.
 * <p>
 * The descriptor references:
 * <ul>
 * <li>An intro background media source (video + audio)</li>
 * <li>A background media source (video + audio)</li>
 * <li>Screen dimensions</li>
 * <li>Global text style defaults</li>
 * <li>Menu item categories: audio, subtitles, misc navigation</li>
 * </ul>
 *
 * The intro is played once, then the menu loops with the background media.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * {
 *   "screenWidth": 1920,
 *   "screenHeight": 1080,
 *   "backgroundIntroMedia": { "type": "MKV", "file": "/path/to/bg_intro.mkv" },
 *   "backgroundMedia": { "type": "MKV", "file": "/path/to/bg.mkv" },
 *   "globalStyle": { "fontName": "SansSerif", "fontSize": 28, ... },
 *   "audioItems": [ ... ],
 *   "subtitleItems": [ ... ],
 *   "miscItems": [ ... ],
 *   "outputName": "00800"
 * }
 * }</pre>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class SetupMenuDescriptor {

	/** Screen width in pixels (default: 1920). */
	private int screenWidth = 1920;

	/** Screen height in pixels (default: 1080). */
	private int screenHeight = 1080;

	/** Background introduction media source descriptor. */
	private BackgroundMediaDescriptor backgroundIntroMedia;

	/** Background media source descriptor. */
	private BackgroundMediaDescriptor backgroundMedia;

	/** Global text style — applies to all items unless overridden. */
	private TextStyle globalStyle;

	/** Audio track selection items. */
	private List<AudioMenuItem> audioItems = new ArrayList<>();

	/** Subtitle track selection items. */
	private List<SubtitleMenuItem> subtitleItems = new ArrayList<>();

	/** Miscellaneous navigation items (launch movie, go back, etc.). */
	private List<MiscMenuItem> miscItems = new ArrayList<>();

	/**
	 * Base name for the output M2TS file for intro (5 digits, no extension). E.g. "00800".
	 */
	private String outputIntroName = "00800";

	/**
	 * Base name for the output M2TS file (5 digits, no extension). E.g. "00801".
	 */
	private String outputName = "00801";

	/**
	 * Base name for the output out-of-mux IGS M2TS file (5 digits, no extension). E.g. "00802".
	 */
	private String outputMenuName = "00802";

	/**
	 * Whether to mux the menu into the same M2TS file as the background.
	 *
	 * Unsupported for now : always out of mux.
	 */
	private boolean inMux = false;

	/**
	 * Base name for the output playlist file (5 digits, no extension). E.g. "00800".
	 */
	private String outputPlaylistName = "00800";

}
