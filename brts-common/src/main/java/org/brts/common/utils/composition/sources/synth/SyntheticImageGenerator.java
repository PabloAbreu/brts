package org.brts.common.utils.composition.sources.synth;

import java.util.Map;

import org.brts.common.utils.composition.ImageFrame;

/**
 * Produces synthetic images based on a description.
 *
 * <p>
 * Supports SVG content (inline or file-based) via {@link SVGImageGenerator}. The frame rate is needed for animated SVGs
 * (SMIL) to compute the snapshot time from the frame number.
 *
 * <p>
 * An optional data model may be supplied to {@link #generate(int, Map)}; for SVG sources, when non-null, the source
 * content is first rendered as a FreeMarker template (see {@code org.brts.common.template.TemplateRenderer}) before
 * being parsed.
 *
 * <p>
 * Returned frames are <em>borrowed</em>: callers must <strong>not</strong> close them.
 */
public interface SyntheticImageGenerator extends AutoCloseable {

	/**
	 * Returns a borrowed {@link ImageFrame} for the given frame number, with no template data model. Do not close the
	 * returned frame.
	 */
	default ImageFrame generate(int frameNumber) {
		return generate(frameNumber, null);
	}

	/**
	 * Returns a borrowed {@link ImageFrame} for the given frame number, optionally treating the source content as a
	 * template rendered with the given data model. Do not close the returned frame.
	 */
	ImageFrame generate(int frameNumber, Map<String, Object> dataModel);

}