package org.brts.lowlevel.model.mpls;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/mpls/PlayItem.java' is part of BRTS.
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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A PlayItem references one M2TS clip and defines the in/out point within that clip expressed in MPLS 45 kHz STC ticks.
 */
@Getter
@Setter
public class PlayItem {

	/** Clip name without extension (5-digit, e.g. "00001"). */
	private String clipName;

	/** Clip codec identifier (usually "M2TS"). */
	private int connectionCondition = 1;

	/** Is seamless angle change allowed? (always false for single-angle). */
	@JsonProperty("isMultiAngle")
	private boolean isMultiAngle = false;

	/** Presentation start time in 45 kHz ticks (in/out within the clip). */
	private long inTimeTicks;

	/** Presentation end time in 45 kHz ticks. */
	private long outTimeTicks;

	/** Stream-table — PIDs and attributes of streams to present from this clip. */
	private List<PlayItemStream> streams;

}
