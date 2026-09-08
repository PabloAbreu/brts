package org.brts.common.utils.composition;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGenerator;
import org.brts.common.utils.composition.sources.video.VideoFrames;
import org.bytedeco.javacpp.Loader;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class CompositionBuffer {
	static {
		try {
			String v = Loader.Detector.getPlatform();
			System.out.println("\n\nOpenCV platform: " + v + "\n\n");
		} catch (Throwable t) {
			log.warn("Failed to load OpenCV native libraries: {}", t.getMessage());
		}
	}

	private final @Getter ImagesComposition configuration;

	private final MediaRepository mediaRepository;

	private final Map<String, ImageReference> references;

	private final CompositionContext context;

	private final boolean twoStepComposition;

	private final Map<String, ImageFrame> resizedImageCache;

	private final Map<String, ImageReference> maskReferences;

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
		resizedImageCache = mediaRepository.getImageCache("resized");
		references = configuration.getImages().stream().collect(HashMap::new, (m, r) -> m.put(r.getImageId(), r),
				HashMap::putAll);
		maskReferences = configuration.getTransparencyMasks() != null ? configuration.getTransparencyMasks().stream()
				.collect(HashMap::new, (m, r) -> m.put(r.getImageId(), r), HashMap::putAll) : new HashMap<>();
		this.twoStepComposition = Boolean
				.parseBoolean(BrtsFileConfig.getInstance().getProperty("brts.composition.twoStep"));
		log.trace("twoStepComposition={}", this.twoStepComposition);
	}

	private ImageReference getReference(String imageId) {
		return references.get(imageId);
	}

	private ImageReference getBackgroundReference() {
		String imageId = configuration.getBaseImageId();
		return getReference(imageId);
	}

	private ImageFrame getImage(ImageReference ref, int frameNumber) {
		if (ref.isStatic()) {
			return mediaRepository.getStaticImage(resolvePath(ref.getSourcePath()));
		} else if (ref.isVideo()) {
			VideoFrames videoFrames = getVideoFrames(ref);
			// loop video if frameNumber exceeds total frames
			int count = videoFrames.getFrameCount();
			int frameIndex = count <= 0 ? frameNumber : frameNumber % videoFrames.getFrameCount();
			log.trace("Fetching video frame {} / {}", frameIndex, videoFrames.getFrameCount());
			return videoFrames.getFrame(frameIndex);
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

	public ImageFrame compose() {
		CompositionEngine engine = CompositionEngineFactory.get();
		// background is owned by this scope; overlays are borrowed (do not close)
		ImageFrame background = engine.copy(getImage(getBackgroundReference(), context.getFrameNumber()));
		for (ImageComposition ic : configuration.getCompositions()) {
			ImageReference ref = getReference(ic.getImageId());
			ImageFrame sourceOverlay = getImage(ref, context.getFrameNumber());
			int[] cropBounds = ic.computeCropBounds(context, sourceOverlay.width(), sourceOverlay.height());
			ImageFrame croppedOverlay = cropBounds == null ? null
					: engine.crop(sourceOverlay, cropBounds[0], cropBounds[1], cropBounds[2], cropBounds[3]);
			ImageFrame overlay = croppedOverlay == null ? sourceOverlay : croppedOverlay;
			double tlx = context.evalNumeric(ic.getTopLeft().getX());
			double tly = context.evalNumeric(ic.getTopLeft().getY());
			float opacity = (float) context.evalNumeric(ic.getOpacity(), 1.0);
			opacity = Math.max(0.0f, Math.min(1.0f, opacity)); // clamp to [0, 1]
			ImageFrame mask = resolveMask(ic);
			try {
				// twoStepComposition was an attempt to improve quality of resizing and rotation, but it seems to
				// have little discernible effect on quality, and it adds complexity and memory usage. So it is disabled
				// by
				// default.
				boolean resizedBeforeComposition = false;
				if (twoStepComposition && ic.getResize() != null) {
					overlay = getOrCreateResized(engine, ic, ref, overlay, cropBounds);
					resizedBeforeComposition = true;
				}
				engine.compose(background, overlay,
						ic.toAffineTransform(context, overlay.width(), overlay.height(), !resizedBeforeComposition),
						(int) tlx, (int) tly, opacity, mask);
			} finally {
				if (croppedOverlay != null) {
					croppedOverlay.close();
				}
			}
		}
		return background;
	}

	/**
	 * Returns a pre-resized overlay from the LRU cache, computing and caching it on first access.
	 *
	 * <p>
	 * Cache key encodes: imageId + whether the source is static or frame-specific + target dimensions. Static images
	 * always produce the same output for a given target size; video/synthetic frames are keyed by frame number.
	 */
	private ImageFrame getOrCreateResized(CompositionEngine engine, ImageComposition ic, ImageReference ref,
			ImageFrame overlay, int[] cropBounds) {
		int[] targetSize = ic.computeTargetSize(context, overlay.width(), overlay.height());
		int targetW = targetSize[0];
		int targetH = targetSize[1];
		String sourceKey = ref.isStatic() ? "s" : String.valueOf(context.getFrameNumber());
		String cropKey = cropBounds == null ? "full"
				: cropBounds[0] + "," + cropBounds[1] + "," + cropBounds[2] + "x" + cropBounds[3];
		String cacheKey = ic.getImageId() + "|" + sourceKey + "|" + cropKey + "|" + targetW + "x" + targetH;
		return resizedImageCache.computeIfAbsent(cacheKey, k -> {
			log.trace("Resizing overlay '{}' to {}x{} (cache miss, key={})", ic.getImageId(), targetW, targetH, k);
			return engine.resize(overlay, targetW, targetH);
		});
	}

	/**
	 * Returns the mask {@link ImageFrame} referenced by {@code ic.getMaskImageId()}, or {@code null} if no mask is
	 * configured. The returned frame is borrowed from the media repository and must not be closed by the caller.
	 */
	private ImageFrame resolveMask(ImageComposition ic) {
		if (ic.getMaskImageId() == null) {
			return null;
		}
		ImageReference maskRef = maskReferences.get(ic.getMaskImageId());
		if (maskRef == null) {
			throw new IllegalArgumentException(
					"maskImageId '" + ic.getMaskImageId() + "' not found in transparencyMasks list");
		}
		return getImage(maskRef, context.getFrameNumber());
	}

}
