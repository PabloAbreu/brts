package org.brts.middle.scan;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Configuration for {@link FirstPlaylistFinder}.
 */
@Getter
@Builder
public class FirstPlaylistFinderConfig {

	/**
	 * Override the starting title number (1-based, matching JUMP_TITLE operand convention). {@code null} means start
	 * from the disc's first-play title entry.
	 */
	@Builder.Default
	private Integer startTitleNumber = null;

	/**
	 * Override the starting movie object index (0-based, into MovieObjects list). Takes precedence over
	 * {@link #startTitleNumber} if both are set. {@code null} means derive from the title entry.
	 */
	@Builder.Default
	private Integer startObjectId = null;

	/**
	 * Additional PSR values to merge on top of the built-in defaults. May be {@code null}.
	 */
	@Builder.Default
	private Map<Integer, Long> psrOverrides = null;

	/**
	 * Maximum number of movie-object transitions (JUMP/CALL) before aborting. Prevents infinite loops across objects.
	 * Default: 100.
	 */
	@Builder.Default
	private int maxChainDepth = 100;

	/**
	 * Maximum total steps (instruction executions) across all chained simulations. Default: 100,000.
	 */
	@Builder.Default
	private long maxTotalSteps = 100_000;

	/**
	 * Minimum playlist duration in seconds for a PLAY_PL* command to qualify as the final answer. Playlists shorter
	 * than this threshold are skipped and simulation continues. {@code null} means no minimum (any duration qualifies).
	 */
	@Builder.Default
	private Long minDurationSeconds = null;

	/**
	 * When {@code true}, stop at the first menu playlist (i.e. {@code MoviePlaylist.isMenu() == true}) even if it is
	 * shorter than {@link #minDurationSeconds}. Has no effect when no playlist loader is provided.
	 */
	@Builder.Default
	private boolean stopAtMenu = false;

}
