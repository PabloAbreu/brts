package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/ProcessUtils.java' is part of BRTS.
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
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * Convenience utilities for managing external processes, including consuming their output and error streams.
 */
@Slf4j
public class ProcessUtils {

	private static final String AUTO = "auto";

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
		String command = BrtsFileConfig.getInstance().propertyOrDefault("brts.command", AUTO);
		// when set to AUTO, try to find brts on our own
		if (AUTO.equalsIgnoreCase(command)) {
			// first, try system PATH
			String fullPath = findFullPath("brts");
			if (fullPath != null) {
				System.out.println("Found brts in system PATH: " + fullPath);
				command = fullPath;
			} else {
				// now reconstruct the command used to launch the current Java process
				List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
				val javaHome = System.getProperty("java.home");
				val os = System.getProperty("os.name");
				val javaCommand = System.getProperty("sun.java.command");
				log.trace("JVM arguments: {}", jvmArgs);
				log.trace("Java home: {}", javaHome);
				log.trace("Reconstructing from Java command: {}", javaCommand);
				if (javaCommand != null && !javaCommand.isEmpty()) {
					Path java = Path.of(javaHome, "bin", os.startsWith("Windows") ? "java.exe" : "java");
					// this code only manages the jar case
					// when using a class folder, just override brts.command in brts.conf
					command = java.toString() + " " + String.join(" ", jvmArgs) + " -jar " + javaCommand.split(" ")[0];
					log.trace("	Reconstructed command: {}", command);
				}
				// if javaCommand is still null or empty, fallback to "brts"
				// it might be defined as an alias
				if (command == null || command.isEmpty()) {
					command = "brts";
				}
			}
		}
		return command;
	}

}
