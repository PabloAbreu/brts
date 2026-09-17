package org.brts.common.utils.composition;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a reference to an image, which can be a static image, a video, or a synthetic image.
 */
@Getter
@Setter
public class ImageReference {
	/**
	 * In the case of a video, this is more the video id, not the id of a single image frame.
	 */
	private String imageId;

	/** Path to the source image file. */
	private String sourcePath;

	/** Path to the video file if this reference is a video. */
	private String videoPath;

	/** Synthetic image source if this reference is a synthetic image. */
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
		/** Type of the synthetic image source (e.g., "svg"). */
		private String type;
		/** Data of the synthetic image source (e.g., inline SVG XML content). */
		private String data;
		/** Source path of the synthetic image source (e.g., path to SVG file on disk). */
		private String srcPath;
		/** Frame rate for animated synthetic images (e.g., animated SVG with SMIL). Null means static. */
		private Double frameRate;
	}
}