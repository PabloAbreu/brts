package org.brts.common.utils.composition.sources.synth;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/sources/synth/SyntheticImageGenerator.java' is part of BRTS.
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
import java.util.Map;

import org.brts.common.utils.composition.ImageFrame;

/**
 * Produces synthetic images based on a description.
 *
 * <p>
 * Supports SVG content (inline or file-based) via {@link SVGImageGenerator}. The frame rate is needed for animated SVGs
 * (SMIL) to compute the snapshot time from the frame number.
 *
 * <p>
 * An optional data model may be supplied to {@link #generate(int, Map)}; for SVG sources, when non-null, the source
 * content is first rendered as a FreeMarker template (see {@code org.brts.common.template.TemplateRenderer}) before
 * being parsed.
 *
 * <p>
 * Returned frames are <em>borrowed</em>: callers must <strong>not</strong> close them.
 */
public interface SyntheticImageGenerator extends AutoCloseable {

	/**
	 * Returns a borrowed {@link ImageFrame} for the given frame number, with no template data model. Do not close the
	 * returned frame.
	 */
	default ImageFrame generate(int frameNumber) {
		return generate(frameNumber, null);
	}

	/**
	 * Returns a borrowed {@link ImageFrame} for the given frame number, optionally treating the source content as a
	 * template rendered with the given data model. Do not close the returned frame.
	 */
	ImageFrame generate(int frameNumber, Map<String, Object> dataModel);

	void close() throws IOException;

}
