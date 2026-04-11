package org.brts.common.utils.composition;

public class SyntheticImageGeneratorFactory {

	public static SyntheticImageGenerator create(String content) {
		return new SVGImageGenerator(content);
	}

}
