package org.brts.common.utils.composition;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * Strategy interface for image loading and compositing operations.
 *
 * <p>
 * Two implementations are provided:
 * <ul>
 * <li>{@link Java2DCompositionEngine} – uses {@link java.awt.Graphics2D} with bicubic interpolation (legacy).</li>
 * <li>{@link OpenCvCompositionEngine} – uses OpenCV {@code warpAffine} with Lanczos-4 interpolation for higher quality
 * scaling and rotation.</li>
 * </ul>
 *
 * <p>
 * The active implementation is resolved via {@link CompositionEngineFactory#get()}.
 */
public interface CompositionEngine {

	/**
	 * Loads an image from the file system and returns it as an {@link ImageFrame}.
	 *
	 * <p>
	 * The returned frame is managed by the caller; call {@link ImageFrame#close()} when done.
	 */
	ImageFrame load(Path imagePath);

	/**
	 * Wraps or converts a {@link BufferedImage} into an {@link ImageFrame} suitable for this engine.
	 *
	 * <p>
	 * For the Java2D engine this is a zero-copy wrap. For the OpenCV engine this converts pixel data into a native Mat.
	 * The returned frame is managed by the caller.
	 */
	ImageFrame fromBufferedImage(BufferedImage img);

	/**
	 * Creates an independent deep copy of {@code src}.
	 *
	 * <p>
	 * The returned frame is managed by the caller.
	 */
	ImageFrame copy(ImageFrame src);

	/**
	 * Composites {@code overlay} onto {@code background} using SRC_OVER alpha blending.
	 *
	 * <p>
	 * {@code transform} is applied to the overlay in its own coordinate space (scale + rotation around the overlay
	 * centre) before it is placed at background position ({@code x}, {@code y}). A global {@code opacity} in
	 * {@code [0,1]} further scales the overlay's alpha channel.
	 *
	 * <p>
	 * {@code background} is modified in place and returned. The caller continues to own both {@code background} and
	 * {@code overlay}; this method does not close either.
	 *
	 * @return {@code background} (same instance, modified)
	 */
	ImageFrame compose(ImageFrame background, ImageFrame overlay, AffineTransform transform, int x, int y,
			float opacity);

}
