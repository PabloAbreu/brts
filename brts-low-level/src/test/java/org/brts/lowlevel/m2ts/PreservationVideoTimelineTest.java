/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/m2ts/PreservationVideoTimelineTest.java' is part of BRTS.
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

package org.brts.lowlevel.m2ts;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreservationVideoTimelineTest {
	@TempDir
	Path temp;

	@Test
	void avcAndHevcRequireOneRealPicturePerTimestampedPes() throws Exception {
		Path avc = write(new byte[] { 0, 0, 1, 9, (byte) 0xf0, 0, 0, 1, 0x65, (byte) 0x80, 0, 0, 1, 9, (byte) 0xf0, 0,
				0, 1, 0x41, (byte) 0x80 });
		PreservationVideoTimeline.validate(avc, timing(StreamCodingType.H264_AVC, 0, 10));
		Path hevc = write(new byte[] { 0, 0, 1, 0x46, 1, 0x50, 0, 0, 1, 0x26, 1, (byte) 0x80 });
		PreservationVideoTimeline.validate(hevc, timing(StreamCodingType.H265_HEVC, 0));
	}

	@Test
	void equalPesCountsCannotHideMissingAudOrMultiplePicturesInOnePes() throws Exception {
		Path noAud = write(new byte[] { 0, 0, 1, 0x65, (byte) 0x80 });
		assertThatThrownBy(() -> PreservationVideoTimeline.validate(noAud, timing(StreamCodingType.H264_AVC, 0)))
				.isInstanceOf(IOException.class).hasMessageContaining("AUD-delimited");
		Path two = write(
				new byte[] { 0, 0, 1, 9, (byte) 0xf0, 0, 0, 1, 0x65, (byte) 0x80, 0, 0, 1, 0x41, (byte) 0x80 });
		assertThatThrownBy(() -> PreservationVideoTimeline.validate(two, timing(StreamCodingType.H264_AVC, 0)))
				.isInstanceOf(IOException.class).hasMessageContaining("missing/multiple pictures");
	}

	@Test
	void rejectsAmbiguousSliceOrderAndPesToAccessUnitOffsets() throws Exception {
		// A continuation slice before any first slice is malformed even with a native AU parser.
		Path multiple = write(new byte[] { 0, 0, 1, 9, (byte) 0xf0, 0, 0, 1, 0x65, 0x40, 0, 0, 1, 0x41, (byte) 0x80 });
		assertThatThrownBy(() -> PreservationVideoTimeline.validate(multiple, timing(StreamCodingType.H264_AVC, 0)))
				.isInstanceOf(IOException.class).hasMessageContaining("Non-first slice precedes");
		Path hevc = write(new byte[] { 0, 0, 1, 0x46, 1, 0x50, 0, 0, 1, 0x26, 1, 0x40 });
		assertThatThrownBy(() -> PreservationVideoTimeline.validate(hevc, timing(StreamCodingType.H265_HEVC, 0)))
				.isInstanceOf(IOException.class).hasMessageContaining("Non-first slice precedes");
		Path avc = write(new byte[] { 0, 0, 1, 9, (byte) 0xf0, 0, 0, 1, 0x65, (byte) 0x80, 0, 0, 1, 9, (byte) 0xf0, 0,
				0, 1, 0x41, (byte) 0x80 });
		assertThatThrownBy(() -> PreservationVideoTimeline.validate(avc, timing(StreamCodingType.H264_AVC, 0)))
				.isInstanceOf(IOException.class).hasMessageContaining("Multiple video access units");
		assertThatThrownBy(() -> PreservationVideoTimeline.validate(avc, timing(StreamCodingType.H264_AVC, 0, 12)))
				.isInstanceOf(IOException.class).hasMessageContaining("byte offsets");
	}

	private Path write(byte[] bytes) throws IOException {
		Path file = Files.createTempFile(temp, "video-", ".es");
		Files.write(file, bytes);
		return file;
	}

	private static StreamTiming timing(StreamCodingType type, long... offsets) {
		var pes = new java.util.ArrayList<PesTiming>();
		for (int i = 0; i < offsets.length; i++)
			pes.add(new PesTiming(i, offsets[i], 90_000L + 3600 * i, null));
		return new StreamTiming(0x1011, "video.es", type, 90_000L, null, pes, List.of());
	}
}
