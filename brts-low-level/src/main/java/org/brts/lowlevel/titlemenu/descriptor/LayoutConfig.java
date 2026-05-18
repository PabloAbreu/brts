package org.brts.lowlevel.titlemenu.descriptor;

import org.brts.common.menu.TextStyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Layout configuration for title menu button arrangement.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LayoutConfig {

	/** Layout algorithm to use. Defaults to {@link LayoutType#TEXT_LIST}. */
	private LayoutType type = LayoutType.TEXT_LIST;

	/** Number of columns for button arrangement. Defaults to 1 for TEXT_LIST, 2 for THUMBNAIL_GRID. */
	private Integer columns;

	/** Top margin in pixels. */
	private Integer marginTop;

	/** Bottom margin in pixels. */
	private Integer marginBottom;

	/** Left margin in pixels. */
	private Integer marginLeft;

	/** Right margin in pixels. */
	private Integer marginRight;

	/** Horizontal spacing between buttons in pixels (for multi-column layouts). */
	private Integer spacingX;

	/** Vertical spacing between buttons in pixels. */
	private Integer spacingY;

	/** Global text style for title labels. Per-title overrides are merged over this. */
	private TextStyle titleStyle;

	// ── Thumbnail-specific ──────────────────────────────────────────────────

	/** Width of each thumbnail in pixels (THUMBNAIL_GRID only). Defaults to 320. */
	private Integer thumbnailWidth;

	/** Height of each thumbnail in pixels (THUMBNAIL_GRID only). Defaults to 180. */
	private Integer thumbnailHeight;

	/** Duration in seconds of the thumbnail extract segment. Defaults to 5. */
	private Double thumbnailDurationSec;

	/** Whether to show the title label below each thumbnail (THUMBNAIL_GRID). Defaults to true. */
	private Boolean showLabels;

	/**
	 * Maximum width in pixels of a rendered text button. When {@code null}, defaults to 75% of the screen width. Long
	 * display names that exceed this width are word-wrapped onto multiple lines.
	 */
	private Integer maxButtonWidth;

	// ── Defaults ────────────────────────────────────────────────────────────

	/** Returns the effective number of columns, applying defaults based on layout type. */
	public int effectiveColumns() {
		if (columns != null)
			return columns;
		return type == LayoutType.THUMBNAIL_GRID ? 2 : 1;
	}

	public int effectiveMarginTop() {
		return marginTop != null ? marginTop : 100;
	}

	public int effectiveMarginBottom() {
		return marginBottom != null ? marginBottom : 100;
	}

	public int effectiveMarginLeft() {
		return marginLeft != null ? marginLeft : 80;
	}

	public int effectiveMarginRight() {
		return marginRight != null ? marginRight : 80;
	}

	public int effectiveSpacingX() {
		return spacingX != null ? spacingX : 40;
	}

	public int effectiveSpacingY() {
		return spacingY != null ? spacingY : 60;
	}

	public int effectiveThumbnailWidth() {
		return thumbnailWidth != null ? thumbnailWidth : 320;
	}

	public int effectiveThumbnailHeight() {
		return thumbnailHeight != null ? thumbnailHeight : 180;
	}

	public double effectiveThumbnailDurationSec() {
		return thumbnailDurationSec != null ? thumbnailDurationSec : 5.0;
	}

	public boolean effectiveShowLabels() {
		return showLabels != null ? showLabels : true;
	}

	/**
	 * Returns the effective maximum button width in pixels. When {@link #maxButtonWidth} is set it is returned as-is;
	 * otherwise defaults to 75% of the given screen width.
	 *
	 * @param screenWidth the screen width in pixels (used only when {@link #maxButtonWidth} is {@code null})
	 */
	public int effectiveMaxButtonWidth(int screenWidth) {
		return maxButtonWidth != null ? maxButtonWidth : screenWidth * 3 / 4;
	}

}
