package org.brts.middle.scan;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Result of {@link FirstPlaylistFinder}: the first playlist that would be played
 * according to the HDMV navigation program, or a reason why none was found.
 */
@Getter
@Setter
public class FirstPlaylistResult {

	/** Whether a PLAY_PL/PLAY_PL_PI/PLAY_PL_PM command was reached. */
	private boolean found;

	/** The playlist number from the PLAY_PL* operand (null if not found). */
	private Integer playlistId;

	/** The play-item index from PLAY_PL_PI operand 2 (null if N/A). */
	private Integer playItemId;

	/** The play-mark index from PLAY_PL_PM operand 2 (null if N/A). */
	private Integer playMarkId;

	/** The specific PLAY command that was reached (e.g. "PLAY_PL", "PLAY_PL_PI"). */
	private String playCommand;

	/**
	 * Human-readable trace of each object/title transition. Example entries:
	 * <ul>
	 * <li>{@code "firstPlay → object 0"}</li>
	 * <li>{@code "object 0 → JUMP_TITLE 1 → object 3"}</li>
	 * <li>{@code "object 3 → PLAY_PL 100"}</li>
	 * </ul>
	 */
	private List<String> trace;

	/**
	 * Why simulation ended (e.g. "PLAY_PL", "BD_J_ENCOUNTERED", "MAX_DEPTH", "DEAD_END").
	 */
	private String terminationReason;

	/** Total number of instructions executed across all chained simulations. */
	private long totalStepsExecuted;

}
