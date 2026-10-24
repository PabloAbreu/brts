/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsShrinkInventory.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.brts.common.model.StreamCodingType;

/** Joined M2TS/CLPI stream inventory, including exact source transport-packet counts by PID. */
public record M2tsShrinkInventory(Path m2tsPath, Path clpiPath, long sourceBytes, long duration90k,
		List<Track> tracks) {

	public M2tsShrinkInventory {
		tracks = tracks == null ? List.of() : List.copyOf(tracks);
	}

	public Track track(int pid) {
		return tracks.stream().filter(track -> track.pid() == pid).findFirst().orElse(null);
	}

	public Map<Integer, Track> tracksByPid() {
		Map<Integer, Track> result = new LinkedHashMap<>();
		for (Track track : tracks) {
			result.put(track.pid(), track);
		}
		return Map.copyOf(result);
	}

	/** Byte size is exact for present source packets; it does not predict remux output overhead. */
	public record Track(int pid, StreamCodingType codingType, String language, Integer declaredBitrateKbps,
			long sourcePacketCount) {
		public long sourceTransportBytes() {
			return Math.multiplyExact(sourcePacketCount, 192L);
		}
	}
}
