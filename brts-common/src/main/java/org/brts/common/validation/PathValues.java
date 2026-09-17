package org.brts.common.validation;

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
