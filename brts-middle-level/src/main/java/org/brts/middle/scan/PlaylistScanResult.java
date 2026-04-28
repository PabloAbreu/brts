package org.brts.middle.scan;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.brts.lowlevel.model.mpls.MoviePlaylist;

import java.util.List;

/**
 * Result of scanning all playlists on a Blu-ray disc.
 * <p>
 * Contains the auto-detected (or forced) content type and a curated list of "interesting" playlists that most likely
 * represent the main content (feature film + alternate cuts, or TV-series episodes).
 */
@Getter
@Setter
public class PlaylistScanResult {

	/** Detected (or forced) content type of the disc. */
	private DiscContentType detectedType;

	/** Short human-readable summary of the detection logic. */
	private String summary;

	/** Curated list of playlists considered interesting. */
	private List<AnnotatedPlaylist> playlists;

	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * A parsed playlist enriched with computed metadata useful for display.
	 */
	@Getter
	@Setter
	public static class AnnotatedPlaylist {

		/** The original low-level parsed playlist. */
		private MoviePlaylist playlist;

		/** Total duration in seconds computed from PlayItems. */
		private double durationSeconds;

		/** Human-readable duration (HH:MM:SS). */
		private String durationFormatted;

		/**
		 * A hint about the role of this playlist (e.g. "main movie", "alternate cut", "episode").
		 */
		private String role;

		/** Source file name of the mpls file (e.g. "00001.mpls"). */
		private String fileName;

		@JsonProperty("isMenu")
		private boolean isMenu;

	}

}
