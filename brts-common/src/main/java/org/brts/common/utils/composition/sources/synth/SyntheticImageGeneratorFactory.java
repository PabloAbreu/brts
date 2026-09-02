package org.brts.common.utils.composition.sources.synth;

import org.brts.common.utils.composition.ImageReference;

/**
 * Factory class for creating instances of SyntheticImageGenerator.
 * 
 */
public class SyntheticImageGeneratorFactory {
	private static final String SYNTHETIC_TYPE_SVG = "svg";

	/**
	 * Creates a new instance of SyntheticImageGenerator based on the provided
	 * content.
	 * 
	 * @param content the synthetic image source content used to create the
	 *                generator
	 * @return a new instance of SyntheticImageGenerator based on the provided
	 *         content
	 */
	public static SyntheticImageGenerator create(ImageReference.SyntheticImageSource content) {
		if (SYNTHETIC_TYPE_SVG.equals(content.getType())) {
			return new SVGImageGenerator(content);
		}
		throw new IllegalArgumentException("Unsupported synthetic image type: " + content.getType());
	}
}
