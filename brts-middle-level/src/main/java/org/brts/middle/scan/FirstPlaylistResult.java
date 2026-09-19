package org.brts.middle.scan;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/scan/FirstPlaylistResult.java' is part of BRTS.
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
 * Result of {@link FirstPlaylistFinder}: the first playlist that would be played according to the HDMV navigation
 * program, or a reason why none was found.
 */
@Getter
@Setter
public class FirstPlaylistResult {

	/** Whether a PLAY_PL/PLAY_PL_PI/PLAY_PL_PM command was reached. */
	private boolean found;

	/** The playlist number from the PLAY_PL* operand (null if not found). */
	private Integer playlistId;

	/** The play-item index from PLAY_PL_PI operand 2 (null if N/A). */
	private Integer playItemId;

	/** The play-mark index from PLAY_PL_PM operand 2 (null if N/A). */
	private Integer playMarkId;

	/** The specific PLAY command that was reached (e.g. "PLAY_PL", "PLAY_PL_PI"). */
	private String playCommand;

	/**
	 * Human-readable trace of each object/title transition. Example entries:
	 * <ul>
	 * <li>{@code "firstPlay → object 0"}</li>
	 * <li>{@code "object 0 → JUMP_TITLE 1 → object 3"}</li>
	 * <li>{@code "object 3 → PLAY_PL 100"}</li>
	 * </ul>
	 */
	private List<String> trace;

	/**
	 * Why simulation ended (e.g. "PLAY_PL", "BD_J_ENCOUNTERED", "MAX_DEPTH", "DEAD_END").
	 */
	private String terminationReason;

	/** Total number of instructions executed across all chained simulations. */
	private long totalStepsExecuted;

	/**
	 * Duration of the matched playlist in seconds, computed from PlayItem in/out ticks at 45 kHz. {@code null} when no
	 * playlist loader is configured or the MPLS file could not be read.
	 */
	private Long durationSeconds;

}
