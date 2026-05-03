package org.brts.lowlevel.titlemenu.descriptor;

import org.brts.common.menu.TextStyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * A single title entry in the title menu descriptor.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TitleEntry {

	/** Title number used for the JUMP_TITLE navigation command (matches index.bdmv title table). */
	private int titleNumber;

	/** Display name shown on the menu button. */
	private String displayName;

	/**
	 * Path to the source media file for this title (MKV or M2TS). Required for THUMBNAIL_GRID layout to extract
	 * animated thumbnails. Optional for TEXT_LIST layout.
	 */
	private String sourceMediaPath;

	/**
	 * Time in seconds from which to extract the thumbnail segment. When {@code null}, defaults to the middle of the
	 * video (duration / 2) to avoid credits and fade-ins.
	 */
	private Double thumbnailExtractTimeSec;

	/**
	 * Whether the thumbnail animation should loop for the full background duration. When {@code false}, the thumbnail
	 * plays once then freezes on the last frame. Defaults to {@code true}.
	 */
	private Boolean thumbnailLoop;

	/** Optional per-title style override. Non-null fields are merged over the layout's titleStyle. */
	private TextStyle style;

	/** Optional explicit D-pad navigation overrides for this title's button. */
	private NavigationOverride nav;

	// ── Defaults ────────────────────────────────────────────────────────────

	/** Returns effective loop setting (defaults to true). */
	public boolean effectiveThumbnailLoop() {
		return thumbnailLoop != null ? thumbnailLoop : true;
	}

}
