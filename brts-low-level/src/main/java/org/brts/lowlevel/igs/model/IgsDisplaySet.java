package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete IGS Display Set — the collection of all segments between an ICS
 * (or PCS) and the End-Of-Display marker.
 * <p>
 * A display set typically contains:
 * <ul>
 *   <li>One ICS (Interactive Composition Segment)</li>
 *   <li>Zero or more PDS (Palette Definition Segments)</li>
 *   <li>Zero or more ODS (Object Definition Segments)</li>
 *   <li>Zero or more WDS (Window Definition Segments)</li>
 *   <li>One End-Of-Display segment</li>
 * </ul>
 */
@Getter
@Setter
@ToString
public class IgsDisplaySet {
    /** Whether this display set starts a new epoch. */
    private boolean epochStart;
    /** Whether the display set is complete (End-Of-Display received). */
    private boolean complete;
    /** The Interactive Composition Segment (non-null for IGS). */
    private IgsCompositionSegment compositionSegment;
    /** Palette definitions in this display set. */
    private List<IgsPalette> palettes = new ArrayList<>();
    /** Object (bitmap) definitions in this display set. */
    private List<IgsObject> objects = new ArrayList<>();
    /** Window definitions in this display set. */
    private List<IgsWindowDefinition> windowDefinitions = new ArrayList<>();
}
