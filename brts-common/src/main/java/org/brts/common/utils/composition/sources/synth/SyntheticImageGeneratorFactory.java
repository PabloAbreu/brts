package org.brts.common.utils.composition.sources.synth;

import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.ImageReference.SyntheticImageSource;

public class SyntheticImageGeneratorFactory {

	public static SyntheticImageGenerator create(ImageReference.SyntheticImageSource content) {
		return new SVGImageGenerator(content);
	}

}
