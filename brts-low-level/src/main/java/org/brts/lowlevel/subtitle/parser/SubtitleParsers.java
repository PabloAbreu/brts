package org.brts.lowlevel.subtitle.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/subtitle/parser/SubtitleParsers.java' is part of BRTS.
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

import java.nio.file.Path;

/**
 * Resolves a {@link SubtitleParser} implementation based on file extension or explicit format name.
 */
public final class SubtitleParsers {

	private SubtitleParsers() {
	}

	/**
	 * Returns the appropriate parser for the given subtitle file, based on its extension.
	 *
	 * @param file the subtitle file path
	 * @return a parser capable of reading the file
	 * @throws IllegalArgumentException if the format is not recognised
	 */
	public static SubtitleParser forFile(Path file) {
		String name = file.getFileName().toString().toLowerCase();
		if (name.endsWith(".srt")) {
			return new SrtParser();
		} else if (name.endsWith(".ass") || name.endsWith(".ssa")) {
			return new SsaParser();
		}
		throw new IllegalArgumentException(
				"Unsupported subtitle format: " + file.getFileName() + " (supported: .srt, .ssa, .ass)");
	}

	/**
	 * Returns the appropriate parser for the given format name.
	 *
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
