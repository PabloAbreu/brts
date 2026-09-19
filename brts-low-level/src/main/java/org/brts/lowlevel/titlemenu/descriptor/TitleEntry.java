package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/descriptor/TitleEntry.java' is part of BRTS.
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

import org.brts.common.menu.TextStyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * A single title entry in the title menu descriptor.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TitleEntry {

	/** Title number used for the JUMP_TITLE navigation command (matches index.bdmv title table). */
	private int titleNumber;

	/** Display name shown on the menu button. */
	private String displayName;

	/**
	 * Path to the source media file for this title (MKV or M2TS). Required for THUMBNAIL_GRID layout to extract
	 * animated thumbnails. Optional for TEXT_LIST layout.
	 */
	private String sourceMediaPath;

	/**
	 * Time in seconds from which to extract the thumbnail segment. When {@code null}, defaults to the middle of the
	 * video (duration / 2) to avoid credits and fade-ins.
	 */
	private Double thumbnailExtractTimeSec;

	/**
	 * Whether the thumbnail animation should loop for the full background duration. When {@code false}, the thumbnail
	 * plays once then freezes on the last frame. Defaults to {@code true}.
	 */
	private Boolean thumbnailLoop;

	/** Optional per-title style override. Non-null fields are merged over the layout's titleStyle. */
	private TextStyle style;

	/** Optional explicit D-pad navigation overrides for this title's button. */
	private NavigationOverride nav;

	// ── Defaults ────────────────────────────────────────────────────────────

	/** Returns effective loop setting (defaults to true). */
	public boolean effectiveThumbnailLoop() {
		return thumbnailLoop != null ? thumbnailLoop : true;
	}

}
