package org.brts.lowlevel.subtitle.parser;

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
