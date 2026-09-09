package org.brts.cli;

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
