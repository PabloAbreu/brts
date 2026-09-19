package org.brts.highlevel.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-high-level/src/main/java/org/brts/highlevel/descriptor/TvSeriesDiscDescriptor.java' is part of BRTS.
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

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * High-level descriptor for a TV series Blu-ray disc.
 * <p>
 * Supports glob patterns or an explicit file list to auto-include episodes.
 * <p>
 * Example {@code series-disc.json}:
 *
 * <pre>{@code
 * {
 *   "templateType": "TV_SERIES",
 *   "discTitle": "My Series — Season 1",
 *   "outputDirectory": "/output/my-series-s01",
 *   "seriesName": "My Series",
 *   "seasonNumber": 1,
 *   "episodesGlob": "/videos/season1/*.mkv",
 *   "audioLanguages": ["eng"],
 *   "subtitleLanguages": ["eng", "fra"],
 *   "generateEpisodeMenu": true
 * }
 * }</pre>
 */
@Getter
@Setter
public class TvSeriesDiscDescriptor extends HighLevelDiscDescriptor {

	private String seriesName;

	private int seasonNumber;

	/**
	 * Glob pattern to discover episode MKV files. Files are sorted alphabetically; this order determines episode
	 * numbering. Mutually exclusive with {@link #episodeFiles}.
	 */
	private String episodesGlob;

	/**
	 * Explicit ordered list of episode MKV file paths. Takes precedence over {@link #episodesGlob} if both are
	 * specified.
	 */
	private List<String> episodeFiles;

	/** Audio languages to include from each episode (ISO 639-2). */
	private List<String> audioLanguages;

	/** Subtitle (PG) languages to include from each episode (ISO 639-2). */
	private List<String> subtitleLanguages;

	/** If true, an episode-selection menu is auto-generated. */
	private boolean generateEpisodeMenu = true;

	/** Path to a video file used as the title menu background. Required when generateEpisodeMenu is true. */
	private String menuBackgroundVideoPath;

}
