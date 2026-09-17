package org.brts.middle.descriptor;

import org.brts.common.validation.ExistingFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

	@Positive
	private int titleId;

	/** Optional display name for menu buttons. Falls back to source MKV filename if absent. */
	private String displayName;

	/** Path to the source MKV file. */
	@NotBlank
	@ExistingFile
	private String sourceMkv;

	/**
	 * Ordered list of ISO 639-2 language codes for audio tracks to include. If null or empty, all audio tracks in the
	 * MKV are included.
	 */
	private List<@Pattern(regexp = LanguageCodeConstraints.PATTERN, message = LanguageCodeConstraints.MESSAGE) String> audioLanguages;

	/**
	 * Ordered list of ISO 639-2 language codes for subtitle (PG) tracks to include. If null or empty, all PG tracks in
	 * the MKV are included.
	 */
	private List<@Pattern(regexp = LanguageCodeConstraints.PATTERN, message = LanguageCodeConstraints.MESSAGE) String> subtitleLanguages;

	/** Chapter markers expressed as seconds from stream start. */
	private List<@Valid ChapterMarker> chapters;

	/**
	 * Controls popup menu generation for this title. Defaults to {@link PopupMenuMode#AUTO} which generates a popup
	 * menu if the title has more than one audio or more than one subtitle track.
	 */
	private PopupMenuMode popupMenu = PopupMenuMode.AUTO;

	// -------------------------------------------------------------------------

	@Getter
	@Setter
	public static class ChapterMarker {

		@PositiveOrZero
		private double timeSeconds;

		private String label;

	}

}
