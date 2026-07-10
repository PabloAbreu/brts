package org.brts.common.utils.composition.java2d;

import java.awt.image.BufferedImage;

import org.brts.common.utils.composition.ImageFrame;

import lombok.RequiredArgsConstructor;

/**
 * {@link ImageFrame} backed by a plain {@link BufferedImage}. {@link #close()} is a no-op.
 */
@RequiredArgsConstructor
public class Java2DImageFrame implements ImageFrame {
	private final BufferedImage image;

	@Override
	public int width() {
		return image.getWidth();
	}

	@Override
	public int height() {
		return image.getHeight();
	}

	@Override
	public BufferedImage toBufferedImage() {
		return image;
	}

	/** No-op — {@link BufferedImage} has no native resources. */
	@Override
	public void close() {
	}
}
