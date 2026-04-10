package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A single palette entry — one of up to 256 YCBCR+alpha colours.
 */
@Getter
@Setter
@ToString
public class PaletteEntry {
    /** Index in the palette (0–255). */
    private int entryId;
    /** Luminance. */
    private int y;
    /** Chroma red. */
    private int cr;
    /** Chroma blue. */
    private int cb;
    /** Transparency (0 = fully transparent, 255 = fully opaque). */
    private int alpha;
}
