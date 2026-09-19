package org.brts.lowlevel.model.mpls;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/mpls/MoviePlaylist.java' is part of BRTS.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Model for an MPLS (Movie Playlist) file — {@code BDMV/PLAYLIST/XXXXX.mpls}.
 * <p>
 * An MPLS file describes:
 * <ul>
 * <li>An ordered list of {@link PlayItem}s, each referencing an M2TS clip</li>
 * <li>A list of {@link SubPath}s for secondary streams (audio, subtitles, menus shown during main playback) —
 * simplified support only</li>
 * <li>Chapter marks ({@link PlayMark}s)</li>
 * <li>Playback conditions (UI state, mnu flag, etc.)</li>
 * </ul>
 */
@Getter
@Setter
public class MoviePlaylist {

	/** Playlist number (5-digit, e.g. "00001"). */
	private String playlistName;

	/** If true, the playlist represents a menu (IG stream drives navigation). */
	@JsonProperty("isMenu")
	private boolean isMenu = false;

	/** Ordered list of play items, each referencing an M2TS clip. */
	private List<PlayItem> playItems;

	/** List of sub-paths for secondary streams (audio, subtitles, menus shown during main playback). */
	private List<SubPath> subPaths;

	/** List of chapter marks. */
	private List<PlayMark> playMarks;

}
