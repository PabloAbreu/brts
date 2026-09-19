package org.brts.lowlevel.m2ts;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/m2ts/M2tsDumpTableFormatter.java' is part of BRTS.
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

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link M2tsDumpFormatter} that renders packet-group rows as a human-readable aligned table, optionally with ANSI
 * colors per PID.
 */
public class M2tsDumpTableFormatter implements M2tsDumpFormatter {

	private static final String DEFAULT_ANSI_COLOR = "\u001B[0m";

	private static final String[] ANSI_COLORS = { "\u001B[31m", // red
			"\u001B[32m", // green
			"\u001B[33m", // yellow
			"\u001B[34m", // blue
			"\u001B[35m", // magenta
			"\u001B[36m", // cyan
	};

	private final boolean ansiColors;

	private final Map<Integer, String> pidToColor = new HashMap<>();

	public M2tsDumpTableFormatter(M2tsInfo info, boolean ansiColors) {
		this.ansiColors = ansiColors;
		if (ansiColors && info.getStreams() != null) {
			int colorIndex = 0;
			for (M2tsStreamInfo s : info.getStreams()) {
				if (colorIndex < ANSI_COLORS.length) {
					pidToColor.put(s.getPid(), ANSI_COLORS[colorIndex++]);
				} else {
					pidToColor.put(s.getPid(), DEFAULT_ANSI_COLOR);
				}
			}
		}
	}

	@Override
	public void printHeader() {
		System.out.printf("%-10s %-10s %-10s %-12s %-6s  %-7s %-12s (ms)     %-12s (ms)     %-7s%n", "Packet#",
				"Nb Packets", "Pkts totl", "ATS", "PID", "Type", "PTS", "DTS", "PES LN");
	}

	@Override
	public void printRow(long groupStartPacket, long groupCount, long packetsSoFar, long groupAts, int pid,
			String streamType, long groupPts, long groupDts, long groupPesLength) {
		if (ansiColors) {
			String color = pidToColor.getOrDefault(pid, DEFAULT_ANSI_COLOR);
			System.out.print(color);
		}
		float ptsSeconds = convertPtsToSeconds(groupPts);
		float dtsSeconds = convertPtsToSeconds(groupDts);
		System.out.printf("%-10d %-10d %-10d %-12d 0x%04X  %-7s %-12d (%.3f) %-12d (%.3f) %-7d%n", groupStartPacket,
				groupCount, packetsSoFar, groupAts, pid, streamType, groupPts, ptsSeconds, groupDts, dtsSeconds,
				groupPesLength);
		if (ansiColors) {
			System.out.print(DEFAULT_ANSI_COLOR);
		}
	}

	@Override
	public void close() {
		// nothing to flush
	}

	private static float convertPtsToSeconds(long pts) {
		return pts / 90000f;
	}

}
