package org.brts.common.utils.composition.java2d;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/java2d/Java2DCompositionEngine.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.brts.common.utils.ImageUtils;
import org.brts.common.utils.composition.CompositionEngine;
import org.brts.common.utils.composition.ImageFrame;

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
	public ImageFrame crop(ImageFrame source, int x, int y, int width, int height) {
		BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = cropped.createGraphics();
		graphics.drawImage(source.toBufferedImage(), 0, 0, width, height, x, y, x + width, y + height, null);
		graphics.dispose();
		return new Java2DImageFrame(cropped);
	}

	@Override
	public ImageFrame resize(ImageFrame source, int targetWidth, int targetHeight) {
		BufferedImage src = source.toBufferedImage();
		BufferedImage dst = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		// g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(src, 0, 0, targetWidth, targetHeight, null);
		g.dispose();
		return new Java2DImageFrame(dst);
	}

	@Override
	public ImageFrame compose(ImageFrame background, ImageFrame overlay, AffineTransform transform, int x, int y,
			float opacity, ImageFrame mask) {
		BufferedImage ovImg = overlay.toBufferedImage();
		if (mask != null) {
			ovImg = applyMask(ovImg, mask.toBufferedImage());
		}
		ImageUtils.compose(background.toBufferedImage(), ovImg, transform, x, y, opacity);
		return background;
	}

	/**
	 * Returns a new {@link BufferedImage} that is a copy of {@code overlay} with its alpha channel multiplied per-pixel
	 * by the corresponding grayscale value from {@code mask}.
	 *
	 * <p>
	 * The mask image is treated as an 8-bit grayscale source: the red channel (bits 16–23 of the ARGB int) is used as
	 * the grey value. If the mask dimensions differ from the overlay's, it is scaled to match via bilinear
	 * interpolation before sampling.
	 *
	 * @param overlay source overlay image (TYPE_INT_ARGB or compatible)
	 * @param mask    grayscale mask; its grey value (0–255) multiplies the overlay's per-pixel alpha
	 * @return new BufferedImage with the modified alpha channel
	 */
	private static BufferedImage applyMask(BufferedImage overlay, BufferedImage mask) {
		int w = overlay.getWidth();
		int h = overlay.getHeight();

		// Resize mask if dimensions differ
		BufferedImage scaledMask;
		if (mask.getWidth() != w || mask.getHeight() != h) {
			scaledMask = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = scaledMask.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(mask, 0, 0, w, h, null);
			g.dispose();
		} else {
			scaledMask = mask;
		}

		BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int ovArgb = overlay.getRGB(x, y);
				int origAlpha = (ovArgb >>> 24) & 0xFF;
				int maskGrey = (scaledMask.getRGB(x, y) >> 16) & 0xFF; // red == grey for greyscale source
				int newAlpha = (origAlpha * maskGrey) / 255;
				result.setRGB(x, y, (newAlpha << 24) | (ovArgb & 0x00FFFFFF));
			}
		}
		return result;
	}

}
