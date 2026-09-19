package org.brts.lowlevel.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/parser/BinaryParser.java' is part of BRTS.
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
import java.io.InputStream;
import java.nio.file.Path;

/**
 * Generic contract for all low-level binary parsers.
 *
 * @param <T> the model type produced by parsing
 */
public interface BinaryParser<T> {

	/**
	 * Parses the binary format from the given {@link InputStream}.
	 *
	 * @param input the stream to read; the caller is responsible for closing it.
	 * @return the parsed model object
	 * @throws org.brts.common.exception.ParseException on any format or I/O error
	 */
	T parse(InputStream input) throws IOException;

	/**
	 * Convenience method — opens the file at {@code path} and delegates to {@link #parse(InputStream)}.
	 */
	default T parse(Path path) throws IOException {
		try (var in = java.nio.file.Files.newInputStream(path)) {
			return parse(in);
		}
	}

}
