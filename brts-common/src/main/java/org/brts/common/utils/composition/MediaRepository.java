package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/** Repository for media assets */
public interface MediaRepository extends AutoCloseable {

	/**
	 * Retrieves video frames from the specified video file.
	 *
	 * @param videoPath the path to the video file
	 * @return a {@link VideoFrames} object fetching the extracted frames from the video
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
	 * @param imagePath the {@link Path} to the image file to be loaded
	 * @return a {@link BufferedImage} object representing the loaded image
	 * @throws IOException              if an I/O error occurs while reading the image file
	 * @throws IllegalArgumentException if the imagePath is null or invalid
	 */
	BufferedImage getStaticImage(Path imagePath);

}
