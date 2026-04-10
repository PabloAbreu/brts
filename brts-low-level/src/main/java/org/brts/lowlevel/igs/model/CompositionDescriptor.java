package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Composition descriptor (shared by PCS and ICS).
 * Contains the composition number and state (epoch start, normal update, etc.).
 */
@Getter
@Setter
@ToString
public class CompositionDescriptor {
    /** 16-bit composition number. */
    private int number;
    /**
     * Composition state (2 bits):
     * <ul>
     *   <li>0 = normal</li>
     *   <li>1 = acquisition point</li>
     *   <li>2 = epoch start</li>
     * </ul>
     */
    private int state;
}
