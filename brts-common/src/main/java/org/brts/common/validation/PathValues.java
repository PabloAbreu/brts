package org.brts.common.validation;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/validation/PathValues.java' is part of BRTS.
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

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the value types accepted by the BRTS filesystem constraints ({@link String}, {@link File}, {@link Path}).
 */
final class PathValues {

	private PathValues() {
		// utility class
	}

	/**
	 * Returns the value as a {@link Path}, or {@code null} when the value is absent, blank or not a supported type. A
	 * {@code null} result means "nothing to check" and the constraint must report success.
	 */
	static Path toPath(Object value) {
		try {
			return switch (value) {
			case null -> null;
			case Path path -> path;
			case File file -> file.toPath();
			case String s -> s.isBlank() ? null : Paths.get(s);
			default -> null;
			};
		} catch (InvalidPathException e) {
			return null;
		}
	}

	/** True when the value is present but cannot be interpreted as a filesystem path. */
	static boolean isMalformed(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof String s) {
			if (s.isBlank()) {
				return false;
			}
			try {
				Paths.get(s);
				return false;
			} catch (InvalidPathException e) {
				return true;
			}
		}
		return !(value instanceof Path || value instanceof File);
	}

}
