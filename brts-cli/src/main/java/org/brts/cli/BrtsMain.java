package org.brts.cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.brts.cli.high.HighLevelCli;
import org.brts.cli.middle.MiddleLevelCli;

import lombok.extern.slf4j.Slf4j;

/**
 * Root CLI dispatcher for the BRTStool suite.
 * <p>
 * Usage:
 *
 * <pre>
 *   brt-cli.jar &lt;level&gt; &lt;command&gt; [options]
 *
 *   Levels:
 *     low      Low-level binary file operations
 *     mid      Middle-level disc authoring operations
 *     high     High-level template-based disc building
 *
 *   Examples:
 *     java -jar brt-cli.jar low  clip-parse     --input BDMV/CLIPINF/00001.clpi
 *     java -jar brt-cli.jar mid  build          --descriptor disc.json --output /tmp/out
 *     java -jar brt-cli.jar high build          --descriptor series-disc.json --output /tmp/out
 * </pre>
 */
@Slf4j
public class BrtsMain {

	private static final Map<String, LevelDispatcher> levels = new LinkedHashMap<>();

	static {
		levels.put("low", LowLevelDispatcher.getLevelDispatcher());
		levels.put("mid", MiddleLevelCli.getLevelDispatcher());
		levels.put("high", HighLevelCli.getLevelDispatcher());
	}

	/** Returns the registered CLI levels in display order. */
	public static Map<String, LevelDispatcher> getLevels() {
		return Collections.unmodifiableMap(levels);
	}

	public static void main(String[] args) {
		String level = args.length > 0 ? args[0].toLowerCase() : null;
		String[] rest = args.length > 0 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];

		if (!FeatureRunner.isBannerSuppressed(rest)) {
			BrtsBanner.print(System.err, level, rest);
		}

		if (level == null) {
			printUsage();
			System.exit(1);
		}

		try {
			LevelDispatcher dispatcher = levels.get(level);
			if (dispatcher != null) {
				dispatcher.dispatch(rest);
			} else {
				System.err.println("Unknown level: " + level);
				printUsage();
				System.exit(1);
			}
		} catch (Exception e) {
			log.error("Fatal error: {}", e.getMessage(), e);
			System.exit(2);
		}
	}

	private static void printUsage() {
		System.out.println("BRTS — Blu-ray Tools Suite");
		System.out.println("Usage: brts-cli.jar <level> <command> [options]");
		System.out.println();
		for (var entry : levels.entrySet()) {
			entry.getValue().printHelp(System.out);
		}
		System.out.println("Run 'brts-cli.jar <level> <command> --help' for command-specific options.");
	}

}
