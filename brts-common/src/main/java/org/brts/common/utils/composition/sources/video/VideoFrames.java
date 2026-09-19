package org.brts.common.utils.composition.sources.video;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/sources/video/VideoFrames.java' is part of BRTS.
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

import java.io.IOException;

import org.brts.common.utils.composition.ImageFrame;

/**
 * Able to produce frames from a video.
 *
 * <p>
 * {@link #getFrame} may be called multiple times with the same frameNumber, or with frameNumbers that loop around the
 * total frame count. Returned frames are <em>borrowed</em> (owned by the implementation's cache); callers must
 * <strong>not</strong> call {@link ImageFrame#close()} on them.
 */
public interface VideoFrames extends AutoCloseable {

	int getFrameCount();

	double getFps();

	/**
	 * Returns a borrowed {@link ImageFrame} for the given frame index. Do not close the returned frame.
	 */
	ImageFrame getFrame(int frameNumber);

	void close() throws IOException;

}
