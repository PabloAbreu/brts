package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/descriptor/LayoutConfig.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import org.brts.common.menu.TextStyle;
import org.brts.common.utils.BrtsFileConfig;

import java.util.Locale;

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

	/** Layout algorithm to use. Defaults to the configured title-menu layout type. */
	private LayoutType type;

	/**
	 * Number of columns for button arrangement. For TEXT_LIST this is the preferred minimum and more columns may be
	 * added to fit the available height. For THUMBNAIL_GRID it is fixed. Defaults to 1 and 2 respectively.
	 */
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

	/**
	 * Optional bounding box that constrains button placement. When set and valid, it replaces margin-based area
	 * computation entirely: buttons are laid out within the box's pixel rectangle.
	 */
	private BoundingBox boundingBox;

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

	private static final String CONFIG_PREFIX = "titleMenu.layout.";
	private static final String PROP_TYPE = CONFIG_PREFIX + "type";
	private static final String PROP_MARGIN_TOP = CONFIG_PREFIX + "marginTop";
	private static final String PROP_MARGIN_BOTTOM = CONFIG_PREFIX + "marginBottom";
	private static final String PROP_MARGIN_LEFT = CONFIG_PREFIX + "marginLeft";
	private static final String PROP_MARGIN_RIGHT = CONFIG_PREFIX + "marginRight";
	private static final String PROP_SPACING_X = CONFIG_PREFIX + "spacingX";
	private static final String PROP_SPACING_Y = CONFIG_PREFIX + "spacingY";
	private static final String PROP_THUMBNAIL_GRID_COLUMNS = CONFIG_PREFIX + "thumbnailGridColumns";
	private static final String PROP_TEXT_LIST_COLUMNS = CONFIG_PREFIX + "textListColumns";
	private static final String PROP_THUMBNAIL_WIDTH = CONFIG_PREFIX + "thumbnailWidth";
	private static final String PROP_THUMBNAIL_HEIGHT = CONFIG_PREFIX + "thumbnailHeight";
	private static final String PROP_THUMBNAIL_DURATION_SEC = CONFIG_PREFIX + "thumbnailDurationSec";
	private static final String PROP_SHOW_LABELS = CONFIG_PREFIX + "showLabels";
	private static final String PROP_MAX_BUTTON_WIDTH_RATIO = CONFIG_PREFIX + "maxButtonWidthRatio";
	private static final BrtsFileConfig CONFIG = BrtsFileConfig.getInstance();

	/** Returns the descriptor value, retaining TEXT_LIST as the legacy raw getter default. */
	public LayoutType getType() {
		return type != null ? type : LayoutType.TEXT_LIST;
	}

	/** Returns the effective layout type, using brts.conf when the descriptor omits it. */
	public LayoutType effectiveType() {
		if (type != null)
			return type;

		String configuredType = CONFIG.propertyOrDefault(PROP_TYPE, LayoutType.TEXT_LIST.name());
		try {
			return LayoutType.valueOf(configuredType.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return LayoutType.TEXT_LIST;
		}
	}

	/** Returns the preferred minimum (TEXT_LIST) or fixed (THUMBNAIL_GRID) number of columns. */
	public int effectiveColumns() {
		if (columns != null)
			return columns;
		boolean thumbs = effectiveType() == LayoutType.THUMBNAIL_GRID;
		return CONFIG.get((thumbs ? PROP_THUMBNAIL_GRID_COLUMNS : PROP_TEXT_LIST_COLUMNS), thumbs ? 2 : 1);
	}

	public int effectiveMarginTop() {
		return marginTop != null ? marginTop : CONFIG.get(PROP_MARGIN_TOP, 100);
	}

	public int effectiveMarginBottom() {
		return marginBottom != null ? marginBottom : CONFIG.get(PROP_MARGIN_BOTTOM, 100);
	}

	public int effectiveMarginLeft() {
		return marginLeft != null ? marginLeft : CONFIG.get(PROP_MARGIN_LEFT, 80);
	}

	public int effectiveMarginRight() {
		return marginRight != null ? marginRight : CONFIG.get(PROP_MARGIN_RIGHT, 80);
	}

	public int effectiveSpacingX() {
		return spacingX != null ? spacingX : CONFIG.get(PROP_SPACING_X, 40);
	}

	public int effectiveSpacingY() {
		return spacingY != null ? spacingY : CONFIG.get(PROP_SPACING_Y, 60);
	}

	public int effectiveThumbnailWidth() {
		return thumbnailWidth != null ? thumbnailWidth : CONFIG.get(PROP_THUMBNAIL_WIDTH, 320);
	}

	public int effectiveThumbnailHeight() {
		return thumbnailHeight != null ? thumbnailHeight : CONFIG.get(PROP_THUMBNAIL_HEIGHT, 180);
	}

	public double effectiveThumbnailDurationSec() {
		return thumbnailDurationSec != null ? thumbnailDurationSec : CONFIG.get(PROP_THUMBNAIL_DURATION_SEC, 5.0);
	}

	public boolean effectiveShowLabels() {
		return showLabels != null ? showLabels : CONFIG.get(PROP_SHOW_LABELS, true);
	}

	/**
	 * Returns the effective maximum button width in pixels. When {@link #maxButtonWidth} is set it is returned as-is;
	 * otherwise uses the configured fraction of the given screen width.
	 *
	 * @param screenWidth the screen width in pixels (used only when {@link #maxButtonWidth} is {@code null})
	 */
	public int effectiveMaxButtonWidth(int screenWidth) {
		if (maxButtonWidth != null)
			return maxButtonWidth;
		double ratio = CONFIG.get(PROP_MAX_BUTTON_WIDTH_RATIO, 0.75);
		if (ratio < 0.0 || ratio > 1.0)
			ratio = 0.75;
		return (int) (screenWidth * ratio);
	}

}
