package org.brts.lowlevel.subtitle.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/subtitle/model/SubtitleCue.java' is part of BRTS.
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

/**
 * A single subtitle cue — one timed block of text that appears on screen.
 * <p>
 * The text may contain basic inline HTML-like tags ({@code <b>}, {@code <i>}, {@code <u>}) which the renderer is
 * expected to interpret. Line breaks within a cue are represented by newline characters ({@code \n}).
 * <p>
 * Optional per-cue positioning allows override of the default placement. Both absolute (pixel) and screen-anchored
 * positioning are supported, depending on the source subtitle format.
 */
@Getter
@Setter
@ToString
public class SubtitleCue {

	/** Sequential cue number (1-based, informational). */
	private int number;

	/** Start time in milliseconds from the beginning of the stream. */
	private long startTimeMs;

	/** End time in milliseconds from the beginning of the stream. */
	private long endTimeMs;

	/**
	 * The subtitle text, potentially containing inline HTML tags ({@code <b>}, {@code <i>}, {@code <u>}) and newlines.
	 */
	private String text;

	/**
	 * Optional per-cue position override. When {@code null}, the renderer uses its default positioning (typically
	 * bottom-centre).
	 */
	private SubtitlePosition position;

	/**
	 * Convenience: duration of this cue in milliseconds.
	 */
	public long getDurationMs() {
		return endTimeMs - startTimeMs;
	}

}
