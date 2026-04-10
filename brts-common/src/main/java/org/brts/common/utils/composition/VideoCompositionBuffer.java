package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import lombok.RequiredArgsConstructor;

/** Manages composition buffers for a stream of frames */
@RequiredArgsConstructor
@Deprecated(since = "1.0", forRemoval = true)
public class VideoCompositionBuffer {
    private final MediaRepository mediaRepository = new MediaRepositoryImpl();
    private final ImagesComposition configuration;
    // CWD by default
    private Path basePath = Path.of("").toAbsolutePath();

    public VideoCompositionBuffer(ImagesComposition configuration, Path basePath) {
        this.configuration = configuration;
        this.basePath = basePath;
    }

    public CompositionBuffer createCompositionBuffer(int frameNumber) {
        CompositionContext context = createCompositionContext(frameNumber);
        return new CompositionBuffer(configuration, mediaRepository, context);
    }

    private CompositionContext createCompositionContext(int frameNumber) {
        return new CompositionContextImpl(frameNumber, configuration, basePath);
    }

    public BufferedImage composeFrame(int frameNumber) {
        CompositionBuffer buffer = createCompositionBuffer(frameNumber);
        return buffer.compose();
    }
}
