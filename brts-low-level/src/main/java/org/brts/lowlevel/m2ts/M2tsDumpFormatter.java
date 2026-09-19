package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/m2ts/M2tsDumpFormatter.java' is part of BRTS.
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

/**
 * Renders M2TS dump output in a specific format. Implementations decide how to present the header and each packet-group
 * row (e.g. human-readable table, CSV, …).
 */
public interface M2tsDumpFormatter {

	/**
	 * Prints the column header line(s) before any rows are emitted.
	 */
	void printHeader();

	/**
	 * Prints a single row representing a consecutive group of packets sharing the same PID.
	 *
	 * @param groupStartPacket index of the first packet in the group (0-based)
	 * @param groupCount       number of packets in this group
	 * @param packetsSoFar     cumulative packet count for this PID up to and including this group
	 * @param groupAts         ATS value of the first packet in the group (27 MHz ticks)
	 * @param pid              the PID shared by all packets in the group
	 * @param streamType       human-readable stream type label (e.g. "VIDEO", "AUDIO", "PAT", …)
	 * @param groupPts         PTS of the first PES in this group, or -1 if unavailable
	 * @param groupDts         DTS of the first PES in this group, or -1 if unavailable
	 * @param groupPesLength   PES_packet_length field value, or -1 if not a PES packet
	 */
	void printRow(long groupStartPacket, long groupCount, long packetsSoFar, long groupAts, int pid, String streamType,
			long groupPts, long groupDts, long groupPesLength);

	/**
	 * Called after the last row has been emitted. Implementations may flush buffered output or print a footer here.
	 */
	void close();

}
