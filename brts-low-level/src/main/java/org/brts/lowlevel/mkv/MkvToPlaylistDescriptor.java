package org.brts.lowlevel.mkv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/mkv/MkvToPlaylistDescriptor.java' is part of BRTS.
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

import java.util.Set;

import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * JSON descriptor for the {@code mkv-to-playlist} CLI command, grouping all conversion input options.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MkvToPlaylistDescriptor {

	/** Path to the source MKV file. */
	private String input;

	/** 5-digit clip name for the generated M2TS/CLPI/MPLS. */
	private String clipName = "00001";

	/** MKV track numbers for audio to include. Null or empty means all audio tracks. */
	private Set<Integer> audioTracks;

	/** MKV track numbers for subtitles to include. Null or empty means all subtitle tracks. */
	private Set<Integer> subtitleTracks;

	/** PGS subtitle rendering configuration. Null falls back to defaults. */
	private PgsRenderConfig pgsConfig;

	/**
	 * Popup menu presentation configuration; a non-null value enables popup menu generation. Track-entry lists are
	 * ignored here since they are computed from the selected MKV tracks at conversion time.
	 */
	private PopupMenuConfig popupMenu;

}
