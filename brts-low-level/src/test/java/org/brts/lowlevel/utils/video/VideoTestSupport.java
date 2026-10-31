/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/utils/video/VideoTestSupport.java' is part of BRTS.
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

import org.brts.common.video.FrameRate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** All process/probe boundaries are in-memory; even executable fixtures are never launched. */
final class VideoTestSupport {

	private VideoTestSupport() {
	}

	static VideoStreamInfo sdr(int width, int height, FrameRate rate) {
		return new VideoStreamInfo(width, height, rate, 1, 1, true, "h264", "bt709", "bt709", "bt709", false, null,
				null, 8, 1, 1, 100, 41, 1, true);
	}

	static VideoConversionParams request(Path directory) throws IOException {
		VideoConversionParams params = new VideoConversionParams();
		params.setInputPath(Files.writeString(directory.resolve("source with spaces.264"), "not real video"));
		params.setOutputPath(directory.resolve("destination with spaces.264"));
		params.setBitrateKbps(20000);
		return params;
	}

	static VideoConverter preview(VideoStreamInfo source) {
		return new VideoConverter((key, fallback) -> fallback, path -> source, new EncoderProcessRunner(command -> {
			throw new AssertionError("prepare must never launch a process");
		}));
	}

	static Process completed(int exit, String stdout, String stderr) throws InterruptedException {
		Process process = mock(Process.class);
		when(process.getInputStream()).thenReturn(new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8)));
		when(process.getErrorStream()).thenReturn(new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8)));
		when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
		when(process.descendants()).thenAnswer(invocation -> Stream.empty());
		when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
		when(process.exitValue()).thenReturn(exit);
		when(process.destroyForcibly()).thenReturn(process);
		return process;
	}

	static String value(List<String> command, String option) {
		int index = command.indexOf(option);
		assertTrue(index >= 0 && index + 1 < command.size(), "Missing option " + option + " in " + command);
		return command.get(index + 1);
	}
}
