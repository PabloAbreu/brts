/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/main/java/org/brts/highlevel/descriptor/MovieDiscDescriptor.java' is part of BRTS.
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

	/** The movie's main feature. */
	private MovieFeature mainFeature;

	/** Optional bonus tracks included on the disc. */
	private List<MovieFeature> bonusTracks;

	// -------------------------------------------------------------------------

	/**
	 * Represents a single movie feature on the disc, including the source MKV file, optional display label, and the
	 * audio and subtitle languages to include.
	 *
	 */
	@Getter
	@Setter
	public static class MovieFeature {

		/** Path to the source MKV file. */
		private String sourceMkv;

		/** Optional display label for this feature. */
		private String label;

		/** Audio languages to include, expressed as language codes. */
		private List<String> audioLanguages;

		/** Subtitle languages to include, expressed as language codes. */
		private List<String> subtitleLanguages;

	}

}
