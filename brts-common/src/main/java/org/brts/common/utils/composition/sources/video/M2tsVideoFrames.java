package org.brts.common.utils.composition.sources.video;

import java.io.IOException;
import java.nio.file.Path;

import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;

/**
 * {@link VideoFrames} implementation that decodes frames from an M2TS file using bytedeco/FFmpeg.
 * <p>
 * Uses the M2TS PMT to locate the H264_AVC video stream by PID, then delegates all FFmpeg decode logic to
 * {@link FfmpegVideoFrames}.
 */
public class M2tsVideoFrames extends FfmpegVideoFrames {

	public M2tsVideoFrames(Path m2tsPath) throws IOException {
		super(m2tsPath, parseH264Pid(m2tsPath));
	}

	private static int parseH264Pid(Path m2tsPath) throws IOException {
		M2tsParser parser = new M2tsParser();
		M2tsInfo info = parser.parse(m2tsPath);
		return info.getStreams().stream().filter(s -> s.getCodingType() == StreamCodingType.H264_AVC).findFirst()
				.map(M2tsStreamInfo::getPid)
				.orElseThrow(() -> new IOException("No H264_AVC stream found in " + m2tsPath));
	}

}
