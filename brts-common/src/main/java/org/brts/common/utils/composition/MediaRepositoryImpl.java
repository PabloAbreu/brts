package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.brts.common.utils.ImageUtils;

public class MediaRepositoryImpl implements MediaRepository {
    /**
     * map of videoPath to VideoFrames instances, to "cache" loaded videos
     * Note that VideoFrames instances do their own caching for frames
     */
    private final Map<Path, VideoFrames> videoCache = new HashMap<>();
    /** Cache for static images, by path */
    // FIXME : cap max size of the cache to avoid OOM
    private final Map<Path, BufferedImage> imageCache = new HashMap<>();
    /** Cache for synthetic image generators, by content */
    private final Map<String, SyntheticImageGenerator> syntheticCache = new HashMap<>();

    @Override
    public VideoFrames getVideoFrames(Path videoPath) {
        return videoCache.computeIfAbsent(videoPath, path -> {
            try {
                return new M2tsVideoFrames(path);
            } catch (Exception e) {
                throw new RuntimeException("Failed to load video frames for path: " + path, e);
            }
        });
    }

    @Override
    public SyntheticImageGenerator getSyntheticImageGenerator(Path dataPath) {
        throw new UnsupportedOperationException("Synthetic image generator loading from data path not implemented yet");
    }

    @Override
    public SyntheticImageGenerator getSyntheticImageGenerator(String content) {
        return syntheticCache.computeIfAbsent(content, SyntheticImageGeneratorFactory::create);
    }

    @Override
    public BufferedImage getStaticImage(Path imagePath) {
        return imageCache.computeIfAbsent(imagePath, ImageUtils::create);
    }

    @Override
    public void close() throws Exception {
        // free all videoframes
        videoCache.values().forEach(vf -> {
            try {
                vf.close();
            } catch (Exception e) {
                // just ignore
            }
        });
        // free all synthetic image generators
        syntheticCache.values().forEach(s -> {
            try {
                s.close();
            } catch (Exception e) {
                // just ignore
            }
        });
    }
}
