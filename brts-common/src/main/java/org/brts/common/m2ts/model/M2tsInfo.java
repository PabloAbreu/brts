package org.brts.common.m2ts.model;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/m2ts/model/M2tsInfo.java' is part of BRTS.
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

import java.util.List;
import java.util.Map;

/**
 * Top-level result of inspecting an M2TS file.
 * <p>
 * Produced by {@link org.brts.lowlevel.m2ts.M2tsParser} and serialisable to JSON so CLI users can inspect a file
 * without further tooling.
 */
@Getter
@Setter
@ToString
public class M2tsInfo {

	/** Absolute path to the parsed file. */
	private String sourcePath;

	/**
	 * Total number of 192-byte Source Packets in the file (i.e. file size / 192, rounded down).
	 */
	private long totalPackets;

	/**
	 * First arrival timestamp (ATS) seen in the TP_extra_header, in 27 MHz ticks. A value of {@code -1} means no
	 * packets were read.
	 */
	private long firstAts27MHz = -1;

	/**
	 * Last arrival timestamp (ATS) seen, in 27 MHz ticks.
	 */
	private long lastAts27MHz = -1;

	/**
	 * First PCR (Program Clock Reference) value seen, in 27 MHz ticks. {@code -1} if no PCR packet was found.
	 */
	private long firstPcr27MHz = -1;

	/**
	 * Last PCR value seen, in 27 MHz ticks.
	 */
	private long lastPcr27MHz = -1;

	/**
	 * Program Map Table PID (normally 0x100 = 256 on Blu-ray). Set to the first program's PMT PID found in the PAT.
	 */
	private int pmtPid = -1;

	/**
	 * All programs found in the PAT: program_number -> PMT PID. Program 0 (NIT) is excluded.
	 */
	private Map<Integer, Integer> programPidMap;

	/**
	 * PID of the Selection Information Table (SIT), as listed in the PAT. {@code -1} if no SIT entry was present.
	 */
	private int sitPid = -1;

	/**
	 * PCR PID reported by the PMT.
	 */
	private int pcrPid = -1;

	/**
	 * All elementary streams found in the PMT, in PMT order.
	 */
	private List<M2tsStreamInfo> streams;

	// -------------------------------------------------------------------------
	// Convenience helpers
	// -------------------------------------------------------------------------

	/**
	 * Duration derived from first/last PCR, converted to milliseconds. Returns {@code -1} if PCR data is not available.
	 */
	@Deprecated
	// because of wrap-around, this will never work as is
	public long __getDurationMs() {
		if (firstPcr27MHz < 0 || lastPcr27MHz < 0)
			return -1;
		long deltaTicks = lastPcr27MHz - firstPcr27MHz;
		return deltaTicks / 27_000; // 27 MHz → ms
	}

}
