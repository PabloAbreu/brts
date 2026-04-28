package org.brts.lowlevel.descriptor;

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
 *       "outTimeTicks": 8100000,
 *       "streamPids": [4113, 4352, 4608]
 *     }
 *   ],
 *   "chapters": [
 *     { "playItemRef": 0, "markTimeTicks": 0 },
 *     { "playItemRef": 0, "markTimeTicks": 2700000 }
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

		/** Chapter mark time in 90 kHz ticks. */
		private long markTimeTicks;

	}

}
