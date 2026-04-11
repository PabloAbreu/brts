package org.brts.lowlevel.writer;

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
	 * @param model the model to write
	 * @param output the destination stream; the caller is responsible for closing it.
	 * @throws org.brts.common.exception.WriteException on any I/O or validation error
	 */
	void write(T model, OutputStream output) throws IOException;

	/**
	 * Convenience method — creates (or overwrites) the file at {@code path} and delegates
	 * to {@link #write(Object, OutputStream)}.
	 */
	default void write(T model, Path path) throws IOException {
		Files.createDirectories(path.getParent());
		try (var out = Files.newOutputStream(path)) {
			write(model, out);
		}
	}

}
