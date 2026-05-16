package org.brts.middle.descriptor;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Middle-level descriptor for a single Blu-ray title.
 * <p>
 * A title descriptor is simpler than its low-level counterparts: the middle-level layer auto-parses the source MKV,
 * auto-assigns PIDs, and auto-generates the low-level CLPI + MPLS descriptors.
 * <p>
 * Example {@code title.json}:
 *
 * <pre>{@code
 * {
 *   "titleId": 1,
 *   "sourceMkv": "/videos/episode01.mkv",
 *   "audioLanguages": ["eng", "fra"],
 *   "subtitleLanguages": ["eng"],
 *   "chapters": [
 *     { "timeSeconds": 0.0 },
 *     { "timeSeconds": 420.0 },
 *     { "timeSeconds": 960.0 }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class TitleDescriptor {

	private int titleId;

	/** Optional display name for menu buttons. Falls back to source MKV filename if absent. */
	private String displayName;

	/** Path to the source MKV file. */
	private String sourceMkv;

	/**
	 * Ordered list of ISO 639-2 language codes for audio tracks to include. If null or empty, all audio tracks in the
	 * MKV are included.
	 */
	private List<String> audioLanguages;

	/**
	 * Ordered list of ISO 639-2 language codes for subtitle (PG) tracks to include. If null or empty, all PG tracks in
	 * the MKV are included.
	 */
	private List<String> subtitleLanguages;

	/** Chapter markers expressed as seconds from stream start. */
	private List<ChapterMarker> chapters;

	// -------------------------------------------------------------------------

	@Getter
	@Setter
	public static class ChapterMarker {

		private double timeSeconds;

		private String label;

	}

}
