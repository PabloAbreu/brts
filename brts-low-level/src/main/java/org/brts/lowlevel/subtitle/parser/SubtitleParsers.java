package org.brts.lowlevel.subtitle.parser;

import java.nio.file.Path;

/**
 * Resolves a {@link SubtitleParser} implementation based on file extension or explicit
 * format name.
 */
public final class SubtitleParsers {

	private SubtitleParsers() {
	}

	/**
	 * Returns the appropriate parser for the given subtitle file, based on its extension.
	 * @param file the subtitle file path
	 * @return a parser capable of reading the file
	 * @throws IllegalArgumentException if the format is not recognised
	 */
	public static SubtitleParser forFile(Path file) {
		String name = file.getFileName().toString().toLowerCase();
		if (name.endsWith(".srt")) {
			return new SrtParser();
		}
		else if (name.endsWith(".ass") || name.endsWith(".ssa")) {
			return new SsaParser();
		}
		throw new IllegalArgumentException(
				"Unsupported subtitle format: " + file.getFileName() + " (supported: .srt, .ssa, .ass)");
	}

	/**
	 * Returns the appropriate parser for the given format name.
	 * @param format the format identifier (case-insensitive), e.g. "SRT", "ASS", "SSA"
	 * @return a parser for that format
	 * @throws IllegalArgumentException if the format is not recognised
	 */
	public static SubtitleParser forFormat(String format) {
		return switch (format.toUpperCase()) {
			case "SRT", "SUBRIP" -> new SrtParser();
			case "SSA", "ASS" -> new SsaParser();
			default -> throw new IllegalArgumentException(
					"Unsupported subtitle format: " + format + " (supported: SRT, SSA, ASS)");
		};
	}

}
