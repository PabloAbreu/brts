/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/m2ts/PreservingM2tsClipWriterTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.SegmentTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreservingM2tsClipWriterTest {
	@TempDir
	Path temp;

	@Test
	void descriptorContractRoundTripsAdditiveFields() throws Exception {
		var descriptor = new M2tsDescriptor();
		descriptor.setSourceM2ts("/source/00001.m2ts");
		descriptor.setSourceClpi("/source/00001.clpi");
		descriptor.setTimingManifest("/work/timing.json");
		descriptor.setMaximumOutputBytes(123_456L);
		byte[] json = JsonMapperFactory.get().writeValueAsBytes(descriptor);
		var result = JsonMapperFactory.get().readValue(json, M2tsDescriptor.class);
		assertThat(result.getSourceM2ts()).isEqualTo(descriptor.getSourceM2ts());
		assertThat(result.getSourceClpi()).isEqualTo(descriptor.getSourceClpi());
		assertThat(result.getTimingManifest()).isEqualTo(descriptor.getTimingManifest());
		assertThat(result.getMaximumOutputBytes()).isEqualTo(123_456L);
	}

	@Test
	void incompleteExplicitContractFailsBeforeBackendOrPublication() {
		var descriptor = new M2tsDescriptor();
		descriptor.setOutputName("00001");
		descriptor.setTimingManifest("/work/timing.json");
		assertThatThrownBy(() -> new PreservingM2tsClipWriter().write(descriptor, temp.resolve("00001.m2ts"),
				temp.resolve("00001.clpi"))).isInstanceOf(IOException.class).hasMessageContaining("required together");
		assertThat(temp.resolve("00001.m2ts")).doesNotExist();
		assertThat(temp.resolve("00001.clpi")).doesNotExist();
	}

	@Test
	void normalizesAbsoluteWrappedSupAndKeepsLateClear() throws Exception {
		long origin = (1L << 33) + 1_000;
		long pts = origin + 90_000;
		long clearPts = pts + 324_000_000;
		Path input = temp.resolve("absolute.pgs");
		try (var out = Files.newOutputStream(input)) {
			M2tsTiming.writeSupSegment(out, pts, null, 0x16, new byte[] { 3, 7 }, 0, 2);
			M2tsTiming.writeSupSegment(out, clearPts, null, 0x80, new byte[0], 0, 0);
		}
		var timing = new StreamTiming(0x1203, input.getFileName().toString(), StreamCodingType.PRESENTATION_GRAPHICS,
				pts, null, List.of(new PesTiming(0, 0, pts, null), new PesTiming(1, 15, clearPts, null)),
				List.of(new SegmentTiming(0, 0x16, 2, pts, null), new SegmentTiming(15, 0x80, 0, clearPts, null)));
		PreservingM2tsClipWriter.validateTiming(timing);
		Path normalized = temp.resolve("normalized.sup");
		PreservingM2tsClipWriter.normalizeSup(input, normalized, timing, origin);
		try (var in = new DataInputStream(Files.newInputStream(normalized))) {
			assertThat(in.readUnsignedShort()).isEqualTo(0x5047);
			assertThat(Integer.toUnsignedLong(in.readInt())).isEqualTo(90_000);
			assertThat(in.readInt()).isZero();
			assertThat(in.readUnsignedByte()).isEqualTo(0x16);
			assertThat(in.readUnsignedShort()).isEqualTo(2);
			assertThat(in.readNBytes(2)).containsExactly(new byte[] { 3, 7 });
			assertThat(in.readUnsignedShort()).isEqualTo(0x5047);
			assertThat(Integer.toUnsignedLong(in.readInt())).isEqualTo(324_090_000);
		}
	}

	@Test
	void rejectsSupBeforeVideoOriginAndUnmanifestedRecords() throws Exception {
		Path input = temp.resolve("sub.pgs");
		try (var out = Files.newOutputStream(input)) {
			M2tsTiming.writeSupSegment(out, 100, null, 0x80, new byte[0], 0, 0);
			M2tsTiming.writeSupSegment(out, 200, null, 0x80, new byte[0], 0, 0);
		}
		var timing = new StreamTiming(0x1200, "sub.pgs", StreamCodingType.PRESENTATION_GRAPHICS, 100L, null,
				List.of(new PesTiming(0, 0, 100L, null)), List.of(new SegmentTiming(0, 0x80, 0, 100, null)));
		assertThatThrownBy(() -> PreservingM2tsClipWriter.normalizeSup(input, temp.resolve("early.sup"), timing, 101))
				.isInstanceOf(IOException.class).hasMessageContaining("before video origin");
		assertThatThrownBy(() -> PreservingM2tsClipWriter.normalizeSup(input, temp.resolve("extra.sup"), timing, 0))
				.isInstanceOf(IOException.class).hasMessageContaining("unmanifested");
	}

	@Test
	void rejectsDiscontinuousOrMissingOriginalTiming() {
		var backwards = timing(StreamCodingType.DOLBY_AC3, List.of(100L, 90L));
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateTiming(backwards)).isInstanceOf(IOException.class)
				.hasMessageContaining("Discontinuous");
		var missing = new StreamTiming(0x1100, "audio.ac3", StreamCodingType.DOLBY_AC3, null, null, List.of(),
				List.of());
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateTiming(missing)).isInstanceOf(IOException.class)
				.hasMessageContaining("Incomplete");
	}

	@Test
	void constantShiftMustPreserveCompletePresentationGrid() throws Exception {
		var source = timing(StreamCodingType.H264_AVC, List.of(100_000L, 103_600L, 107_200L));
		var shifted = timing(StreamCodingType.H264_AVC, List.of(0L, 3_600L, 7_200L));
		PreservingM2tsClipWriter.validatePresentation(source, shifted, 100_000, true);
		var dropped = timing(StreamCodingType.H264_AVC, List.of(0L, 7_200L));
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validatePresentation(source, dropped, 100_000, true))
				.isInstanceOf(IOException.class).hasMessageContaining("presentation grid");
	}

	@Test
	void metaSchedulesSignedAudioDelayAndSourceVideoClock() throws Exception {
		var descriptor = new M2tsDescriptor();
		var video = new M2tsDescriptor.StreamEntry();
		video.setPid(0x1011);
		video.setStreamTypeByte(0x24);
		video.setFrameRateFps(25.0);
		var audio = new M2tsDescriptor.StreamEntry();
		audio.setPid(0x1102);
		audio.setStreamTypeByte(0x81);
		descriptor.setStreams(List.of(video, audio));
		var audioTiming = timing(StreamCodingType.DOLBY_AC3, List.of(81_001L));
		String meta = PreservingM2tsClipWriter.meta(descriptor,
				List.of(temp.resolve("video.hevc"), temp.resolve("audio.ac3")), Map.of(0x1102, audioTiming), 90_000,
				true);
		assertThat(meta).contains("--pcr-on-video-pid", "--start-time=45000", "--blu-ray-v3", "timeshift=-99ms",
				"V_MPEGH/ISO/HEVC");
	}

	@Test
	void wholeStreamSequenceCheckRejectsLaterSpsChanges() throws Exception {
		Path stable = temp.resolve("stable.h264");
		Files.write(stable, new byte[] { 0, 0, 0, 1, 0x67, 0x42, 0x23, (byte) 0x80, 0, 0, 1, 0x65, 7, 8, 0, 0, 1, 0x67,
				0x42, 0x23, (byte) 0x80 });
		PreservationVideoGeometry.validateStableSequence(stable, 0x1b);
		Path changed = temp.resolve("changed.h264");
		byte[] bytes = Files.readAllBytes(stable);
		bytes[bytes.length - 2] = 0x24;
		Files.write(changed, bytes);
		assertThatThrownBy(() -> PreservationVideoGeometry.validateStableSequence(changed, 0x1b))
				.isInstanceOf(IOException.class).hasMessageContaining("changing video sequence");
		Path missing = temp.resolve("missing.hevc");
		Files.write(missing, new byte[] { 0, 0, 1, 0x42, 1, (byte) 0x80 });
		assertThatThrownBy(() -> PreservationVideoGeometry.validateStableSequence(missing, 0x24))
				.isInstanceOf(IOException.class).hasMessageContaining("Missing/truncated");
	}

	@Test
	void oversizeIsRejectedWithoutPublishingOrDeletingStage() throws Exception {
		Path stage = temp.resolve("normalized.m2ts");
		Files.write(stage, new byte[384]);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateSize(stage, 383)).isInstanceOf(IOException.class)
				.hasMessageContaining("maximumOutputBytes");
		assertThat(stage).hasSize(384);
		assertThat(temp.resolve("00001.m2ts")).doesNotExist();
		assertThat(temp.resolve("00001.clpi")).doesNotExist();
		PreservingM2tsClipWriter.validateSize(stage, 384);
	}

	@Test
	void nonzeroBackendExitThrowsAndKeepsDiagnostics() throws Exception {
		Path meta = temp.resolve("input.meta");
		Files.writeString(meta, "test");
		Path log = temp.resolve("mux.log");
		assertThatThrownBy(() -> PreservingM2tsClipWriter.run("/bin/false", meta, temp, log))
				.isInstanceOf(IOException.class).hasMessageContaining("exited 1");
		assertThat(log).exists();
		assertThat(meta).exists();
	}

	@Test
	void interruptionThrowsAndRestoresInterruptFlag() throws Exception {
		Path executable = temp.resolve("blocking-tsmuxer");
		Files.writeString(executable, "#!/bin/sh\nread ignored\n");
		assertThat(executable.toFile().setExecutable(true)).isTrue();
		try {
			Thread.currentThread().interrupt();
			assertThatThrownBy(() -> PreservingM2tsClipWriter.run(executable.toString(), temp.resolve("input.meta"),
					temp, temp.resolve("interrupt.log"))).isInstanceOf(IOException.class)
					.hasMessageContaining("interrupted");
			assertThat(Thread.currentThread().isInterrupted()).isTrue();
		} finally {
			Thread.interrupted();
		}
	}

	private static StreamTiming timing(StreamCodingType type, List<Long> pts) {
		return new StreamTiming(0x1100, "stream.es", type, pts.getFirst(), null,
				pts.stream().map(p -> new PesTiming(0, 0, p, null)).toList(), List.of());
	}
}
