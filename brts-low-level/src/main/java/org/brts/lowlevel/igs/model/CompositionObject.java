package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Composition object reference — maps a graphic object to a window at a
 * specific screen position. Used in both PCS and ICS effect definitions.
 */
@Getter
@Setter
@ToString
public class CompositionObject {
    /** Referenced object id (16 bits). */
    private int objectIdRef;
    /** Referenced window id (8 bits). */
    private int windowIdRef;
    /** Whether this object is forced on (1 bit). */
    private boolean forcedOn;
    /** Horizontal position (16 bits). */
    private int x;
    /** Vertical position (16 bits). */
    private int y;
    /** Whether cropping is enabled (1 bit). */
    private boolean cropFlag;
    /** Crop horizontal offset (valid when cropFlag=true). */
    private int cropX;
    /** Crop vertical offset. */
    private int cropY;
    /** Crop width. */
    private int cropWidth;
    /** Crop height. */
    private int cropHeight;
}
