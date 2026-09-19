package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/MediaRepositoryImpl.java' is part of BRTS.
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
import java.util.HashMap;
import java.util.Map;

import org.brts.common.utils.CacheUtils;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGenerator;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGeneratorFactory;
import org.brts.common.utils.composition.sources.video.VideoFrames;
import org.brts.common.utils.composition.sources.video.VideoFramesFactory;

public class MediaRepositoryImpl implements MediaRepository {

	/**
	 * map of videoPath to VideoFrames instances, to "cache" loaded videos
	 */
	private final Map<Path, VideoFrames> videoCache = new HashMap<>();

	/** Cache for static images, by path */
	// FIXME : cap max size of the cache to avoid OOM
	private final Map<Path, ImageFrame> imageCache = new HashMap<>();

	/** Cache for synthetic image generators, by content */
	private final Map<String, SyntheticImageGenerator> syntheticCache = new HashMap<>();

	@Override
	public VideoFrames getVideoFrames(Path videoPath) {
		return videoCache.computeIfAbsent(videoPath, path -> {
			try {
				return VideoFramesFactory.create(path);
			} catch (Exception e) {
				throw new RuntimeException("Failed to load video frames for path: " + path, e);
			}
		});
	}

	@Override
	public SyntheticImageGenerator getSyntheticImageGenerator(ImageReference.SyntheticImageSource content) {
		final String cacheKey = content.getType() + ":"
				+ (content.getSrcPath() != null ? content.getSrcPath() : content.getData());
		return syntheticCache.computeIfAbsent(cacheKey, key -> SyntheticImageGeneratorFactory.create(content));
	}

	@Override
	public ImageFrame getStaticImage(Path imagePath) {
		return imageCache.computeIfAbsent(imagePath, p -> CompositionEngineFactory.get().load(p));
	}

	@Override
	@SuppressWarnings("PMD.EmptyCatchBlock")
	public void close() {
		// free all videoframes
		videoCache.values().forEach(vf -> {
			try {
				vf.close();
			} catch (Exception e) {
				// just ignore
			}
		});
		// free all cached static images
		imageCache.values().forEach(f -> f.close());
		imageCache.clear();
		// free all synthetic image generators
		syntheticCache.values().forEach(s -> {
			try {
				s.close();
			} catch (Exception e) {
				// just ignore
			}
		});
	}

	private final Map<String, Map<String, ImageFrame>> caches = new HashMap<>();

	@Override
	public Map<String, ImageFrame> getImageCache(String cacheName) {
		return caches.computeIfAbsent(cacheName, k -> CacheUtils.lruCache(20, ImageFrame::close));
	}
}
