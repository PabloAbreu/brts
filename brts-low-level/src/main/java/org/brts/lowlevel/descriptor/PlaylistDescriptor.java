package org.brts.lowlevel.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/descriptor/PlaylistDescriptor.java' is part of BRTS.
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
 * JSON descriptor for generating an MPLS (playlist) file.
 * <p>
 * Example {@code 00001.playlist-descriptor.json}:
 *
 * <pre>{@code
 * {
 *   "playlistName": "00001",
 *   "playItems": [
 *     {
 *       "clipName": "00001",
 *       "inTimeTicks": 0,
 *       "outTimeTicks": 4050000,
 *       "streamPids": [4113, 4352, 4608]
 *     }
 *   ],
 *   "chapters": [
 *     { "playItemRef": 0, "markTimeTicks": 0 },
 *     { "playItemRef": 0, "markTimeTicks": 1350000 }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class PlaylistDescriptor {

	private String playlistName;

	@JsonProperty("isMenu")
	private boolean isMenu = false;

	private List<PlayItemDescriptor> playItems;

	private List<ChapterDescriptor> chapters;

	// -------------------------------------------------------------------------

	@Getter
	@Setter
	public static class PlayItemDescriptor {

		private String clipName;

		private long inTimeTicks;

		private long outTimeTicks;

		/** PIDs of elementary streams to include in the STN (stream number table). */
		private List<Integer> streamPids;

	}

	// -------------------------------------------------------------------------

	@Getter
	@Setter
	public static class ChapterDescriptor {

		private int playItemRef;

		/** Chapter mark time in 45 kHz ticks. */
		private long markTimeTicks;

	}

}
