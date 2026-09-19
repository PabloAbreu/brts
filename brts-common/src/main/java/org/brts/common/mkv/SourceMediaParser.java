package org.brts.common.mkv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/mkv/SourceMediaParser.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstract contract for source media parsers. Implementations provide track metadata extracted from different container
 * formats (MKV, MP4, etc.) without coupling the rest of the tool suite to a specific library.
 */
public interface SourceMediaParser {

	/**
	 * Parses the container at the given path and returns track metadata.
	 *
	 * @param path path to the source media file
	 * @return populated {@link SourceMediaInfo}
	 * @throws IOException                              on read failure
	 * @throws org.brts.common.exception.ParseException if the container is unrecognised
	 */
	SourceMediaInfo parse(Path path) throws IOException;

	/**
	 * Returns the file extensions this parser can handle (lowercase, without dot). Example:
	 * {@code ["mkv", "mka", "mks"]}.
	 */
	String[] supportedExtensions();

}
