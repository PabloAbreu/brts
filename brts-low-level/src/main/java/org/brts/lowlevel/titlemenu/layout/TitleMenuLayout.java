package org.brts.lowlevel.titlemenu.layout;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/layout/TitleMenuLayout.java' is part of BRTS.
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
import java.nio.file.Path;

import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;

/**
 * Strategy interface for title menu layout implementations.
 * <p>
 * Each implementation arranges title buttons on screen and produces rendered images for the three IGS states
 * (normal/selected/activated). Optionally, a layout may also produce an
 * {@link org.brts.common.utils.composition.ImagesComposition} descriptor for background video generation (e.g. when
 * animated thumbnails need to be composited into the background).
 */
public interface TitleMenuLayout {

	/**
	 * Computes the layout and renders button images.
	 *
	 * @param descriptor the title menu descriptor with titles, layout config, and screen dimensions
	 * @param baseDir    base directory for resolving relative paths in the descriptor
	 * @return layout result with positioned buttons and optional background composition
	 * @throws IOException on I/O errors during media access (e.g. thumbnail extraction)
	 */
	LayoutResult layout(TitleMenuDescriptor descriptor, Path baseDir) throws IOException;

}
