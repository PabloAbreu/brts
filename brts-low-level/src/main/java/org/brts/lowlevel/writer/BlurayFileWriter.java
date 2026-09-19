package org.brts.lowlevel.writer;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/writer/BlurayFileWriter.java' is part of BRTS.
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generic contract for all low-level binary writers.
 *
 * @param <T> the model type consumed by the writer
 */
public interface BlurayFileWriter<T> {

	/**
	 * Serialises {@code model} to the given {@link OutputStream}.
	 *
	 * @param model  the model to write
	 * @param output the destination stream; the caller is responsible for closing it.
	 * @throws org.brts.common.exception.WriteException on any I/O or validation error
	 */
	void write(T model, OutputStream output) throws IOException;

	/**
	 * Convenience method — creates (or overwrites) the file at {@code path} and delegates to
	 * {@link #write(Object, OutputStream)}.
	 */
	default void write(T model, Path path) throws IOException {
		Files.createDirectories(path.getParent());
		try (var out = Files.newOutputStream(path)) {
			write(model, out);
		}
	}

}
