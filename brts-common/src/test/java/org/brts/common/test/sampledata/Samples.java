package org.brts.common.test.sampledata;

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