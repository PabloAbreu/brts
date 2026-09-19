package org.brts.lowlevel.subtitle.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/subtitle/parser/SubtitleParser.java' is part of BRTS.
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

import org.brts.lowlevel.subtitle.model.SubtitleTrack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Common interface for subtitle file parsers.
 * <p>
 * Each implementation handles a specific text-based subtitle format (SRT, SSA/ASS, etc.) and produces a format-agnostic
 * {@link SubtitleTrack}.
 */
public interface SubtitleParser {

	/**
	 * Parses a subtitle file from disk.
	 *
	 * @param file path to the subtitle file
	 * @return the parsed subtitle track
	 * @throws IOException on I/O or parse error
	 */
	SubtitleTrack parse(Path file) throws IOException;

	/**
	 * Parses subtitle data from an input stream. This overload supports on-the-fly extraction from container formats
	 * (e.g. MKV) where the data is not on disk.
	 *
	 * @param input  the raw subtitle text stream
	 * @param format hint for the format name (e.g. "SRT", "ASS")
	 * @return the parsed subtitle track
	 * @throws IOException on I/O or parse error
	 */
	SubtitleTrack parse(InputStream input, String format) throws IOException;

}
