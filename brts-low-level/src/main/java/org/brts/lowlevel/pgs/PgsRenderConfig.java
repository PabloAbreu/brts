package org.brts.lowlevel.pgs;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for PGS subtitle rendering.
 * <p>
 * All fields have sensible defaults for typical 1080p Blu-ray output.
 * Override individual fields to customise the appearance.
 */
@Getter
@Setter
public class PgsRenderConfig {

    /** Screen width in pixels. */
    private int screenWidth = 1920;

    /** Screen height in pixels. */
    private int screenHeight = 1080;

    /** Font family name. */
    private String fontName = "SansSerif";

    /** Font size in points. */
    private int fontSize = 48;

    /**
     * Primary text colour as ARGB (default: white, fully opaque).
     */
    private int fontColor = 0xFFFFFFFF;

    /**
     * Outline/border colour as ARGB (default: black, fully opaque —
     * provides legibility over varied backgrounds).
     */
    private int outlineColor = 0xFF000000;

    /**
     * Outline stroke width in pixels.
     */
    private float outlineWidth = 3.0f;

    /**
     * Default vertical position as a fraction of screen height (0.0 = top,
     * 1.0 = bottom).  The subtitle baseline is placed at this vertical
     * offset.  Default 0.90 places text near the bottom.
     */
    private double verticalPositionRatio = 0.90;

    /**
     * Horizontal margin in pixels (left and right).  Text is wrapped or
     * centred within {@code screenWidth - 2 * horizontalMargin}.
     */
    private int horizontalMargin = 120;

    /**
     * Frame rate code for the PGS video descriptor (default: 1 = 23.976 fps).
     * <ul>
     *   <li>1 = 23.976</li>
     *   <li>2 = 24.000</li>
     *   <li>3 = 25.000</li>
     *   <li>4 = 29.970</li>
     *   <li>6 = 50.000</li>
     *   <li>7 = 59.940</li>
     * </ul>
     */
    private int frameRateCode = 1;
}
