package org.brts.middle.menu.descriptor;

import lombok.Getter;
import lombok.Setter;

/**
 * Common base for audio/subtitle track selection menu items.
 * <p>
 * Consolidates the shared {@link #streamNumber} field used to select a physical track position, consistent across all
 * titles' source media.
 */
@Getter
@Setter
public abstract class StreamMenuItem extends MenuItem {

	/** 1-based stream number. See subclasses for the exact PSR mapping and semantics. */
	private int streamNumber;

}
