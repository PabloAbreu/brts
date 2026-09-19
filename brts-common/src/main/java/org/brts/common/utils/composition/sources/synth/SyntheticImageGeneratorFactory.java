package org.brts.common.utils.composition.sources.synth;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/sources/synth/SyntheticImageGeneratorFactory.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import org.brts.common.utils.composition.ImageReference;

/**
 * Factory class for creating instances of SyntheticImageGenerator.
 *
 */
public class SyntheticImageGeneratorFactory {
	private static final String SYNTHETIC_TYPE_SVG = "svg";

	/**
	 * Creates a new instance of SyntheticImageGenerator based on the provided content.
	 *
	 * @param content the synthetic image source content used to create the generator
	 * @return a new instance of SyntheticImageGenerator based on the provided content
	 */
	public static SyntheticImageGenerator create(ImageReference.SyntheticImageSource content) {
		if (SYNTHETIC_TYPE_SVG.equals(content.getType())) {
			return new SVGImageGenerator(content);
		}
		throw new IllegalArgumentException("Unsupported synthetic image type: " + content.getType());
	}
}
