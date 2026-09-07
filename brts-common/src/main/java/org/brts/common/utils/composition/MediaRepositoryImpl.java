package org.brts.common.utils.composition;

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
	public void close() throws Exception {
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
