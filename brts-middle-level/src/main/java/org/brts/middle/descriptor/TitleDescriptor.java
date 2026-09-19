package org.brts.middle.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/descriptor/TitleDescriptor.java' is part of BRTS.
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

import org.brts.common.validation.ExistingFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Middle-level descriptor for a single Blu-ray title.
 * <p>
 * A title descriptor is simpler than its low-level counterparts: the middle-level layer auto-parses the source MKV,
 * auto-assigns PIDs, and auto-generates the low-level CLPI + MPLS descriptors.
 * <p>
 * Example {@code title.json}:
 *
 * <pre>{@code
 * {
 *   "titleId": 1,
 *   "sourceMkv": "/videos/episode01.mkv",
 *   "audioLanguages": ["eng", "fra"],
 *   "subtitleLanguages": ["eng"],
 *   "chapters": [
 *     { "timeSeconds": 0.0 },
 *     { "timeSeconds": 420.0 },
 *     { "timeSeconds": 960.0 }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class TitleDescriptor {

	/**
	 * Unique identifier for this title within the Blu-ray disc. Must be positive.
	 *
	 * Example: 1
	 *
	 * This value will be found in navigation commands and menu references within the Blu-ray disc.
	 */
	@Positive
	private int titleId;

	/** Optional display name for menu buttons. Falls back to source MKV filename if absent. */
	private String displayName;

	/** Path to the source MKV file. */
	@NotBlank
	@ExistingFile
	private String sourceMkv;

	/**
	 * Ordered list of ISO 639-2 language codes for audio tracks to include. If null or empty, all audio tracks in the
	 * MKV are included.
	 */
	private List<@Pattern(regexp = LanguageCodeConstraints.PATTERN, message = LanguageCodeConstraints.MESSAGE) String> audioLanguages;

	/**
	 * Ordered list of ISO 639-2 language codes for subtitle (PG) tracks to include. If null or empty, all PG tracks in
	 * the MKV are included.
	 */
	private List<@Pattern(regexp = LanguageCodeConstraints.PATTERN, message = LanguageCodeConstraints.MESSAGE) String> subtitleLanguages;

	/** Chapter markers expressed as seconds from stream start. */
	private List<@Valid ChapterMarker> chapters;

	/**
	 * Controls popup menu generation for this title. Defaults to {@link PopupMenuMode#AUTO} which generates a popup
	 * menu if the title has more than one audio or more than one subtitle track.
	 */
	private PopupMenuMode popupMenu = PopupMenuMode.AUTO;

	// -------------------------------------------------------------------------

	/**
	 * Model for a single chapter marker within a title. Contains the time from the start of the stream and a label.
	 */
	@Getter
	@Setter
	public static class ChapterMarker {

		/** Time of the chapter marker expressed as seconds from the start of the stream. Must be zero or positive. */
		@PositiveOrZero
		private double timeSeconds;

		/** Optional label for the chapter marker. */
		private String label;

	}

}
