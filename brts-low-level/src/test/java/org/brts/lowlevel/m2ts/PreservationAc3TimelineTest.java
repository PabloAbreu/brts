/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/m2ts/PreservationAc3TimelineTest.java' is part of BRTS.
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
import java.util.ArrayList;
import java.util.List;

import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreservationAc3TimelineTest {
	@TempDir
	Path temp;

	@Test
	void regroupedAndMidFramePesHaveTheSameElementarySampleGrid() throws Exception {
		Path es = frames(0, 0, 8);
		// Source PES 2 begins inside frame 1: its PTS belongs to frame 2, not the partial frame.
		var source = timing(List.of(0L, 200L, 640L), List.of(90_000L, 95_760L, 104_400L));
		var generated = timing(List.of(0L, 384L, 768L), List.of(0L, 8_640L, 17_280L));
		PreservingM2tsClipWriter.validatePresentation(source, generated, 90_000, es, es);
	}

	@Test
	void equalPesPtsDoNotHideMovedFrameAnchors() throws Exception {
		Path es = frames(0, 0, 8);
		var source = timing(List.of(0L, 256L), List.of(90_000L, 95_760L));
		var wrong = timing(List.of(0L, 384L), List.of(0L, 5_760L));
		assertThatThrownBy(() -> PreservationAc3Timeline.compare(es, source, es, wrong, 90_000))
				.isInstanceOf(IOException.class).hasMessageContaining("exact sample clock");
	}

	@Test
	void rejectsGapDriftDistinctDtsAndTimestampedPesWithoutFrameStart() throws Exception {
		Path es = frames(0, 0, 8);
		var source = timing(List.of(0L, 256L), List.of(90_000L, 95_761L));
		assertThatThrownBy(() -> PreservationAc3Timeline.validate(es, source)).isInstanceOf(IOException.class)
				.hasMessageContaining("exact sample clock");
		var noFrame = timing(List.of(0L, 20L, 30L), List.of(90_000L, 92_880L, 92_880L));
		assertThatThrownBy(() -> PreservationAc3Timeline.validate(es, noFrame)).isInstanceOf(IOException.class)
				.hasMessageContaining("no commencing syncframe");
		var dts = new StreamTiming(0x1100, "audio.ac3", StreamCodingType.DOLBY_AC3, 90_000L, 89_000L,
				List.of(new PesTiming(0, 0, 90_000L, 89_000L)), List.of());
		assertThatThrownBy(() -> PreservationAc3Timeline.validate(es, dts)).isInstanceOf(IOException.class)
				.hasMessageContaining("distinct AC3 decode");
	}

	@Test
	void fractionalSampleClockUsesCumulativeSamplesNotRoundedFrameDurations() throws Exception {
		Path es = frames(1, 0, 6);
		var source = timing(List.of(0L, 276L, 552L), List.of(90_000L, 96_269L, 102_538L));
		var muxed = timing(List.of(0L, 414L, 690L), List.of(0L, 9_404L, 15_673L));
		PreservationAc3Timeline.compare(es, source, es, muxed, 90_000);
		var drift = timing(List.of(0L, 690L), List.of(0L, 15_670L));
		assertThatThrownBy(() -> PreservationAc3Timeline.compare(es, source, es, drift, 90_000))
				.isInstanceOf(IOException.class).hasMessageContaining("exact sample clock");
	}

	@Test
	void supports32kAndHalfRateAc3AndRejectsTruncationOrEac3() throws Exception {
		Path es = frames(2, 0, 3);
		PreservationAc3Timeline.validate(es, timing(List.of(0L, 192L), List.of(0L, 4_320L)));
		Path half = frames(0, 0, 3, 9);
		PreservationAc3Timeline.validate(half, timing(List.of(0L, 128L), List.of(0L, 5_760L)));
		Path eac3 = frames(0, 0, 3, 16);
		assertThatThrownBy(() -> PreservationAc3Timeline.validate(eac3, timing(List.of(0L), List.of(0L))))
				.isInstanceOf(IOException.class).hasMessageContaining("Unsupported AC3 frame header");
		byte[] truncated = Files.readAllBytes(es);
		Files.write(es, java.util.Arrays.copyOf(truncated, truncated.length - 1));
		assertThatThrownBy(() -> PreservationAc3Timeline.validate(es, timing(List.of(0L), List.of(0L))))
				.isInstanceOf(IOException.class).hasMessageContaining("Truncated AC3 syncframe");
	}

	@Test
	void unchangedTimingCannotHideDifferentElementaryBytes() throws Exception {
		Path source = frames(0, 0, 3);
		Path generated = temp.resolve("changed.ac3");
		byte[] bytes = Files.readAllBytes(source);
		bytes[20] = 1;
		Files.write(generated, bytes);
		var timing = timing(List.of(0L), List.of(0L));
		assertThatThrownBy(() -> PreservationAc3Timeline.compare(source, timing, generated, timing, 0))
				.isInstanceOf(IOException.class).hasMessageContaining("elementary bytes changed");
	}

	private Path frames(int fscod, int sizeCode, int count) throws IOException {
		return frames(fscod, sizeCode, count, 8);
	}

	private Path frames(int fscod, int sizeCode, int count, int bsid) throws IOException {
		int size = fscod == 0 ? 128 : fscod == 1 ? 138 : 192;
		byte[] result = new byte[size * count];
		for (int offset = 0; offset < result.length; offset += size) {
			result[offset] = 0x0b;
			result[offset + 1] = 0x77;
			result[offset + 4] = (byte) ((fscod << 6) | sizeCode);
			result[offset + 5] = (byte) (bsid << 3);
		}
		Path es = Files.createTempFile(temp, "frames-", ".ac3");
		Files.write(es, result);
		return es;
	}

	private static StreamTiming timing(List<Long> offsets, List<Long> pts) {
		List<PesTiming> pes = new ArrayList<>();
		for (int i = 0; i < offsets.size(); i++)
			pes.add(new PesTiming(i, offsets.get(i), pts.get(i), null));
		return new StreamTiming(0x1100, "audio.ac3", StreamCodingType.DOLBY_AC3, pts.getFirst(), null, pes, List.of());
	}
}
