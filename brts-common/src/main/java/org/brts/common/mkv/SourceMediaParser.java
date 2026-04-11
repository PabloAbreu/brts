package org.brts.common.mkv;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstract contract for source media parsers. Implementations provide track metadata
 * extracted from different container formats (MKV, MP4, etc.) without coupling the rest
 * of the tool suite to a specific library.
 */
public interface SourceMediaParser {

	/**
	 * Parses the container at the given path and returns track metadata.
	 * @param path path to the source media file
	 * @return populated {@link SourceMediaInfo}
	 * @throws IOException on read failure
	 * @throws org.brts.common.exception.ParseException if the container is unrecognised
	 */
	SourceMediaInfo parse(Path path) throws IOException;

	/**
	 * Returns the file extensions this parser can handle (lowercase, without dot).
	 * Example: {@code ["mkv", "mka", "mks"]}.
	 */
	String[] supportedExtensions();

}
