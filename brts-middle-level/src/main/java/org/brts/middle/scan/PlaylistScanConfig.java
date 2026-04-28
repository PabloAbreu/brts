package org.brts.middle.scan;

import lombok.Builder;
import lombok.Getter;

/**
 * Configuration for playlist scanning heuristics. All duration thresholds are in minutes.
 * <p>
 * Sensible defaults are provided; every parameter can be overridden via the CLI.
 */
@Getter
@Builder
public class PlaylistScanConfig {

	// ── Movie detection ─────────────────────────────────────────────────────

	/**
	 * Minimum duration (minutes) for a playlist to be considered a feature film. Default: 80 min.
	 */
	@Builder.Default
	private double movieMinMinutes = 80.0;

	/**
	 * When the disc is classified as MOVIE, any playlist whose duration is at least this fraction of the longest
	 * playlist's duration is considered an interesting alternate / director's cut. Default: 0.85 (85 %).
	 */
	@Builder.Default
	private double movieAlternateCutRatio = 0.85;

	// ── TV-series detection ─────────────────────────────────────────────────

	/**
	 * Minimum duration (minutes) for a playlist to be considered a TV-series episode. Default: 22 min.
	 */
	@Builder.Default
	private double episodeMinMinutes = 22.0;

	/**
	 * Maximum allowed ratio between the longest and shortest candidate episodes. If the ratio exceeds this, the
	 * candidates are not uniform enough to be auto-detected as a series. Default: 2.0.
	 */
	@Builder.Default
	private double episodeMaxDurationRatio = 2.0;

	/**
	 * Minimum number of playlists of similar duration to trigger TV-series auto-detection. Default: 2.
	 */
	@Builder.Default
	private int episodeMinCount = 2;

	// ── Forced type ─────────────────────────────────────────────────────────

	/**
	 * If non-null, forces the disc content type instead of auto-detecting.
	 */
	@Builder.Default
	private DiscContentType forcedType = null;

	/**
	 * If true, menu playlists (isMenu=true) are excluded from the results. Default: true.
	 */
	@Builder.Default
	private boolean excludeMenus = true;

	// ── Factory ─────────────────────────────────────────────────────────────

	/** Returns a config with all defaults. */
	public static PlaylistScanConfig defaults() {
		return PlaylistScanConfig.builder().build();
	}

}
