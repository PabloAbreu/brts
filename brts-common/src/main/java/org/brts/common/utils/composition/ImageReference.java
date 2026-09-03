package org.brts.common.utils.composition;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a reference to an image, which can be a static image, a video, or a synthetic image.
 */
@Getter
@Setter
public class ImageReference {
	// in the case of a video, this is more the video id, not the id of a single image frame
	private String imageId;

	private String sourcePath;

	private String videoPath;

	private SyntheticImageSource syntheticImage;

	public boolean isStatic() {
		return sourcePath != null && !sourcePath.isEmpty();
	}

	public boolean isVideo() {
		return videoPath != null && !videoPath.isEmpty();
	}

	public boolean isSynthetic() {
		return syntheticImage != null;
	}

	/**
	 * Represents a synthetic image source, which can be for example when type is "svg" an inline SVG, a file path to an
	 * SVG, or an animated SVG with a specified frame rate.
	 */
	@Getter
	@Setter
	public static class SyntheticImageSource {
		private String type; // example : SVG
		private String data; // example : inline SVG XML content
		private String srcPath; // example : path to SVG file on disk
		private Double frameRate; // fps for animated SVG (SMIL); null means static
	}
}