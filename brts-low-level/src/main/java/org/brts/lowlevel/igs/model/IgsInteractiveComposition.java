package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Interactive Composition — the top-level structure inside an ICS (Interactive
 * Composition Segment). Holds stream/UI model, timeout values, and the full list of
 * interactive pages.
 */
@Getter
@Setter
@ToString
public class IgsInteractiveComposition {

	/**
	 * Stream model (1 bit):
	 * <ul>
	 * <li>0 = Out-Of-Mux — timestamps determine display</li>
	 * <li>1 = In-Mux — multiplexed with AV</li>
	 * </ul>
	 */
	private int streamModel;

	/**
	 * User interface model (1 bit):
	 * <ul>
	 * <li>0 = Always On — menu is always visible</li>
	 * <li>1 = Pop-Up — menu appears on user request</li>
	 * </ul>
	 */
	private int uiModel;

	/**
	 * Composition timeout PTS (33 bits, 90 kHz). Valid only when
	 * {@code streamModel == 0}.
	 */
	private long compositionTimeoutPts;

	/**
	 * Selection timeout PTS (33 bits, 90 kHz). Valid only when {@code streamModel == 0}.
	 */
	private long selectionTimeoutPts;

	/**
	 * User timeout duration in 90 kHz ticks (24 bits).
	 */
	private int userTimeoutDuration;

	/** Interactive pages. */
	private List<IgsPage> pages = new ArrayList<>();

}
