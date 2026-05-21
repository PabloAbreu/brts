package org.brts.common.utils.composition;

import java.nio.file.Path;

public class Samples {
	public static Path root() {
		String sampleDir = System.getProperty("test.samples.dir");
		if (sampleDir == null || sampleDir.isEmpty()) {
			throw new IllegalStateException("System property 'test.samples.dir' is not set or empty");
		}
		return Path.of(sampleDir);
	}

	public static boolean samplesExist() {
		try {
			return root().toFile().exists();
		} catch (IllegalStateException e) {
			return false;
		}
	}

	public static Path sample(String relativePath) {
		return root().resolve(relativePath);
	}
}
