package org.brts.common.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtils {

	public static void deleteDir(Path dir) {
		log.debug("Deleting directory {}", dir);
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

	// wrapper for Files.move that traces src/dst
	public static void move(Path src, Path dst) throws IOException {
		log.debug("Moving {} to {}", src, dst);
		Files.move(src, dst);
	}

	public static void createDirectories(Path dir) throws IOException {
		log.debug("Creating directory {}", dir);
		Files.createDirectories(dir);
	}
}
