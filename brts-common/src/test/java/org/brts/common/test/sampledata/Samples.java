package org.brts.common.test.sampledata;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/test/sampledata/Samples.java' is part of BRTS.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class Samples {

	private static final String PROPERTY_NAME = "test.samples.dir";

	private Samples() {
	}

	public static Path root() {
		return configuredRoot().orElseThrow(
				() -> new IllegalStateException("System property '" + PROPERTY_NAME + "' is not set or empty"));
	}

	public static Optional<Path> configuredRoot() {
		String sampleDir = System.getProperty(PROPERTY_NAME);
		if (sampleDir == null || sampleDir.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(Path.of(sampleDir));
	}

	public static boolean samplesExist() {
		return configuredRoot().map(Files::isDirectory).orElse(false);
	}

	public static Path sample(String relativePath) {
		return root().resolve(relativePath);
	}
}
