package org.brts.common.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;

/**
 * Convenience utilities for managing external processes, including consuming their output and error streams.
 */
public class ProcessUtils {

	/**
	 * Swallows an InputStream in a separate thread, passing each line to the given consumer. Useful for consuming
	 * process output or error streams without blocking the main thread.
	 */
	@RequiredArgsConstructor
	public static class StreamGobbler extends Thread {

		private final InputStream input;

		protected Consumer<String> consumer = null;

		@Override
		public void run() {
			try (var reader = new BufferedReader(new InputStreamReader(input))) {
				String line;
				while ((line = reader.readLine()) != null) {
					consumer.accept(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * Specialized StreamGobbler that accumulates the output into a StringBuilder and returns it as a single string.
	 */
	public static class StringStreamGobbler extends StreamGobbler {

		private final StringBuilder output = new StringBuilder();

		public StringStreamGobbler(InputStream input) {
			super(input);
			consumer = s -> output.append(s).append("\n");
		}

		public String getOutput() {
			return output.toString();
		}

	}

	/**
	 * Finds the full path to an executable binary by searching the system PATH.
	 *
	 * @param binaryName
	 * @return the full path to the binary if found, or null if not found
	 */
	public static String findFullPath(String binaryName) {
		String pathEnv = System.getenv("PATH");
		String[] paths = pathEnv.split(System.getProperty("path.separator"));
		for (String path : paths) {
			Path fullPath = Paths.get(path, binaryName);
			if (Files.isExecutable(fullPath)) {
				return fullPath.toString();
			}
		}
		return null;
	}

	/**
	 * Returns the full command line to launch BRTS.
	 *
	 * @return the configured command to launch BRTS
	 */
	public static String getBrtsCommand() {
		return BrtsFileConfig.getInstance().propertyOrDefault("brts.command", "brts");
	}

}
