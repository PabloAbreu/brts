package org.brts.common.utils.composition;

public class SyntheticImageGeneratorFactory {

	public static SyntheticImageGenerator create(ImageReference.SyntheticImageSource content) {
		return new SVGImageGenerator(content);
	}

}
