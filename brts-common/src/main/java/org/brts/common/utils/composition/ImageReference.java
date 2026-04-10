package org.brts.common.utils.composition;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImageReference {
    private String imageId;// in the case of a video, this is more the video id, not the id of a single
                           // image frame
    private String sourcePath;
    private String videoPath;
    // TODO might also be synthesized from a JSON description of shapes and text to
    // compose,
    // with an existing description format and an existing renderer (SVG ?)
    private String syntheticImage;

    public boolean isStatic() {
        return sourcePath != null && !sourcePath.isEmpty();
    }

    public boolean isVideo() {
        return videoPath != null && !videoPath.isEmpty();
    }

    public boolean isSynthetic() {
        return syntheticImage != null && !syntheticImage.isEmpty();
    }
}