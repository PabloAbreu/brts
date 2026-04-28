package org.brts.lowlevel.subtitle.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A single subtitle cue — one timed block of text that appears on screen.
 * <p>
 * The text may contain basic inline HTML-like tags ({@code <b>}, {@code <i>}, {@code <u>}) which the renderer is
 * expected to interpret. Line breaks within a cue are represented by newline characters ({@code \n}).
 * <p>
 * Optional per-cue positioning allows override of the default placement. Both absolute (pixel) and screen-anchored
 * positioning are supported, depending on the source subtitle format.
 */
@Getter
@Setter
@ToString
public class SubtitleCue {

	/** Sequential cue number (1-based, informational). */
	private int number;

	/** Start time in milliseconds from the beginning of the stream. */
	private long startTimeMs;

	/** End time in milliseconds from the beginning of the stream. */
	private long endTimeMs;

	/**
	 * The subtitle text, potentially containing inline HTML tags ({@code <b>}, {@code <i>}, {@code <u>}) and newlines.
	 */
	private String text;

	/**
	 * Optional per-cue position override. When {@code null}, the renderer uses its default positioning (typically
	 * bottom-centre).
	 */
	private SubtitlePosition position;

	/**
	 * Convenience: duration of this cue in milliseconds.
	 */
	public long getDurationMs() {
		return endTimeMs - startTimeMs;
	}

}
