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
    // you might compose the same image multiple times with different transforms
    private List<ImageComposition> compositions;
}