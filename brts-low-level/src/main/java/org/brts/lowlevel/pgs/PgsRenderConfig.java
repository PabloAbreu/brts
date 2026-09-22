package org.brts.lowlevel.pgs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/pgs/PgsRenderConfig.java' is part of BRTS.
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

import org.brts.common.utils.BrtsValue;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for PGS subtitle rendering.
 * <p>
 * All fields have sensible defaults for typical 1080p Blu-ray output. Override individual fields to customise the
 * appearance.
 */
@Getter
@Setter
@BrtsValue("pgs.render")
public class PgsRenderConfig {

	/** Screen width in pixels. */
	private Integer screenWidth;

	public int effectiveScreenWidth() {
		return screenWidth != null ? screenWidth : 1920;
	}

	/** Screen height in pixels. */
	private Integer screenHeight;

	public int effectiveScreenHeight() {
		return screenHeight != null ? screenHeight : 1080;
	}

	/** Font family name. */
	@BrtsValue
	private String fontName;

	/** Font size in points. */
	@BrtsValue
	private int fontSize;

	/**
	 * Primary text colour as ARGB (default: white, fully opaque).
	 */
	@BrtsValue
	private int fontColor;

	/**
	 * Outline/border colour as ARGB (default: black, fully opaque — provides legibility over varied backgrounds).
	 */
	@BrtsValue
	private int outlineColor;

	/**
	 * Outline stroke width in pixels.
	 */
	@BrtsValue
	private float outlineWidth;

	/**
	 * Default vertical position as a fraction of screen height (0.0 = top, 1.0 = bottom). The subtitle baseline is
	 * placed at this vertical offset. Default 0.90 places text near the bottom.
	 */
	@BrtsValue
	private double verticalPositionRatio;

	/**
	 * Horizontal margin in pixels (left and right). Text is wrapped or centred within
	 * {@code screenWidth - 2 * horizontalMargin}.
	 */
	@BrtsValue
	private int horizontalMargin;

	/**
	 * Frame rate code for the PGS video descriptor (default: 1 = 23.976 fps).
	 * <ul>
	 * <li>1 = 23.976</li>
	 * <li>2 = 24.000</li>
	 * <li>3 = 25.000</li>
	 * <li>4 = 29.970</li>
	 * <li>6 = 50.000</li>
	 * <li>7 = 59.940</li>
	 * </ul>
	 */
	private int frameRateCode = 1;

}
