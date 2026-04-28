package org.brts.lowlevel.parser;

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
