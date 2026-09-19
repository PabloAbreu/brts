package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/descriptor/BackgroundSource.java' is part of BRTS.
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
 * Describes the background media source for a title menu. Exactly one of {@link #videoPath}, {@link #imagePath}, or
 * {@link #composition} must be set.
 *
 * <ul>
 * <li>{@link #videoPath} — video file (MKV/M2TS); audio is extracted from it.</li>
 * <li>{@link #imagePath} — static image; rendered as a single-frame composition looped to fill
 * {@link #durationSeconds}. Optionally combined with {@link #audioPath}.</li>
 * <li>{@link #composition} — full {@link ImagesComposition} recipe rendered via {@code CompositedVideoGenerator}.
 * Optionally combined with {@link #audioPath}.</li>
 * </ul>
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
	 * Path to a static image file (PNG/JPG) used as background. Rendered via {@link ImagesComposition} (single static
	 * frame). Must be combined with {@link #durationSeconds} and optionally {@link #audioPath}. Mutually exclusive with
	 * {@link #videoPath} and {@link #composition}.
	 */
	private String imagePath;

	/**
	 * Full images-composition recipe rendered by {@code CompositedVideoGenerator}. Must be combined with
	 * {@link #durationSeconds} unless the composition's base image is a video (from which the frame count is derived).
	 * Mutually exclusive with {@link #videoPath} and {@link #imagePath}.
	 */
	private ImagesComposition composition;

	/**
	 * Duration in seconds for the rendered background. Required when {@link #imagePath} or {@link #composition} is set
	 * and the composition's base is a static image (no video to derive frame count from).
	 */
	private Double durationSeconds;

	/**
	 * Path to a separate audio elementary-stream file used as background music when {@link #imagePath} or
	 * {@link #composition} is set. Supported formats depend on the muxer (typically AC3, DTS, LPCM elementary streams).
	 * The stream type is detected automatically via FFmpeg.
	 */
	private String audioPath;

	/** Returns true if this source uses a video file. */
	public boolean isVideo() {
		return videoPath != null && !videoPath.isBlank();
	}

	/** Returns true if this source uses an {@link ImagesComposition} recipe. */
	public boolean isComposition() {
		return composition != null;
	}

	/** Returns true if this source uses a static image (rendered as a trivial composition). */
	public boolean isImage() {
		return imagePath != null && !imagePath.isBlank();
	}

}
