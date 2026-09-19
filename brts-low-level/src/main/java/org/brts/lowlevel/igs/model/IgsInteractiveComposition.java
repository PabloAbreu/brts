package org.brts.lowlevel.igs.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/model/IgsInteractiveComposition.java' is part of BRTS.
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
 * Interactive Composition — the top-level structure inside an ICS (Interactive Composition Segment). Holds stream/UI
 * model, timeout values, and the full list of interactive pages.
 */
@Getter
@Setter
@ToString
public class IgsInteractiveComposition {

	public static final int STREAM_MODEL_OUT_OF_MUX = 1;
	public static final int STREAM_MODEL_IN_MUX = 0;

	public static final int UI_MODEL_ALWAYS_ON = 0;
	public static final int UI_MODEL_POP_UP = 1;
	/**
	 * Stream model (1 bit):
	 * <ul>
	 * <li>1 = Out-Of-Mux — timestamps determine display</li>
	 * <li>0 = In-Mux — multiplexed with AV</li>
	 * </ul>
	 */
	private int streamModel;

	/**
	 * User interface model (1 bit):
	 * <ul>
	 * <li>0 = Always On — menu is always visible</li>
	 * <li>1 = Pop-Up — menu appears on user request</li>
	 * </ul>
	 */
	private int uiModel;

	/**
	 * Composition timeout PTS (33 bits, 90 kHz). useful only when {@code streamModel == 0}.
	 */
	private long compositionTimeoutPts;

	/**
	 * Selection timeout PTS (33 bits, 90 kHz). useful only when {@code streamModel == 0}.
	 */
	private long selectionTimeoutPts;

	/**
	 * User timeout duration in 90 kHz ticks (24 bits).
	 */
	private int userTimeoutDuration;

	/** Interactive pages. */
	private List<IgsPage> pages = new ArrayList<>();

}
