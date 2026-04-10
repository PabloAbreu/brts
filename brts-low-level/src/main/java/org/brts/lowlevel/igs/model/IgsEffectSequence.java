package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Effect sequence — a list of windows and timed effects used for page
 * in/out animations.
 */
@Getter
@Setter
@ToString
public class IgsEffectSequence {
    /** Windows used in this effect sequence. */
    private List<IgsWindow> windows = new ArrayList<>();
    /** Ordered list of effects (frames) in this animation. */
    private List<IgsEffect> effects = new ArrayList<>();
}
