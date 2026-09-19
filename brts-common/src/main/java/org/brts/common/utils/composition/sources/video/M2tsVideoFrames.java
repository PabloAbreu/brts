package org.brts.common.utils.composition.sources.video;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/composition/sources/video/M2tsVideoFrames.java' is part of BRTS.
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
