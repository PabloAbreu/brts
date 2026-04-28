package org.brts.common.m2ts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import org.brts.common.m2ts.model.M2tsInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts elementary streams from an M2TS file to separate files on disk.
 * <p>
 * For each PID listed in a parsed {@link M2tsInfo}, the extractor writes all PES payload bytes for that PID into a file
 * named {@code <outputDir>/<pid>.<ext>}, where the extension is derived from the stream type.
 * <p>
 * The output files are raw elementary streams (i.e. the MPEG-2 TS packetisation and PES headers are removed).
 * Downstream tools like FFmpeg can consume them directly.
 * <p>
 * This class delegates to {@link M2tsDemuxer} for packet iteration and {@link FilePacketHandler} for file output.
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * M2tsInfo info = new M2tsParser().parse(inputPath);
 * new M2tsExtractor().extract(inputPath, info, outputDir, Set.of(4113, 4352));
 * }</pre>
 */
public class M2tsExtractor {

	private static final Logger log = LoggerFactory.getLogger(M2tsExtractor.class);

	private final M2tsDemuxer demuxer = new M2tsDemuxer();

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Extracts all streams described in {@code info} to {@code outputDir}.
	 *
	 * @param source    path to the M2TS source file
	 * @param info      stream metadata (from {@link M2tsParser})
	 * @param outputDir directory where elementary stream files will be written
	 * @throws IOException on I/O error
	 */
	public void extract(Path source, M2tsInfo info, Path outputDir) throws IOException {
		extract(source, info, outputDir, null);
	}

	/**
	 * Extracts only the PIDs listed in {@code pidFilter} to {@code outputDir}.
	 *
	 * @param source    path to the M2TS source file
	 * @param info      stream metadata (from {@link M2tsParser})
	 * @param outputDir directory where elementary stream files will be written
	 * @param pidFilter only these PIDs will be extracted; pass null or empty to extract all
	 * @throws IOException on I/O error
	 */
	public void extract(Path source, M2tsInfo info, Path outputDir, Set<Integer> pidFilter) throws IOException {
		log.info("Extracting M2TS streams: {} → {}", source.getFileName(), outputDir);

		try (FilePacketHandler handler = new FilePacketHandler(info, outputDir, pidFilter)) {
			demuxer.demux(source, info, handler, pidFilter);
		}
	}

}
