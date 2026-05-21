package org.brts.common.utils.composition;

import lombok.Getter;
import lombok.Setter;

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

	@Getter
	@Setter
	public static class SyntheticImageSource {
		private String type; // example : SVG
		private String data; // example : inline SVG XML content
		private String srcPath; // example : path to SVG file on disk
		private Double frameRate; // fps for animated SVG (SMIL); null means static
	}
}