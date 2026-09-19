package org.brts.lowlevel.model.clpi;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/model/clpi/EpMap.java' is part of BRTS.
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
 * EP_map (Entry Point Map) — enables random access into an M2TS clip. Each {@link EpMapEntry} stores a coarse-grained
 * (SPN=Source Packet Number) anchor aligned to a keyframe, required for seek and chapter navigation.
 */
@Getter
@Setter
public class EpMap {

	/** One EP_map stream entry per video PID (usually just one). */
	private List<EpMapStream> streams;

	// -------------------------------------------------------------------------

	/** EP_map stream — groups all entry points for one elementary stream PID. */
	@Getter
	@Setter
	public static class EpMapStream {

		/** PID of the elementary stream this EP_map stream corresponds to. */
		private int pid;

		/** EP type: 1 = I-frame only (standard for video). */
		private int epType = 1;

		/** List of entry-point anchors within this EP_map stream. */
		private List<EpMapEntry> entries;

	}

	// -------------------------------------------------------------------------

	/**
	 * A single entry-point anchor within an EP_map stream. The combination of PTS + SPN uniquely identifies a seekable
	 * position.
	 */
	@Getter
	@Setter
	public static class EpMapEntry {

		/** Presentation Time Stamp in 90 kHz ticks. */
		private long ptsTicks;

		/** Source Packet Number (188-byte TS packet index from start of clip). */
		private long spn;

		/** True if this entry marks a multi-angle change point. */
		private boolean isAngleChangePoint;

		/**
		 * I-picture end position offset (3 bits, 0–7). Indicates the relative distance to the end of the I-picture in
		 * units of aligned units.
		 */
		private int iEndPositionOffset;

	}

}
