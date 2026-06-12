package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.brts.common.utils.ImageUtils;

import lombok.Getter;

/**
 * Manages composition and caching of images for video composition operations.
 *
 * This class manages composition of several images into a single frame.
 *
 * <p>
 * This class handles loading and caching of three types of images:
 * <ul>
 * <li>Static images - cached BufferedImages loaded from file paths</li>
 * <li>Videos - cached video frames that can be accessed by frame number</li>
 * <li>Synthetic images - generated images produced by SyntheticImageGenerator</li>
 * </ul>
 *
 * <p>
 * The buffer uses lazy loading with caching to optimize performance when composing multiple frames. Video frames are
 * accessed with automatic looping when the requested frame number exceeds the total frame count.
 *
 * @author brt-common
 * @version 1.0
 */
public class CompositionBuffer {

	private final @Getter ImagesComposition configuration;

	private final MediaRepository mediaRepository;

	private final Map<String, ImageReference> references;

	private final CompositionContext context;

	private VideoFrames getVideoFrames(ImageReference ref) {
		return mediaRepository.getVideoFrames(resolvePath(ref.getVideoPath()));
	}

	private SyntheticImageGenerator getSyntheticGenerator(ImageReference ref) {
		return mediaRepository.getSyntheticImageGenerator(ref.getSyntheticImage());
	}

	public CompositionBuffer(ImagesComposition configuration, MediaRepository mediaRepository,
			CompositionContext context) {
		this.configuration = configuration;
		// if base image is not set, use the first image in the list as base
		if (configuration.getBaseImageId() == null && !configuration.getImages().isEmpty()) {
			configuration.setBaseImageId(configuration.getImages().get(0).getImageId());
		}
		this.mediaRepository = mediaRepository;
		this.context = context;
		references = configuration.getImages().stream().collect(HashMap::new, (m, r) -> m.put(r.getImageId(), r),
				HashMap::putAll);
	}

	private ImageReference getReference(String imageId) {
		return references.get(imageId);
	}

	private ImageReference getBackgroundReference() {
		String imageId = configuration.getBaseImageId();
		return getReference(imageId);
	}

	private BufferedImage getImage(ImageReference ref, int frameNumber) {
		if (ref.isStatic()) {
			return mediaRepository.getStaticImage(resolvePath(ref.getSourcePath()));
		} else if (ref.isVideo()) {
			VideoFrames videoFrames = getVideoFrames(ref);
			// loop video if frameNumber exceeds total frames
			return videoFrames.getFrame(frameNumber % videoFrames.getFrameCount());
		} else if (ref.isSynthetic()) {
			SyntheticImageGenerator generator = getSyntheticGenerator(ref);
			return generator.generate(frameNumber);
		}
		return null;
	}

	private Path resolvePath(String relativePath) {
		Path relative = Paths.get(relativePath);
		if (relative.isAbsolute())
			return relative;
		return context.resolvePath(relative);
	}

	public BufferedImage compose() {
		BufferedImage background = ImageUtils.copy(getImage(getBackgroundReference(), context.getFrameNumber()));
		for (ImageComposition ic : configuration.getCompositions()) {
			ImageReference ref = getReference(ic.getImageId());
			BufferedImage overlay = getImage(ref, context.getFrameNumber());
			double tlx = context.evalNumeric(ic.getTopLeft().getX());
			double tly = context.evalNumeric(ic.getTopLeft().getY());
			float opacity = (float) context.evalNumeric(ic.getOpacity(), 1.0);
			opacity = Math.max(0.0f, Math.min(1.0f, opacity)); // clamp to [0, 1]
			ImageUtils.compose(background, overlay,
					ic.toAffineTransform(context, overlay.getWidth(), overlay.getHeight()), (int) tlx, (int) tly,
					opacity);
		}
		return background;
	}

}
