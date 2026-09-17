package org.brts.common.utils.composition;

import java.util.List;
import java.util.Map;

import org.brts.common.utils.expressions.ObjectExpression;

import lombok.Getter;
import lombok.Setter;

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