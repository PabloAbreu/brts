package org.brts.common.utils.composition;

import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.brts.common.utils.ImageUtils;

/**
 * {@link CompositionEngine} implementation backed by Java2D {@link java.awt.Graphics2D}.
 *
 * <p>
 * Delegates all operations to {@link ImageUtils} so that the existing behaviour is preserved exactly.
 */
public class Java2DCompositionEngine implements CompositionEngine {

	@Override
	public ImageFrame load(Path imagePath) {
		return new Java2DImageFrame(ImageUtils.create(imagePath));
	}

	@Override
	public ImageFrame fromBufferedImage(BufferedImage img) {
		return new Java2DImageFrame(img);
	}

	@Override
	public ImageFrame copy(ImageFrame src) {
		return new Java2DImageFrame(ImageUtils.copy(src.toBufferedImage()));
	}

	@Override
	public ImageFrame compose(ImageFrame background, ImageFrame overlay, AffineTransform transform, int x, int y,
			float opacity) {
		ImageUtils.compose(background.toBufferedImage(), overlay.toBufferedImage(), transform, x, y, opacity);
		return background;
	}

}
