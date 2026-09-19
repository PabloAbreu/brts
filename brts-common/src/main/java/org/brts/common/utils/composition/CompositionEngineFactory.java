package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/CompositionEngineFactory.java' is part of BRTS.
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

import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.composition.java2d.Java2DCompositionEngine;
import org.brts.common.utils.composition.opencv.OpenCvCompositionEngine;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves and caches the active {@link CompositionEngine} singleton.
 *
 * <p>
 * The implementation is chosen once at first access via the {@code brts.composition.engine} property read from
 * {@link BrtsFileConfig}. Accepted values:
 * <ul>
 * <li>{@code java2d} – {@link Java2DCompositionEngine} (default)</li>
 * <li>{@code opencv} – {@link OpenCvCompositionEngine}</li>
 * </ul>
 */
@Slf4j
public final class CompositionEngineFactory {

	public static final String ENGINE_PROPERTY = "brts.composition.engine";

	public static final String ENGINE_JAVA2D = "java2d";

	public static final String ENGINE_OPENCV = "opencv";

	private static volatile CompositionEngine instance;

	private CompositionEngineFactory() {
	}

	/** Returns the active {@link CompositionEngine} singleton (created on first call). */
	public static CompositionEngine get() {
		if (instance == null) {
			synchronized (CompositionEngineFactory.class) {
				if (instance == null) {
					instance = create();
				}
			}
		}
		return instance;
	}

	private static CompositionEngine create() {
		String value = BrtsFileConfig.getInstance().getProperty(ENGINE_PROPERTY);
		if (ENGINE_OPENCV.equalsIgnoreCase(value)) {
			log.info("Composition engine: OpenCV (INTER_AREA)");
			return new OpenCvCompositionEngine();
		}
		if (value != null && !value.isBlank() && !ENGINE_JAVA2D.equalsIgnoreCase(value)) {
			log.warn("Unknown composition engine '{}', falling back to java2d", value);
		}
		log.info("Composition engine: Java2D");
		return new Java2DCompositionEngine();
	}
}
