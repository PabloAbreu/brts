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
import java.util.Set;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.SegmentTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.clpi.ClpiRegenOptions;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.clpi.EpMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreservingM2tsClipWriterTest {
	@TempDir
	Path temp;

	@Test
	void acceptsSingleAvcOrHevcSourceVideo() throws Exception {
		for (var type : List.of(StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC)) {
			PreservingM2tsClipWriter.validateSourceVideo(sourceInfo(stream(0x1011, type)), video(0x1011, type));
		}
	}

	@Test
	void acceptsIgnoredHevcDolbyVisionCompanion() throws Exception {
		var source = sourceInfo(stream(0x1015, StreamCodingType.H265_HEVC), stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1011, StreamCodingType.H265_HEVC), stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		PreservingM2tsClipWriter.validateSourceVideo(source, video(0x1011, StreamCodingType.H265_HEVC));
	}

	@Test
	void rejectsOtherSecondaryVideoTopologies() {
		var hevc = StreamCodingType.H265_HEVC;
		var avc = StreamCodingType.H264_AVC;
		for (var source : List.of(sourceInfo(stream(0x1011, hevc), stream(0x1016, hevc)),
				sourceInfo(stream(0x1011, avc), stream(0x1015, hevc)),
				sourceInfo(stream(0x1011, hevc), stream(0x1015, avc)),
				sourceInfo(stream(0x1015, hevc), stream(0x1016, hevc)),
				sourceInfo(stream(0x1011, hevc), stream(0x1015, hevc), stream(0x1016, hevc)))) {
			assertThatThrownBy(() -> PreservingM2tsClipWriter.validateSourceVideo(source, video(0x1011, hevc)))
					.isInstanceOf(IOException.class).hasMessageContaining("Secondary/multiview video is unsupported");
		}
	}

	@Test
	void sourceComparisonExcludesOnlyCompanionWithoutChangingOutputOptions() throws Exception {
		var source = sourceInfo(stream(0x1015, StreamCodingType.H265_HEVC), stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1011, StreamCodingType.H265_HEVC), stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		var output = new ClpiRegenOptions();
		var metadata = new ClpiRegenOptions.VideoOverrides();
		metadata.setAspectRatio(3);
		output.getVideoStreams().put(0x1011, metadata);
		var options = PreservingM2tsClipWriter.sourceRegenOptions(source, video(0x1011, StreamCodingType.H265_HEVC),
				output);
		assertThat(options.getExcludedStreamPids()).containsExactly(0x1015);
		assertThat(output.getExcludedStreamPids()).isEmpty();
		assertThat(options.getVideoStreams()).containsExactlyEntriesOf(output.getVideoStreams());
		assertThat(options.getVideoStreams()).isNotSameAs(output.getVideoStreams());
		assertThatThrownBy(() -> PreservingM2tsClipWriter.sourceRegenOptions(
				sourceInfo(stream(0x1011, StreamCodingType.H265_HEVC), stream(0x1016, StreamCodingType.H265_HEVC)),
				video(0x1011, StreamCodingType.H265_HEVC), output)).isInstanceOf(IOException.class);
		var single = PreservingM2tsClipWriter.sourceRegenOptions(sourceInfo(stream(0x1011, StreamCodingType.H264_AVC)),
				video(0x1011, StreamCodingType.H264_AVC), output);
		assertThat(single.getExcludedStreamPids()).isEmpty();
	}

	@Test
	void rejectsRetainingDolbyVisionCompanionInsteadOfPrimary() {
		var source = sourceInfo(stream(0x1011, StreamCodingType.H265_HEVC), stream(0x1015, StreamCodingType.H265_HEVC));
		assertThatThrownBy(
				() -> PreservingM2tsClipWriter.validateSourceVideo(source, video(0x1015, StreamCodingType.H265_HEVC)))
				.isInstanceOf(IOException.class).hasMessageContaining("Secondary/multiview video is unsupported");
	}

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
	void wholeStreamSequenceCheckAcceptsHevcTypeZeroNalHeader() throws Exception {
		Path elementary = temp.resolve("type-zero.hevc");
		Files.write(elementary, new byte[] { 0, 0, 1, 0x40, 1, (byte) 0x80, 0, 0, 1, 0x42, 1, (byte) 0x80, 0, 0, 1, 0,
				1, (byte) 0x80 });

		PreservationVideoGeometry.validateStableSequence(elementary, 0x24);
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

	@Test
	void clipDiagnosticsReportAllHevcDifferencesAndBothStreamSnapshots() {
		var source = clip(StreamCodingType.H265_HEVC);
		var output = clip(StreamCodingType.H265_HEVC);
		var original = source.getStreams().getFirst();
		var regenerated = output.getStreams().getFirst();
		original.setDynamicRangeType(1);
		original.setColorSpace(2);
		original.setHdrPlusFlag(false);
		regenerated.setDynamicRangeType(0);
		regenerated.setColorSpace(null);
		regenerated.setHdrPlusFlag(true);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class).hasMessageContaining("dynamicRangeType: source=1, output=0 (changed)")
				.hasMessageContaining("colorSpace: source=2, output=null (changed)")
				.hasMessageContaining("hdrPlusFlag: source=false, output=true (changed)")
				.hasMessageContaining("sourceStream={pid=4113 (0x1011), codingType=H265_HEVC")
				.hasMessageContaining("outputStream={pid=4113 (0x1011), codingType=H265_HEVC")
				.hasMessageContaining("videoFormat=6, frameRate=3, aspectRatio=3");
	}

	@Test
	void clipValidationTreatsUnprovenHdrPlusAsEncodedFalse() throws Exception {
		var source = clip(StreamCodingType.H265_HEVC);
		var output = clip(StreamCodingType.H265_HEVC);
		source.getStreams().getFirst().setHdrPlusFlag(false);
		output.getStreams().getFirst().setHdrPlusFlag(null);
		PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011));
		source.getStreams().getFirst().setHdrPlusFlag(true);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("hdrPlusFlag: source=true, output=null (changed)");
	}

	@Test
	void clipDiagnosticsDistinguishMissingRequiredValuesFromUnsupportedFlags() {
		var source = clip(StreamCodingType.H264_AVC);
		var output = clip(StreamCodingType.H264_AVC);
		source.getStreams().getFirst().setVideoFormat(null);
		output.getStreams().getFirst().setVideoFormat(null);
		source.getStreams().getFirst().setOcFlag(true);
		source.getStreams().getFirst().setCrFlag(true);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("videoFormat: source=null, output=null (required source value is missing)")
				.hasMessageContaining("ocFlag: source=true is unsupported by this pipeline; output=null")
				.hasMessageContaining("crFlag: source=true is unsupported by this pipeline; output=null");
	}

	@Test
	void clipDiagnosticsReportAudioAndLanguageDifferences() {
		var source = clip(StreamCodingType.DOLBY_AC3);
		var output = clip(StreamCodingType.DOLBY_AC3);
		source.getStreams().getFirst().setLanguage("eng");
		source.getStreams().getFirst().setAudioChannelLayout(6);
		source.getStreams().getFirst().setSampleRate(1);
		output.getStreams().getFirst().setLanguage("fra");
		output.getStreams().getFirst().setAudioChannelLayout(3);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class).hasMessageContaining("language: source=eng, output=fra")
				.hasMessageContaining("audioChannelLayout: source=6, output=3")
				.hasMessageContaining("sampleRate: source=1, output=null");
	}

	@Test
	void clipDiagnosticsReportStreamCountUnknownPidAndCodec() {
		var source = clip(StreamCodingType.H264_AVC);
		var output = clip(StreamCodingType.H265_HEVC);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011, 0x1100)))
				.isInstanceOf(IOException.class).hasMessageContaining("expected=2, output=1")
				.hasMessageContaining("sourceStreams=[{pid=4113").hasMessageContaining("outputStreams=[{pid=4113");
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class).hasMessageContaining("CLPI codec/PID changed: retained=true")
				.hasMessageContaining("codingType=H264_AVC").hasMessageContaining("codingType=H265_HEVC");
		output.getStreams().getFirst().setPid(0x1015);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class).hasMessageContaining("unknown PID 4117")
				.hasMessageContaining("outputStream={pid=4117 (0x1015)").hasMessageContaining("sourceStreams=");
	}

	@Test
	void clipDiagnosticsReportEntryPointStateAndExactPresentationTicks() throws Exception {
		var source = clip(StreamCodingType.H264_AVC);
		var output = clip(StreamCodingType.H264_AVC);
		PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011));
		var epMap = output.getEpMap();
		output.setEpMap(null);
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class).hasMessageContaining("output.epMap=null")
				.hasMessageContaining("output.epMap.streamEntryCounts=null");
		output.setEpMap(epMap);
		output.setTsRecordingStartPts(null);
		output.setTsRecordingEndPts(Timestamp.ofTicks(300));
		output.setDuration(Timestamp.ofTicks(200));
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateClip(source, output, Set.of(0x1011)))
				.isInstanceOf(IOException.class)
				.hasMessageContaining("tsRecordingStartPts (90 kHz ticks): source=100, output=null")
				.hasMessageContaining("tsRecordingEndPts (90 kHz ticks): source=200, output=300")
				.hasMessageContaining("duration (90 kHz ticks, expected from source end-start): source=100, output=200")
				.hasMessageContaining("source={tsRecordingStartPts=100, tsRecordingEndPts=200, duration=100}")
				.hasMessageContaining("output={tsRecordingStartPts=null, tsRecordingEndPts=300, duration=200}");
	}

	@Test
	void clipValidationStillAcceptsMatchingOptionalNullMetadata() throws Exception {
		for (var type : List.of(StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC, StreamCodingType.DOLBY_AC3,
				StreamCodingType.PRESENTATION_GRAPHICS))
			PreservingM2tsClipWriter.validateClip(clip(type), clip(type), Set.of(0x1011));
	}

	private static ClipInfo clip(StreamCodingType type) {
		var stream = new ClipStream();
		stream.setPid(0x1011);
		stream.setCodingType(type);
		stream.setVideoFormat(6);
		stream.setFrameRate(3);
		stream.setAspectRatio(3);
		var clip = new ClipInfo();
		clip.setStreams(List.of(stream));
		clip.setTsRecordingStartPts(Timestamp.ofTicks(100));
		clip.setTsRecordingEndPts(Timestamp.ofTicks(200));
		clip.setDuration(Timestamp.ofTicks(100));
		var epStream = new EpMap.EpMapStream();
		epStream.setPid(0x1011);
		epStream.setEntries(List.of(new EpMap.EpMapEntry()));
		var epMap = new EpMap();
		epMap.setStreams(List.of(epStream));
		clip.setEpMap(epMap);
		return clip;
	}

	private static M2tsInfo sourceInfo(M2tsStreamInfo... streams) {
		var info = new M2tsInfo();
		info.setStreams(List.of(streams));
		return info;
	}

	private static M2tsStreamInfo stream(int pid, StreamCodingType type) {
		var stream = new M2tsStreamInfo();
		stream.setPid(pid);
		stream.setCodingType(type);
		stream.setStreamTypeByte(type.getCodingTypeByte());
		return stream;
	}

	private static M2tsDescriptor.StreamEntry video(int pid, StreamCodingType type) {
		var video = new M2tsDescriptor.StreamEntry();
		video.setPid(pid);
		video.setStreamTypeByte(type.getCodingTypeByte());
		return video;
	}

	private static StreamTiming timing(StreamCodingType type, List<Long> pts) {
		return new StreamTiming(0x1100, "stream.es", type, pts.getFirst(), null,
				pts.stream().map(p -> new PesTiming(0, 0, p, null)).toList(), List.of());
	}
}
