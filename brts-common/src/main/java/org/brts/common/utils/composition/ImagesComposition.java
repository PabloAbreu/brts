package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/ImagesComposition.java' is part of BRTS.
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

import java.util.List;
import java.util.Map;

import org.brts.common.utils.expressions.ObjectExpression;

import lombok.Getter;
import lombok.Setter;

/**
 * Model for an images composition, including base image, overlay images, and optional transparency masks.
 */
@Getter
@Setter
public class ImagesComposition {

	/** Map of constant values used in the composition expressions. */
	private Map<String, ObjectExpression> constants;

	/** List of image references used in the composition. */
	private List<ImageReference> images;

	/** Optional ID of the base image; if not set, the first image in the list is used as base. */
	private String baseImageId;

	/**
	 * Optional composition canvas width; when set, the base image is scaled to it so that overlay coordinates are
	 * expressed in this space and not in the base image's native resolution.
	 */
	private Integer canvasWidth;

	/** Optional composition canvas height. */
	private Integer canvasHeight;

	/** List of image compositions; you might compose the same image multiple times with different transforms. */
	private List<ImageComposition> compositions;

	/**
	 * Optional list of 8-bit grayscale images used as transparency masks; each ImageComposition can reference one by
	 * maskImageId.
	 */
	private List<ImageReference> transparencyMasks;

}
