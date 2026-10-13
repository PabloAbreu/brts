/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/VideoConversionPlan.java' is part of BRTS.
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

package org.brts.lowlevel.utils.video;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.VideoConversionParams.RateControl;

/** Immutable request snapshot, resolved geometry, and shell-free command preview. No process is started by prepare. */
public record VideoConversionPlan(Path inputPath, Path outputPath, StreamCodingType codec, Engine engine,
		VideoStreamInfo source, FrameRate sourceFrameRate, FrameRate frameRate, int width, int height, int contentWidth,
		int contentHeight, int padLeft, int padTop, int padRight, int padBottom, int gopFrames, int bFrames,
		int maxBitrateKbps, int vbvBufferKbits, RateControl rateControl, boolean overwrite, Duration timeout,
		List<String> command, List<String> warnings) {

	public VideoConversionPlan {
		command = List.copyOf(command);
		warnings = List.copyOf(warnings);
	}

	/** Target duration / source duration. Separately extracted audio must be adjusted outside this utility. */
	public double durationRatio() {
		return sourceFrameRate.asDouble() / frameRate.asDouble();
	}
}
