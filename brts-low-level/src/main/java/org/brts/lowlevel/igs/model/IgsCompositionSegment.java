package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Interactive Composition Segment (ICS) — the main segment in an IGS display set that defines the menu structure
 * (pages, buttons, navigation).
 */
@Getter
@Setter
@ToString
public class IgsCompositionSegment {

	/** PTS from the PES header (90 kHz ticks). */
	private long pts;

	/** DTS from the PES header (90 kHz ticks, -1 if absent). */
	private long dts = -1;

	/** Video descriptor (screen dimensions, frame rate). */
	private VideoDescriptor videoDescriptor;

	/** Composition descriptor (number + state). */
	private CompositionDescriptor compositionDescriptor;

	/** Sequence descriptor (first/last fragment). */
	private SequenceDescriptor sequenceDescriptor;

	/** The interactive composition data. */
	private IgsInteractiveComposition interactiveComposition;

}
