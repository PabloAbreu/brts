package org.brts.lowlevel.titlemenu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Describes the background media source for a title menu. Supports either a video file (MKV/M2TS) or a static image
 * combined with a separate audio file.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackgroundSource {

	/**
	 * Path to a video file (MKV or M2TS) used as background. When set, both video and audio are extracted from this
	 * file.
	 */
	private String videoPath;

	/**
	 * Path to a static image file (PNG/JPG) used as background. Must be combined with {@link #audioPath} for audio.
	 * Mutually exclusive with {@link #videoPath}.
	 */
	private String imagePath;

	/**
	 * Path to a separate audio file used as background music when {@link #imagePath} is set. Supported formats depend
	 * on the muxer (typically AC3, DTS, LPCM elementary streams).
	 */
	private String audioPath;

	/**
	 * Audio stream type byte for the separate audio file (e.g. 0x81 for AC3). Required when {@link #audioPath} is set.
	 */
	private Integer audioStreamTypeByte;

	/** Returns true if this source uses a video file. */
	public boolean isVideo() {
		return videoPath != null && !videoPath.isBlank();
	}

	/** Returns true if this source uses a static image with separate audio. */
	public boolean isImageWithAudio() {
		return imagePath != null && !imagePath.isBlank();
	}

}
