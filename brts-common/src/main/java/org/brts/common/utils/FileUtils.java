package org.brts.common.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtils {

	public static void deleteDir(Path dir) {
		try (var stream = Files.walk(dir)) {
			stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException ignored) {
				}
			});
		} catch (IOException ignored) {
		}
	}
}
