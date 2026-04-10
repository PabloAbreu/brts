package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Window Definition — a rectangular region of the display where graphics are
 * composited.
 */
@Getter
@Setter
@ToString
public class IgsWindow {
    /** Window id (8 bits). */
    private int id;
    /** Horizontal position (16 bits). */
    private int x;
    /** Vertical position (16 bits). */
    private int y;
    /** Width in pixels (16 bits). */
    private int width;
    /** Height in pixels (16 bits). */
    private int height;
}
