package org.brts.common.utils.composition;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@link VideoFrames} implementation that decodes frames from an MP4 (or MOV) file using bytedeco/FFmpeg.
 * <p>
 * Selects the first video stream in the container. All decode logic is inherited from {@link FfmpegVideoFrames}.
 */
public class Mp4VideoFrames extends FfmpegVideoFrames {

	public Mp4VideoFrames(Path mp4Path) throws IOException {
		super(mp4Path);
	}

}
