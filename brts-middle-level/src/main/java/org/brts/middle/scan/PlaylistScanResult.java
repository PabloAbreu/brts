package org.brts.middle.scan;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/scan/PlaylistScanResult.java' is part of BRTS.
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
import org.brts.lowlevel.model.mpls.MoviePlaylist;

import java.util.List;

/**
 * Result of scanning all playlists on a Blu-ray disc.
 * <p>
 * Contains the auto-detected (or forced) content type and a curated list of "interesting" playlists that most likely
 * represent the main content (feature film + alternate cuts, or TV-series episodes).
 */
@Getter
@Setter
public class PlaylistScanResult {

	/** Detected (or forced) content type of the disc. */
	private DiscContentType detectedType;

	/** Short human-readable summary of the detection logic. */
	private String summary;

	/** Curated list of playlists considered interesting. */
	private List<AnnotatedPlaylist> playlists;

	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * A parsed playlist enriched with computed metadata useful for display.
	 */
	@Getter
	@Setter
	public static class AnnotatedPlaylist {

		/** The original low-level parsed playlist. */
		private MoviePlaylist playlist;

		/** Total duration in seconds computed from PlayItems. */
		private double durationSeconds;

		/** Human-readable duration (HH:MM:SS). */
		private String durationFormatted;

		/**
		 * A hint about the role of this playlist (e.g. "main movie", "alternate cut", "episode").
		 */
		private String role;

		/** Source file name of the mpls file (e.g. "00001.mpls"). */
		private String fileName;

		@JsonProperty("isMenu")
		private boolean isMenu;

	}

}
