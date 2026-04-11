package org.brts.lowlevel.pgs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete PGS Display Set — the collection of all segments between a PCS (Presentation
 * Composition Segment) and the End-Of-Display marker.
 * <p>
 * A display set typically contains:
 * <ul>
 * <li>One PCS ({@link PgsCompositionSegment})</li>
 * <li>Zero or more PDS (Palette Definition Segments) — reuses {@link IgsPalette}</li>
 * <li>Zero or more WDS (Window Definition Segments) — reuses
 * {@link IgsWindowDefinition}</li>
 * <li>Zero or more ODS (Object Definition Segments) — reuses {@link IgsObject}</li>
 * <li>One End-Of-Display segment</li>
 * </ul>
 * PDS, ODS, WDS, and END segments are structurally identical between PGS and IGS, so we
 * reuse the IGS model classes directly.
 */
@Getter
@Setter
@ToString
public class PgsDisplaySet {

	/** Whether this display set starts a new epoch. */
	private boolean epochStart;

	/** The Presentation Composition Segment. */
	private PgsCompositionSegment compositionSegment;

	/** Palette definitions in this display set. */
	private List<IgsPalette> palettes = new ArrayList<>();

	/** Object (bitmap) definitions in this display set. */
	private List<IgsObject> objects = new ArrayList<>();

	/** Window definitions in this display set. */
	private List<IgsWindowDefinition> windowDefinitions = new ArrayList<>();

}
