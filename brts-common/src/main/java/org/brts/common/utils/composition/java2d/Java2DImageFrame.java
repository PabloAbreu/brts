package org.brts.common.utils.composition.java2d;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/java2d/Java2DImageFrame.java' is part of BRTS.
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
