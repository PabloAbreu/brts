package org.brts.lowlevel.subtitle.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete subtitle track — an ordered list of {@link SubtitleCue}s plus optional
 * metadata from the source format.
 * <p>
 * This is the common intermediate representation produced by all subtitle parsers,
 * regardless of the source format (SRT, SSA/ASS, etc.).
 */
@Getter
@Setter
@ToString(exclude = "cues")
public class SubtitleTrack {

	/** The source format identifier (e.g. "SRT", "SSA", "ASS"). */
	private String format;

	/** Title / name of the subtitle track (from file metadata, if available). */
	private String title;

	/** Language code (e.g. "eng", "fra"), if known. */
	private String language;

	/** The ordered list of subtitle cues, sorted by start time. */
	private List<SubtitleCue> cues = new ArrayList<>();

	/**
	 * Convenience: total duration of the track in milliseconds (based on the last cue's
	 * end time).
	 */
	public long getDurationMs() {
		if (cues.isEmpty())
			return 0;
		return cues.get(cues.size() - 1).getEndTimeMs();
	}

}
