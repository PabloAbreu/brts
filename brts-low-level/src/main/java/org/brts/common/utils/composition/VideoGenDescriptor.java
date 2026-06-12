package org.brts.common.utils.composition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Top-level JSON descriptor for the {@code low video-gen} CLI command.
 *
 * <p>
 * Combines an {@link ImagesComposition} recipe with optional {@link CompositedVideoGenerator.Config} generation
 * parameters and an optional output clip name.
 *
 * <p>
 * Example minimal descriptor (static base with auto-derived frame count from extra audio):
 *
 * <pre>
 * {
 *   "clipName": "00001",
 *   "composition": {
 *     "baseImageId": "background",
 *     "images": [
 *       { "imageId": "background", "sourcePath": "background.png" }
 *     ],
 *     "compositions": []
 *   },
 *   "config": {
 *     "fps": 24,
 *     "frameCount": 240,
 *     "extraAudioPath": "audio.ac3"
 *   }
 * }
 * </pre>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoGenDescriptor {

	/**
	 * 5-digit clip name written to the output {@code <clipName>.m2ts} and {@code <clipName>.clpi} files. Defaults to
	 * {@code "00001"} when absent.
	 */
	private String clipName;

	/**
	 * Images composition recipe. Required — describes the layers and base image/video to composite for each frame.
	 */
	private ImagesComposition composition;

	/**
	 * Generation parameters (fps, frameCount, resolution, bitrate, extra audio). All fields are optional; sensible
	 * defaults are applied by {@link CompositedVideoGenerator.Config} when absent.
	 */
	private CompositedVideoGenerator.Config config;

}
