package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/ImageFrame.java' is part of BRTS.
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

import org.brts.common.utils.composition.java2d.Java2DImageFrame;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGenerator;
import org.brts.common.utils.composition.sources.video.VideoFrames;

/**
 * Implementation-independent handle to a single image in the composition pipeline.
 *
 * <p>
 * An {@code ImageFrame} may hold native resources (e.g. an OpenCV {@code Mat}). Callers that receive an <em>owned</em>
 * frame must call {@link #close()} when done. Frames returned by {@link VideoFrames#getFrame},
 * {@link MediaRepository#getStaticImage} and {@link SyntheticImageGenerator#generate} are <em>borrowed</em> (owned by
 * their respective cache); do <strong>not</strong> close them. Frames returned by {@link CompositionEngine#copy} and
 * {@link CompositionBuffer#compose} are <em>owned</em> by the caller.
 *
 * <p>
 * Implementations backed by a plain {@link BufferedImage} ({@link Java2DImageFrame}) have a no-op {@link #close()}.
 */
public interface ImageFrame extends AutoCloseable {

	int width();

	int height();

	/**
	 * Converts or unwraps the frame to a {@link BufferedImage} with {@code TYPE_INT_ARGB} pixel layout.
	 *
	 * <p>
	 * May allocate a new {@link BufferedImage} on each call for native-backed frames. The returned image remains valid
	 * after this frame is closed.
	 */
	BufferedImage toBufferedImage();

	/** Releases any native resources held by this frame. Safe to call multiple times. */
	@Override
	void close();

}
