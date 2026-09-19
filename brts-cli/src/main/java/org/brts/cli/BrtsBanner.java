package org.brts.cli;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/BrtsBanner.java' is part of BRTS.
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

import org.brts.common.utils.BrtsFileConfig;

import java.io.PrintStream;

/**
 * Startup banner for the BRTS CLI. Disable with {@code brts.cli.banner=false}.
 */
public final class BrtsBanner {

	public static final String CONFIG_KEY_BANNER = "brts.cli.banner";

	private static final String[] BANNER_LINES = { //
			" ____    ____    _____   ____  ", //
			"| __ )  |  _ \\  |_   _| / ___| ", //
			"|  _ \\  | |_) |   | |   \\___ \\ ", //
			"| |_) | |  _ <    | |    ___) |", //
			"|____/  |_| \\_\\   |_|   |____/ " //
	};

	private BrtsBanner() {
	}

	/**
	 * Prints the banner unless disabled by configuration.
	 *
	 * @param out   target stream (use stderr to keep stdout machine-parseable)
	 * @param level requested level, may be {@code null}
	 * @param args  arguments passed to the level dispatcher
	 */
	public static void print(PrintStream out, String level, String[] args) {
		if (!BrtsFileConfig.getInstance().parseBooleanProperty(CONFIG_KEY_BANNER, true)) {
			return;
		}
		for (String line : BANNER_LINES) {
			out.println(line);
		}
		out.println();
		out.println("  Blu-ray Tools Suite " + version());
		out.println("  Java    : " + System.getProperty("java.version"));
		out.println("  Level   : " + (level == null || level.isBlank() ? "<none>" : level));
		out.println("  Args    : " + (args == null || args.length == 0 ? "<none>" : String.join(" ", args)));
		out.println();
	}

	private static String version() {
		String version = BrtsBanner.class.getPackage().getImplementationVersion();
		return version == null ? "dev" : version;
	}

}
