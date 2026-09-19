package org.brts.lowlevel.subtitle.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/subtitle/model/SubtitlePosition.java' is part of BRTS.
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
 * Per-cue positioning information for a subtitle.
 * <p>
 * Supports two modes:
 * <ul>
 * <li><b>Absolute</b>: pixel coordinates ({@link #x}, {@link #y}) — used when the source format provides explicit
 * {@code \pos(x,y)} tags (e.g. SSA/ASS).</li>
 * <li><b>Anchor-based</b>: screen region identified by {@link #alignment} (numpad-style 1–9, as used by SSA {@code \an}
 * tags and SRT positioning). The renderer maps this to an actual pixel position.</li>
 * </ul>
 * When {@link #absolutePosition} is {@code true}, the renderer uses ({@link #x}, {@link #y}) directly. Otherwise it
 * uses {@link #alignment}.
 */
@Getter
@Setter
@ToString
public class SubtitlePosition {

	/**
	 * Whether the position is specified as absolute pixel coordinates. If {@code false}, the {@link #alignment} field
	 * is used instead.
	 */
	private boolean absolutePosition;

	/** Absolute horizontal position in pixels (from the left). */
	private int x;

	/** Absolute vertical position in pixels (from the top). */
	private int y;

	/**
	 * Numpad-style alignment (1–9):
	 *
	 * <pre>
	 *   7  8  9   (top-left, top-centre, top-right)
	 *   4  5  6   (mid-left, mid-centre, mid-right)
	 *   1  2  3   (bottom-left, bottom-centre, bottom-right)
	 * </pre>
	 *
	 * Default is 2 (bottom-centre).
	 */
	private int alignment = 2;

	// ── Factory methods ─────────────────────────────────────────────────────

	public static SubtitlePosition absolute(int x, int y) {
		SubtitlePosition pos = new SubtitlePosition();
		pos.absolutePosition = true;
		pos.x = x;
		pos.y = y;
		return pos;
	}

	public static SubtitlePosition aligned(int alignment) {
		SubtitlePosition pos = new SubtitlePosition();
		pos.absolutePosition = false;
		pos.alignment = alignment;
		return pos;
	}

}
