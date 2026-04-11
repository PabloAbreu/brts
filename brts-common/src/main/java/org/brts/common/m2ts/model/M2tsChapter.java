package org.brts.common.m2ts.model;

import lombok.Getter;
import lombok.Setter;

/**
 * A chapter (mark) within an M2TS clip, used both for MPLS PlayMark generation and for
 * EP_map anchor placement.
 */
@Getter
@Setter
public class M2tsChapter {

	/** Chapter index (0-based). */
	private int index;

	/**
	 * Chapter start presentation time in 90 kHz ticks (relative to the clip PTS origin).
	 */
	private long ptsTicks;

	/** Optional human-readable title / label. */
	private String title;

	public M2tsChapter() {
	}

	public M2tsChapter(int index, long ptsTicks) {
		this.index = index;
		this.ptsTicks = ptsTicks;
	}

	public M2tsChapter(int index, long ptsTicks, String title) {
		this.index = index;
		this.ptsTicks = ptsTicks;
		this.title = title;
	}

}
