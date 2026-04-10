package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Video descriptor found at the start of PCS / ICS segments.
 * Describes the target video dimensions and frame rate.
 */
@Getter
@Setter
@ToString
public class VideoDescriptor {
    private int width;
    private int height;
    /** Frame rate code (4 bits). */
    private int frameRateCode;
}
