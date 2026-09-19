package org.brts.cli;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/LevelDispatcher.java' is part of BRTS.
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

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Registry and dispatcher for {@link FeatureRunner} instances within a single level (low / mid / high).
 */
@RequiredArgsConstructor
public class LevelDispatcher {

	/** the human-readable level name (e.g. "low"). */
	private final @Getter String levelName;

	private final Map<String, FeatureRunner<?>> runners = new LinkedHashMap<>();

	/** Registers a runner. The command name is taken from the runner itself. */
	public LevelDispatcher register(FeatureRunner<?> runner) {
		runners.put(runner.getCommandName(), runner);
		return this;
	}

	/** Returns all registered runners in registration order. */
	public Collection<FeatureRunner<?>> getRunners() {
		return Collections.unmodifiableCollection(runners.values());
	}

	/**
	 * Dispatches the given args to the appropriate runner. The first element of {@code args} is the sub-command name;
	 * the rest are forwarded to the runner.
	 */
	public void dispatch(String[] args) throws Exception {
		if (args.length < 1 || "help".equalsIgnoreCase(args[0])) {
			printHelp(System.out);
			return;
		}

		String command = args[0].toLowerCase();
		FeatureRunner<?> runner = runners.get(command);
		if (runner == null) {
			System.err.println("Unknown " + levelName + " command: " + command);
			printHelp(System.err);
			System.exit(1);
		}

		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		runner.setInvocator(BrtsMain::main);
		runner.run(rest);
	}

	/** Prints a summary of all registered commands. */
	public void printHelp(PrintStream out) {
		out.println("BRTS " + levelName + " commands:");
		int maxLen = runners.keySet().stream().mapToInt(String::length).max().orElse(0);
		for (FeatureRunner<?> runner : runners.values()) {
			out.printf("  %-" + (maxLen + 2) + "s %s%n", runner.getCommandName(), runner.getDescription());
		}
		out.println();
		out.println("Run 'brts " + levelName + " <command> --help' for command-specific options.");
	}

}
