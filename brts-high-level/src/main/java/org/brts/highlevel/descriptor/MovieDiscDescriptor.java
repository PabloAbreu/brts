package org.brts.highlevel.descriptor;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * High-level descriptor for a single movie Blu-ray disc.
 * <p>
 * Example {@code movie-disc.json}:
 *
 * <pre>{@code
 * {
 *   "templateType": "MOVIE",
 *   "discTitle": "My Movie",
 *   "outputDirectory": "/output/my-movie",
 *   "mainFeature": {
 *     "sourceMkv": "/videos/my-movie.mkv",
 *     "audioLanguages": ["eng", "fra"],
 *     "subtitleLanguages": ["eng"]
 *   },
 *   "bonusTracks": [
 *     { "sourceMkv": "/videos/making-of.mkv", "label": "Making Of" }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class MovieDiscDescriptor extends HighLevelDiscDescriptor {

	private MovieFeature mainFeature;

	private List<MovieFeature> bonusTracks;

	// -------------------------------------------------------------------------

	@Getter
	@Setter
	public static class MovieFeature {

		private String sourceMkv;

		private String label;

		private List<String> audioLanguages;

		private List<String> subtitleLanguages;

	}

}
