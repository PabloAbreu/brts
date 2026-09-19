package org.brts.lowlevel.model.mpls;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/mpls/SubPath.java' is part of BRTS.
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
 * SubPath — secondary path played in sync with the main PlayItem sequence. Used for secondary video/audio streams and
 * for out-of-mux PG/IG subtitles. Simplified support: only type and clip references are modeled.
 */
@Getter
@Setter
public class SubPath {

	/**
	 * SubPath type codes (Blu-ray spec table 5-18):
	 * <ul>
	 * <li>2 — Primary audio of Browse-able slideshow</li>
	 * <li>3 — Interactive graphics presentation menu</li>
	 * <li>4 — Text subtitle</li>
	 * <li>6 — Secondary video with secondary audio</li>
	 * <li>7 — Secondary audio of a Play item</li>
	 * </ul>
	 */
	private int subPathType;

	/** Indicates whether this SubPath is set to repeat. */
	@JsonProperty("isRepeatSubPath")
	private boolean isRepeatSubPath = false;

	/** List of SubPlayItems within this SubPath. */
	private List<SubPlayItem> subPlayItems;

	// -------------------------------------------------------------------------

	/** A single clip reference within a SubPath. */
	@Getter
	@Setter
	public static class SubPlayItem {

		/** Name of the clip referenced by this SubPlayItem. Example: "00001" */
		private String clipName;

		/** Connection condition for this SubPlayItem. */
		private int connectionCondition;

		/** In-time of this SubPlayItem within the clip (90 kHz ticks : TODO check that). */
		private long inTimeTicks;

		/** Out-time of this SubPlayItem within the clip (90 kHz ticks). */
		private long outTimeTicks;

		/** Sync reference to the main PlayItem (zero-based index). */
		private int syncPlayItemId;

		/**
		 * PTS value in the main PlayItem at which this SubPlayItem starts (90 kHz ticks).
		 */
		private long syncStartPtsTicks;

	}

}
