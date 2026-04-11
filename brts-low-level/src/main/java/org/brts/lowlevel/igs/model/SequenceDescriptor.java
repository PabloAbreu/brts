package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Sequence descriptor — indicates whether a segment is the first or last fragment of a
 * multi-fragment object or composition.
 */
@Getter
@Setter
@ToString
public class SequenceDescriptor {

	/** True if this is the first fragment. */
	private boolean firstInSequence;

	/** True if this is the last fragment. */
	private boolean lastInSequence;

}
