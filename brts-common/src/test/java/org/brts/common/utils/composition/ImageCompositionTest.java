package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/utils/composition/ImageCompositionTest.java' is part of BRTS.
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import org.brts.common.utils.composition.java2d.Java2DCompositionEngine;
import org.brts.common.utils.expressions.Expression;
import org.brts.common.utils.expressions.ObjectExpression;
import org.junit.jupiter.api.Test;

class ImageCompositionTest {

	private static final CompositionContext CONTEXT = new CompositionContext() {
		@Override
		public int getFrameNumber() {
			return 0;
		}

		@Override
		public Object eval(Expression expression) {
			return ((ObjectExpression) expression).getValue();
		}

		@Override
		public Path resolvePath(Path relativePath) {
			return relativePath;
		}
	};

	@Test
	void computesCropBoundsInSourcePixels() {
		ImageComposition composition = new ImageComposition();
		composition.setCrop(crop(10, 20, 640, 360));

		assertArrayEquals(new int[] { 10, 20, 640, 360 }, composition.computeCropBounds(CONTEXT, 1920, 1080));
	}

	@Test
	void rejectsCropOutsideSourceBounds() {
		ImageComposition composition = new ImageComposition();
		composition.setCrop(crop(100, 0, 101, 100));

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> composition.computeCropBounds(CONTEXT, 200, 100));
		assertEquals("crop [100, 0, 101, 100] exceeds source bounds 200x100", exception.getMessage());
	}

	@Test
	void cropsRequestedSourceRectangle() {
		BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
		source.setRGB(0, 0, 0xFFFF0000);
		source.setRGB(1, 0, 0xFF00FF00);
		source.setRGB(2, 0, 0xFF0000FF);
		source.setRGB(1, 1, 0xFFFFFFFF);

		Java2DCompositionEngine engine = new Java2DCompositionEngine();
		try (ImageFrame cropped = engine.crop(engine.fromBufferedImage(source), 1, 0, 2, 2)) {
			BufferedImage result = cropped.toBufferedImage();
			assertEquals(2, result.getWidth());
			assertEquals(2, result.getHeight());
			assertEquals(0xFF00FF00, result.getRGB(0, 0));
			assertEquals(0xFF0000FF, result.getRGB(1, 0));
			assertEquals(0xFFFFFFFF, result.getRGB(0, 1));
		}
	}

	private static ImageComposition.Crop crop(int x, int y, int width, int height) {
		ImageComposition.Point topLeft = new ImageComposition.Point();
		topLeft.setX(ObjectExpression.of(x));
		topLeft.setY(ObjectExpression.of(y));
		ImageComposition.Size size = new ImageComposition.Size();
		size.setWidth(ObjectExpression.of(width));
		size.setHeight(ObjectExpression.of(height));
		ImageComposition.Crop crop = new ImageComposition.Crop();
		crop.setTopLeft(topLeft);
		crop.setSize(size);
		return crop;
	}

}
