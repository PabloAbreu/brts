package org.brts.common.utils.composition;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.brts.common.utils.composition.java2d.Java2DCompositionEngine;
import org.brts.common.utils.composition.opencv.OpenCvCompositionEngine;

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
	 * Returns a new frame containing the specified source-space rectangle.
	 *
	 * <p>
	 * The returned frame is managed by the caller; call {@link ImageFrame#close()} when done. {@code source} is not
	 * modified and not closed.
	 */
	ImageFrame crop(ImageFrame source, int x, int y, int width, int height);

	/**
	 * Returns a new {@link ImageFrame} that is a scaled copy of {@code source} with the given dimensions.
	 *
	 * <p>
	 * The interpolation quality matches the engine (bicubic for Java2D, Lanczos-4 for OpenCV). The returned frame is
	 * owned by the caller; call {@link ImageFrame#close()} when done. {@code source} is not modified and not closed.
	 */
	ImageFrame resize(ImageFrame source, int targetWidth, int targetHeight);

	/**
	 * Composites {@code overlay} onto {@code background} using SRC_OVER alpha blending.
	 *
	 * <p>
	 * {@code transform} is applied to the overlay in its own coordinate space (scale + rotation around the overlay
	 * centre) before it is placed at background position ({@code x}, {@code y}). A global {@code opacity} in
	 * {@code [0,1]} further scales the overlay's alpha channel.
	 *
	 * <p>
	 * When {@code mask} is non-null it must be an 8-bit grayscale image (loaded from a grayscale PNG). Its pixel values
	 * are applied to the overlay's alpha channel <em>before</em> the affine warp: for each pixel {@code i},
	 * {@code effectiveAlpha[i] = overlayAlpha[i] * maskValue[i] / 255}. The mask is automatically resized to the
	 * overlay's dimensions when they differ. When {@code mask} is {@code null} the behaviour is unchanged.
	 *
	 * <p>
	 * {@code background} is modified in place and returned. The caller continues to own {@code background},
	 * {@code overlay}, and {@code mask}; this method does not close any of them.
	 *
	 * @param mask nullable grayscale mask; {@code null} means no mask
	 * @return {@code background} (same instance, modified)
	 */
	ImageFrame compose(ImageFrame background, ImageFrame overlay, AffineTransform transform, int x, int y,
			float opacity, ImageFrame mask);

}
