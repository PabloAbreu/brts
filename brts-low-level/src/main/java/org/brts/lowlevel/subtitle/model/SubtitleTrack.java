package org.brts.lowlevel.subtitle.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/subtitle/model/SubtitleTrack.java' is part of BRTS.
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
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete subtitle track — an ordered list of {@link SubtitleCue}s plus optional metadata from the source format.
 * <p>
 * This is the common intermediate representation produced by all subtitle parsers, regardless of the source format
 * (SRT, SSA/ASS, etc.).
 */
@Getter
@Setter
@ToString(exclude = "cues")
public class SubtitleTrack {

	/** The source format identifier (e.g. "SRT", "SSA", "ASS"). */
	private String format;

	/** Title / name of the subtitle track (from file metadata, if available). */
	private String title;

	/** Language code (e.g. "eng", "fra"), if known. */
	private String language;

	/** The ordered list of subtitle cues, sorted by start time. */
	private List<SubtitleCue> cues = new ArrayList<>();

	/**
	 * Convenience: total duration of the track in milliseconds (based on the last cue's end time).
	 */
	public long getDurationMs() {
		if (cues.isEmpty())
			return 0;
		return cues.get(cues.size() - 1).getEndTimeMs();
	}

}
