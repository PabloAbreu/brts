package org.brts.common.utils.composition.sources.video;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/sources/video/VideoFramesFactory.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

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
		case "mp4", "mov" -> new Mp4VideoFrames(path);
		default -> throw new IllegalArgumentException("Unsupported video format: " + fileName);
		};
	}

}
