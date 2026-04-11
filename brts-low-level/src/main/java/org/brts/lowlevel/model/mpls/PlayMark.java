package org.brts.lowlevel.model.mpls;

import lombok.Getter;
import lombok.Setter;

/**
 * A chapter mark within an MPLS playlist. Each mark anchors a chapter to a specific PTS
 * within a PlayItem.
 */
@Getter
@Setter
public class PlayMark {

	/** Mark type: 0x01 = chapter mark, 0x02 = index mark. */
	private int markType = 0x01;

	/** Zero-based index into the PlayItem list. */
	private int playItemRef;

	/** Mark time in 90 kHz ticks (relative to clip start, i.e. absolute PTS). */
	private long markTimeTicks;

	/** Entry ES PID. Use 0xFFFF if not applicable. */
	private int entryEsPid = 0xFFFF;

	/** Duration of this mark in 90 kHz ticks (0 = to next mark). */
	private long durationTicks = 0;

}
