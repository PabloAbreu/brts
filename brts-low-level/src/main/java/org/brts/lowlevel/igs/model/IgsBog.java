package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Button Overlap Group (BOG) — a set of mutually exclusive buttons.
 * Only one button in a BOG is valid (visible) at a time.
 */
@Getter
@Setter
@ToString
public class IgsBog {
    /** Default valid button id ref (16 bits). */
    private int defaultValidButtonIdRef;
    /** Buttons in this overlap group. */
    private List<IgsButton> buttons = new ArrayList<>();
}
