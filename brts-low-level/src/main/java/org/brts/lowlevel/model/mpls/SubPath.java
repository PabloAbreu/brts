package org.brts.lowlevel.model.mpls;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * SubPath — secondary path played in sync with the main PlayItem sequence. Used for
 * secondary video/audio streams and for out-of-mux PG/IG subtitles. Simplified support:
 * only type and clip references are modeled.
 */
@Getter
@Setter
public class SubPath {

	/**
	 * SubPath type codes (Blu-ray spec table 5-18):
	 * <ul>
	 * <li>2 — Primary audio of Browse-able slideshow</li>
	 * <li>3 — Interactive graphics presentation menu</li>
	 * <li>4 — Text subtitle</li>
	 * <li>6 — Secondary video with secondary audio</li>
	 * <li>7 — Secondary audio of a Play item</li>
	 * </ul>
	 */
	private int subPathType;

	@JsonProperty("isRepeatSubPath")
	private boolean isRepeatSubPath = false;

	private List<SubPlayItem> subPlayItems;

	// -------------------------------------------------------------------------

	/** A single clip reference within a SubPath. */
	@Getter
	@Setter
	public static class SubPlayItem {

		private String clipName;

		private long inTimeTicks;

		private long outTimeTicks;

		/** Sync reference to the main PlayItem (zero-based index). */
		private int syncPlayItemId;

		/**
		 * PTS value in the main PlayItem at which this SubPlayItem starts (90 kHz ticks).
		 */
		private long syncStartPtsTicks;

	}

}
