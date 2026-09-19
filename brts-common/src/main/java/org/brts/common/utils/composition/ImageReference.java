package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/ImageReference.java' is part of BRTS.
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
