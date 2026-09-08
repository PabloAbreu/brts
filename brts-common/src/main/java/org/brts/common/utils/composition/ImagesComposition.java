package org.brts.common.utils.composition;

import java.util.List;
import java.util.Map;

import org.brts.common.utils.expressions.ObjectExpression;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImagesComposition {

	private Map<String, ObjectExpression> constants;

	private List<ImageReference> images;

	// optional, if not set the first image in the list is used as base
	private String baseImageId;

	// optional composition canvas size; when set, the base image is scaled to it so
	// that overlay coordinates are expressed in this space and not in the base image's
	// native resolution
	private Integer canvasWidth;

	private Integer canvasHeight;

	// you might compose the same image multiple times with different transforms
	private List<ImageComposition> compositions;

	// optional list of 8-bit grayscale images used as transparency masks;
	// each ImageComposition can reference one by maskImageId
	private List<ImageReference> transparencyMasks;

}