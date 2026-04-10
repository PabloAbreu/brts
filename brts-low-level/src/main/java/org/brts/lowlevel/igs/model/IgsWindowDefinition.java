package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * Window Definition Segment (WDS) — carries one or more window definitions.
 */
@Getter
@Setter
@ToString
public class IgsWindowDefinition {
    /** PTS from the PES header (90 kHz ticks). */
    private long pts;
    /** Windows defined in this segment. */
    private List<IgsWindow> windows = new ArrayList<>();
}
