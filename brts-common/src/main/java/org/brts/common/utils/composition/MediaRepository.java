package org.brts.common.utils.composition;

import java.nio.file.Path;

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

}
