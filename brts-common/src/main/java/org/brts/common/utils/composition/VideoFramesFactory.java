package org.brts.common.utils.composition;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Factory that creates the appropriate {@link VideoFrames} implementation based on the file extension.
 */
public class VideoFramesFactory {

	private VideoFramesFactory() {
	}

	/**
	 * Creates a {@link VideoFrames} instance for the given video file path.
	 *
	 * @param path path to the video file
	 * @return a new {@link VideoFrames} ready to decode frames
	 * @throws IOException              if opening or parsing the file fails
	 * @throws IllegalArgumentException if the file extension is not supported
	 */
	public static VideoFrames create(Path path) throws IOException {
		String fileName = path.getFileName().toString();
		int dot = fileName.lastIndexOf('.');
		String ext = dot >= 0 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";

		return switch (ext) {
		case "m2ts" -> new M2tsVideoFrames(path);
		case "mkv" -> new MkvVideoFrames(path);
		default -> throw new IllegalArgumentException("Unsupported video format: " + fileName);
		};
	}

}
