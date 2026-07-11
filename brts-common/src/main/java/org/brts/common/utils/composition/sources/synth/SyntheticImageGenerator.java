package org.brts.common.utils.composition.sources.synth;

import org.brts.common.utils.composition.ImageFrame;

/**
 * Produces synthetic images based on a description.
 *
 * <p>
 * Supports SVG content (inline or file-based) via {@link SVGImageGenerator}. The frame rate is needed for animated SVGs
 * (SMIL) to compute the snapshot time from the frame number.
 *
 * <p>
 * Returned frames are <em>borrowed</em>: callers must <strong>not</strong> close them.
 */
public interface SyntheticImageGenerator extends AutoCloseable {

	/**
	 * Returns a borrowed {@link ImageFrame} for the given frame number. Do not close the returned frame.
	 */
	ImageFrame generate(int frameNumber);

}