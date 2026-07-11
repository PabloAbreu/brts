package org.brts.common.utils.composition.sources.video;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link VideoFrames} implementation that decodes frames from an MKV (Matroska) file using bytedeco/FFmpeg.
 * <p>
 * Selects the first video stream in the container. All decode logic is inherited from {@link FfmpegVideoFrames}.
 */
public class MkvVideoFrames extends FfmpegVideoFrames {

	public MkvVideoFrames(Path mkvPath) throws IOException {
		super(mkvPath);
	}

}
