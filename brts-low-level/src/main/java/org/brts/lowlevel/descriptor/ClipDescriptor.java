package org.brts.lowlevel.descriptor;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * JSON descriptor for generating a single M2TS + CLPI file pair.
 * <p>
 * Example {@code 00001.clip-descriptor.json}: <pre>{@code
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
