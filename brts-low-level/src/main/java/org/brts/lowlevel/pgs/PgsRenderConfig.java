package org.brts.lowlevel.pgs;

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
	private int screenWidth = 1920;

	/** Screen height in pixels. */
	private int screenHeight = 1080;

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
