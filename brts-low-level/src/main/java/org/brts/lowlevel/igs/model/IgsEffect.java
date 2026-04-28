package org.brts.lowlevel.igs.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

/**
 * A visual effect — a timed palette + object composition used in page transition animations (in-effects and
 * out-effects).
 */
@Getter
@Setter
@ToString
public class IgsEffect {

	/** Duration in 90 kHz ticks (24 bits). */
	private int duration;

	/** Palette id reference (8 bits). */
	private int paletteIdRef;

	/** Composition objects used in this effect frame. */
	private List<CompositionObject> compositionObjects = new ArrayList<>();

}
