/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/shrink/BatchWholeM2tsConverterTest.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.SourceFingerprint;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.shrink.WholeM2tsConverter.M2tsConversionRequest;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BatchWholeM2tsConverterTest {
	@TempDir
	Path temp;

	@ParameterizedTest
	@EnumSource(Engine.class)
	void deferredBatchIsAbsoluteThreeStepPipelineWithoutCallingExecutor(Engine engine) throws Exception {
		var request = request();
		AtomicInteger calls = new AtomicInteger();
		var converter = new BatchWholeM2tsConverter(engine, path -> calls.incrementAndGet(), path -> metadata());
		var prepared = converter.prepare(request);
		assertThat(calls).hasValue(0);
		assertThat(prepared.engine()).isEqualTo(engine);
		assertThat(prepared.workspace()).isEqualTo(temp.resolve("output disc/.brts-shrink/00001"));
		assertThat(Files.exists(prepared.workspace().resolve("extracted"))).isFalse();
		assertThat(Files.exists(prepared.outputPath())).isFalse();
		var batch = JsonMapperFactory.get().readValue(prepared.batchDescriptor().toFile(), BatchRunDescriptor.class);
		assertThat(batch.getSteps()).extracting(s -> s.getCommand()).containsExactly("m2ts-extract", "video-convert",
				"m2ts-create");
		assertThat(batch.getSteps().get(0).toCommandTokens()).containsExactly("m2ts-extract", "--input",
				request.inventory().m2tsPath().toString(), "--output",
				prepared.workspace().resolve("extracted").toString(), "--pids", "4113,4354,4608", "--preserve-timing",
				"--source-sha256", SourceFingerprint.sha256(request.inventory().m2tsPath()), "--source-clpi",
				request.inventory().clpiPath().toString(), "--source-clpi-sha256",
				SourceFingerprint.sha256(request.inventory().clpiPath()));
		assertThat(batch.getSteps().get(1).toCommandTokens()).containsSubsequence("--codec", "H264_AVC", "--engine",
				engine.name(), "--bitrate-kbps", Integer.toString(prepared.videoBitrateKbps()), "--overwrite");
		assertThat(batch.getSteps().get(2).toCommandTokens()).containsExactly("m2ts-create", "--descriptor",
				prepared.muxDescriptor().toString(), "--output", prepared.outputPath().getParent().toString(), "--clpi",
				prepared.outputClpi().getParent().toString());
		var mux = JsonMapperFactory.get().readValue(prepared.muxDescriptor().toFile(), M2tsDescriptor.class);
		assertThat(mux.getMaximumOutputBytes()).isEqualTo(1_000_000L);
		assertThat(mux.getSourceClpi()).isEqualTo(request.inventory().clpiPath().toString());
		assertThat(mux.getSourceM2tsSha256()).isEqualTo(SourceFingerprint.sha256(request.inventory().m2tsPath()));
		assertThat(mux.getSourceClpiSha256()).isEqualTo(SourceFingerprint.sha256(request.inventory().clpiPath()));
		assertThat(batch.getSteps().get(1).toCommandTokens()).containsSubsequence("--source-frame-rate", "24/1");
		assertThat(mux.getTimingManifest()).endsWith("/extracted/timing.json");
		assertThat(mux.getStreams()).extracting(s -> s.getPid()).containsExactly(0x1011, 0x1102, 0x1200);
		for (var stream : mux.getStreams())
			assertThat(Path.of(stream.getFile()).isAbsolute()).isTrue();
		assertThat(mux.getStreams().get(2).getFile()).endsWith("/extracted/pid_1200.pgs");
	}

	@Test
	void parserFractionalRateIsPassedAsExactRational() throws Exception {
		var request = request();
		var info = metadata();
		info.getStreams().getFirst().setVfr(0x61);
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> info);
		var prepared = converter.prepare(request);
		var batch = JsonMapperFactory.get().readValue(prepared.batchDescriptor().toFile(), BatchRunDescriptor.class);
		assertThat(batch.getSteps().get(1).toCommandTokens()).containsSubsequence("--source-frame-rate", "24000/1001");
	}

	@Test
	void immediateExecutesPreparedBatchAndInspectsPublishedPair() throws Exception {
		var request = request();
		AtomicInteger calls = new AtomicInteger();
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
			calls.incrementAndGet();
			assertThat(path).isAbsolute().exists();
			Path stream = request.destinationRoot().resolve("BDMV/STREAM/00001.m2ts");
			Path clpi = request.destinationRoot().resolve("BDMV/CLIPINF/00001.clpi");
			Files.createDirectories(stream.getParent());
			Files.createDirectories(clpi.getParent());
			Files.write(stream, new byte[192]);
			Files.write(clpi, new byte[1]);
		}, path -> metadata());
		var result = converter.convert(request);
		assertThat(calls).hasValue(1);
		assertThat(result.outputBytes()).isEqualTo(192);
		assertThat(result.outputClpi()).exists();
	}

	@Test
	void executorFailurePropagatesWithoutPublishing() throws Exception {
		var request = request();
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
			throw new IOException("encoder failed");
		}, path -> metadata());
		assertThatThrownBy(() -> converter.convert(request)).isInstanceOf(IOException.class)
				.hasMessage("encoder failed");
		assertThat(request.destinationRoot().resolve("BDMV/STREAM/00001.m2ts")).doesNotExist();
	}

	@Test
	void missingPairAfterExecutorIsFailure() throws Exception {
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		assertThatThrownBy(() -> converter.convert(request())).isInstanceOf(IOException.class)
				.hasMessageContaining("did not publish");
	}

	@Test
	void rejectsSourceRootAndSymlinkAliasesIncludingOutputSubdirectories() throws Exception {
		var original = request();
		Path sourceRoot = temp.resolve("source disc");
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		for (Path root : List.of(sourceRoot, sourceRoot.resolve("nested"))) {
			assertThatThrownBy(() -> converter.prepare(withRoot(original, root))).isInstanceOf(IOException.class)
					.hasMessageContaining("separate");
		}
		Path alias = Files.createSymbolicLink(temp.resolve("alias"), sourceRoot);
		assertThatThrownBy(() -> converter.prepare(withRoot(original, alias.resolve("new"))))
				.isInstanceOf(IOException.class).hasMessageContaining("separate");
		Files.createDirectories(original.destinationRoot().resolve("BDMV"));
		Files.createSymbolicLink(original.destinationRoot().resolve("BDMV/STREAM"), sourceRoot.resolve("BDMV/STREAM"));
		assertThatThrownBy(() -> converter.prepare(original)).isInstanceOf(IOException.class)
				.hasMessageContaining("aliases");
	}

	@Test
	void rejectsWorkspaceSubdirectoryAliasesAndPreexistingDescriptors() throws Exception {
		var request = request();
		Path work = request.destinationRoot().resolve(".brts-shrink/00001");
		Files.createDirectories(work);
		Files.createSymbolicLink(work.resolve("converted"), temp.resolve("source disc/BDMV/STREAM"));
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IOException.class)
				.hasMessageContaining("symlink");
		Files.delete(work.resolve("converted"));
		Files.createSymbolicLink(work.resolve("m2ts-descriptor.json"), request.inventory().m2tsPath());
		assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IOException.class)
				.hasMessageContaining("already exist");
		assertThat(Files.size(request.inventory().m2tsPath())).isEqualTo(192);
	}

	@Test
	void rejectsPreexistingEitherDestinationWithoutChangingIt() throws Exception {
		var request = request();
		Path clpi = request.destinationRoot().resolve("BDMV/CLIPINF/00001.clpi");
		Files.createDirectories(clpi.getParent());
		Files.writeString(clpi, "existing");
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IOException.class)
				.hasMessageContaining("already exists");
		assertThat(Files.readString(clpi)).isEqualTo("existing");
	}

	@Test
	void rejectsAudioTransformsAndBitrateHints() throws Exception {
		var request = request();
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		request.config().setFixedAudioBitrateKbps(Map.of(0x1102, 640));
		assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("bitrate hints");
		request.config().setFixedAudioBitrateKbps(Map.of());
		request.config().setMinimumAudioBitrateKbps(Map.of(0x1102, 640));
		assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IllegalArgumentException.class);
		request.config().setMinimumAudioBitrateKbps(Map.of());
		request.config().setAudioDowngrades(Map.of(0x1102, M2tsShrinkConfig.AudioDowngrade.DTS_TO_AC3));
		assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void ignoresHevcDolbyVisionCompanionPidDuringPreparation() throws Exception {
		var original = request();
		var tracks = new java.util.ArrayList<>(original.inventory().tracks());
		tracks.removeIf(track -> track.pid() == 0x1011);
		tracks.add(new M2tsShrinkInventory.Track(0x1011, StreamCodingType.H265_HEVC, null, null, 1000));
		tracks.add(new M2tsShrinkInventory.Track(0x1015, StreamCodingType.H265_HEVC, null, null, 100));
		var inventory = new M2tsShrinkInventory(original.inventory().m2tsPath(), original.inventory().clpiPath(),
				original.inventory().sourceBytes(), original.inventory().duration90k(), tracks);
		var plan = new M2tsShrinkPlanner().plan(inventory, "00001", original.config(), 1_000_000, Engine.FFMPEG);
		var request = new M2tsConversionRequest(inventory, plan, original.config(), original.destinationRoot(), null,
				"00001");
		var info = metadata();
		info.setStreams(List.of(stream(0x1011, StreamCodingType.H265_HEVC), stream(0x1015, StreamCodingType.H265_HEVC),
				stream(0x1102, StreamCodingType.DTS), stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS)));
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> info);

		var prepared = converter.prepare(request);
		var batch = JsonMapperFactory.get().readValue(prepared.batchDescriptor().toFile(), BatchRunDescriptor.class);
		var mux = JsonMapperFactory.get().readValue(prepared.muxDescriptor().toFile(), M2tsDescriptor.class);
		assertThat(batch.getSteps().get(0).toCommandTokens()).containsSubsequence("--pids", "4113,4354,4608");
		assertThat(mux.getStreams()).extracting(s -> s.getPid()).containsExactly(0x1011, 0x1102, 0x1200);
		assertThat(batch.getSteps().get(1).toCommandTokens()).containsSubsequence("--codec", "H265_HEVC");
	}

	@Test
	void rejectsOtherMultiVideoCombinations() throws Exception {
		var original = request();
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		for (var extraVideo : List.of(new M2tsShrinkInventory.Track(0x1015, StreamCodingType.H264_AVC, null, null, 1),
				new M2tsShrinkInventory.Track(0x1016, StreamCodingType.H265_HEVC, null, null, 1),
				new M2tsShrinkInventory.Track(0x1015, StreamCodingType.H265_HEVC, null, null, 1))) {
			var tracks = new java.util.ArrayList<>(original.inventory().tracks());
			tracks.add(extraVideo);
			if (extraVideo.pid() == 0x1015 && extraVideo.codingType() == StreamCodingType.H265_HEVC) {
				tracks.add(new M2tsShrinkInventory.Track(0x1016, StreamCodingType.H265_HEVC, null, null, 1));
			}
			var inventory = new M2tsShrinkInventory(original.inventory().m2tsPath(), original.inventory().clpiPath(),
					original.inventory().sourceBytes(), original.inventory().duration90k(), tracks);
			var plan = new M2tsShrinkPlanner().plan(inventory, "00001", original.config(), 1_000_000, Engine.FFMPEG);
			var request = new M2tsConversionRequest(inventory, plan, original.config(), original.destinationRoot(),
					null, "00001");
			assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Exactly one primary");
		}
	}

	@ParameterizedTest
	@EnumSource(value = StreamCodingType.class, names = { "LPCM", "DTS_HD", "TEXT_SUBTITLE", "INTERACTIVE_GRAPHICS",
			"VC1" })
	void rejectsUnsupportedStreams(StreamCodingType type) throws Exception {
		var original = request();
		var tracks = new java.util.ArrayList<>(original.inventory().tracks());
		tracks.add(new M2tsShrinkInventory.Track(0x1300, type, "eng", null, 1));
		var inventory = new M2tsShrinkInventory(original.inventory().m2tsPath(), original.inventory().clpiPath(),
				2_000_000, 900_000, tracks);
		var plan = new M2tsShrinkPlanner().plan(inventory, "00001", original.config(), 1_000_000, Engine.FFMPEG);
		var request = new M2tsConversionRequest(inventory, plan, original.config(), original.destinationRoot(), null,
				"00001");
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void bitrateRoundsDownAtMinimumBoundary() {
		var inventory = budgetInventory(90_000, 0);
		assertThat(ShrinkBitrateBudget.calculate(inventory, budgetPlan(inventory, 137), Set.of(0x1011))).isEqualTo(1);
		assertThatThrownBy(() -> ShrinkBitrateBudget.calculate(inventory, budgetPlan(inventory, 136), Set.of(0x1011)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void bitrateAccountsForRetainedNonvideoButNotRemovedTracks() {
		var inventory = budgetInventory(90_000, 100);
		var plan = budgetPlan(inventory, 100_000);
		assertThat(ShrinkBitrateBudget.calculate(inventory, plan, Set.of(0x1011))).isEqualTo(736);
		assertThat(ShrinkBitrateBudget.calculate(inventory, plan, Set.of(0x1011, 0x1102))).isEqualTo(588);
	}

	@Test
	void bitrateUsesBigIntegerAndCapsAtConverterMaximum() {
		var inventory = budgetInventory(1, 0);
		assertThat(ShrinkBitrateBudget.calculate(inventory, budgetPlan(inventory, Long.MAX_VALUE), Set.of(0x1011)))
				.isEqualTo(40_000);
		var oversizedAudio = budgetInventory(90_000, Long.MAX_VALUE);
		assertThatThrownBy(() -> ShrinkBitrateBudget.calculate(oversizedAudio,
				budgetPlan(oversizedAudio, Long.MAX_VALUE), Set.of(0x1011, 0x1102)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Target cannot");
	}

	@Test
	void bitrateRejectsUnknownDuration() {
		var inventory = budgetInventory(0, 0);
		assertThatThrownBy(() -> ShrinkBitrateBudget.calculate(inventory, budgetPlan(inventory, 1000), Set.of()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duration");
	}

	private M2tsConversionRequest request() throws IOException {
		Path source = temp.resolve("source disc/BDMV/STREAM/00001.m2ts");
		Path clpi = temp.resolve("source disc/BDMV/CLIPINF/00001.clpi");
		Files.createDirectories(source.getParent());
		Files.createDirectories(clpi.getParent());
		Files.write(source, new byte[192]);
		Files.write(clpi, new byte[1]);
		var inventory = new M2tsShrinkInventory(source, clpi, 2_000_000, 900_000, List.of(
				new M2tsShrinkInventory.Track(0x1011, StreamCodingType.H264_AVC, null, null, 1000),
				new M2tsShrinkInventory.Track(0x1102, StreamCodingType.DTS, "eng", null, 100),
				new M2tsShrinkInventory.Track(0x1200, StreamCodingType.PRESENTATION_GRAPHICS, "fra", null, 10)));
		var config = new M2tsShrinkConfig();
		var plan = new M2tsShrinkPlanner().plan(inventory, "00001", config, 1_000_000, Engine.FFMPEG);
		return new M2tsConversionRequest(inventory, plan, config, temp.resolve("output disc"), null, "00001");
	}

	private M2tsConversionRequest withRoot(M2tsConversionRequest request, Path root) {
		return new M2tsConversionRequest(request.inventory(), request.shrinkPlan(), request.config(), root, null,
				"00001");
	}

	private M2tsInfo metadata() {
		var info = new M2tsInfo();
		info.setStreams(List.of(stream(0x1011, StreamCodingType.H264_AVC), stream(0x1102, StreamCodingType.DTS),
				stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS)));
		return info;
	}

	private M2tsStreamInfo stream(int pid, StreamCodingType type) {
		var stream = new M2tsStreamInfo();
		stream.setPid(pid);
		stream.setCodingType(type);
		stream.setStreamTypeByte(type.getCodingTypeByte());
		if (type.isVideo()) {
			stream.setFrameRate(2);
			stream.setVideoFormat(6);
		}
		return stream;
	}

	private M2tsShrinkInventory budgetInventory(long duration, long audioPackets) {
		return new M2tsShrinkInventory(Path.of("source.m2ts"), Path.of("source.clpi"), Long.MAX_VALUE, duration,
				List.of(new M2tsShrinkInventory.Track(0x1011, StreamCodingType.H264_AVC, null, null, 1),
						new M2tsShrinkInventory.Track(0x1102, StreamCodingType.DTS, null, null, audioPackets)));
	}

	private M2tsShrinkPlan budgetPlan(M2tsShrinkInventory inventory, long target) {
		return new M2tsShrinkPlanner().plan(inventory, "00001", new M2tsShrinkConfig(), target, Engine.FFMPEG);
	}
}
