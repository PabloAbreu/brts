package org.brts.lowlevel.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/utils/composition/VideoGenDescriptor.java' is part of BRTS.
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

import org.brts.common.utils.composition.ImagesComposition;

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
