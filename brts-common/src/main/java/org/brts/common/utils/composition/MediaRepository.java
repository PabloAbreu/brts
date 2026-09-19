package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/MediaRepository.java' is part of BRTS.
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

import java.nio.file.Path;
import java.util.Map;

import org.brts.common.utils.composition.sources.synth.SyntheticImageGenerator;
import org.brts.common.utils.composition.sources.video.VideoFrames;

/** Repository for media assets used during frame composition. */
public interface MediaRepository extends AutoCloseable {

	/**
	 * Retrieves video frames from the specified video file.
	 *
	 * @param videoPath the path to the video file
	 * @return a {@link VideoFrames} object for extracting frames from the video
	 */
	VideoFrames getVideoFrames(Path videoPath);

	/**
	 * Retrieves a synthetic image generator configured for the specified content.
	 *
	 * @param content the content used by the synthetic image generator
	 * @return a {@link SyntheticImageGenerator} instance configured with the provided content
	 */
	SyntheticImageGenerator getSyntheticImageGenerator(ImageReference.SyntheticImageSource content);

	/**
	 * Retrieves a static image from the specified file path.
	 *
	 * <p>
	 * The returned frame is <em>borrowed</em> (owned by an internal cache); do not close it.
	 *
	 * @param imagePath the {@link Path} to the image file to be loaded
	 * @return a borrowed {@link ImageFrame} representing the loaded image
	 */
	ImageFrame getStaticImage(Path imagePath);

	/**
	 * Retrieves a named image cache for storing resized images.
	 *
	 * @param cacheName the name of the cache
	 * @return a map representing the image cache
	 */
	Map<String, ImageFrame> getImageCache(String cacheName);

	void close();
}
