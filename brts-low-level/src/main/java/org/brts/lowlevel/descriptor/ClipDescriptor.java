package org.brts.lowlevel.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/descriptor/ClipDescriptor.java' is part of BRTS.
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
 * JSON descriptor for generating a single M2TS + CLPI file pair.
 * <p>
 * Example {@code 00001.clip-descriptor.json}:
 *
 * <pre>{@code
 * {
 *   "clipName": "00001",
 *   "sourceMkv": "/path/to/movie.mkv",
 *   "tracks": [
 *     { "mkvTrackNumber": 1, "targetPid": 4113, "includeInPlaylist": true },
 *     { "mkvTrackNumber": 2, "targetPid": 4352, "language": "eng" }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class ClipDescriptor {

	private String clipName;

	private String sourceMkv;

	private List<TrackMapping> tracks;

	// -------------------------------------------------------------------------

	/** Maps one MKV track to a target Blu-ray PID. */
	@Getter
	@Setter
	public static class TrackMapping {

		/** MKV track number (1-based as reported by jebml). */
		private int mkvTrackNumber;

		/** Target PID to assign in the output M2TS TS packets. */
		private int targetPid;

		/** Override language tag (ISO 639-2). Null = take from MKV. */
		private String language;

		/**
		 * If false, this track is muxed into M2TS but excluded from the MPLS STN table.
		 */
		private boolean includeInPlaylist = true;

	}

}
