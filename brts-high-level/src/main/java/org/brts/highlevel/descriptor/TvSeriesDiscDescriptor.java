package org.brts.highlevel.descriptor;

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

}
