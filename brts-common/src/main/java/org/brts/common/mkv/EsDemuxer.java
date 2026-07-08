package org.brts.common.mkv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Demuxes a container into elementary stream files.
 */
public interface EsDemuxer {

	Map<Integer, Path> demux(Path sourcePath, Path outputDir) throws IOException;

	Map<Integer, Path> demux(Path sourcePath, Path outputDir, Set<Integer> trackFilter) throws IOException;
}
