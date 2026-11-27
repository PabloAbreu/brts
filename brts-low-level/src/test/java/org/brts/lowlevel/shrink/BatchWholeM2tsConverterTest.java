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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.ExtractionCache;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.SourceFingerprint;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.shrink.WholeM2tsConverter.M2tsConversionRequest;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class BatchWholeM2tsConverterTest {
	@TempDir
	Path temp;

	@ParameterizedTest
	@EnumSource(Engine.class)
	void deferredBatchIsAbsoluteThreeStepPipelineWithoutCallingExecutor(Engine engine) throws Exception {
		var request = request();
		assertThat(request.extractionCache()).isNull();
		AtomicInteger calls = new AtomicInteger();
		var converter = new BatchWholeM2tsConverter(engine, path -> calls.incrementAndGet(), path -> metadata());
		var prepared = converter.prepare(request);
		assertThat(calls).hasValue(0);
		assertThat(prepared.engine()).isEqualTo(engine);
		assertThat(prepared.workspace()).isEqualTo(temp.resolve("output disc/.brts-shrink/00001"));
		assertThat(Files.exists(prepared.workspace().resolve("extracted"))).isFalse();
		assertThat(Files.exists(prepared.outputPath())).isFalse();
		var batch = JsonMapperFactory.get().readValue(prepared.batchDescriptor().toFile(), BatchRunDescriptor.class);
		assertThat(batch.getPreflightSteps()).isEmpty();
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
				"00001", true);
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

	@ParameterizedTest
	@EnumSource(value = StreamCodingType.class, names = { "DOLBY_TRUEHD", "DTS_HD", "DTS_HD_MASTER_AUDIO",
			"DTS_EXPRESS" })
	void preparesBluRayDolbyAndDtsVariants(StreamCodingType type) throws Exception {
		var original = request();
		var tracks = new java.util.ArrayList<>(original.inventory().tracks());
		tracks.add(new M2tsShrinkInventory.Track(0x1300, type, "eng", null, 50));
		var inventory = new M2tsShrinkInventory(original.inventory().m2tsPath(), original.inventory().clpiPath(),
				original.inventory().sourceBytes(), original.inventory().duration90k(), tracks);
		var plan = new M2tsShrinkPlanner().plan(inventory, "00001", original.config(), 1_000_000, Engine.FFMPEG);
		var request = new M2tsConversionRequest(inventory, plan, original.config(), original.destinationRoot(), null,
				"00001", true);
		var info = metadata();
		var streams = new java.util.ArrayList<>(info.getStreams());
		streams.add(stream(0x1300, type));
		info.setStreams(streams);
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> info);

		var prepared = converter.prepare(request);
		var batch = JsonMapperFactory.get().readValue(prepared.batchDescriptor().toFile(), BatchRunDescriptor.class);
		var mux = JsonMapperFactory.get().readValue(prepared.muxDescriptor().toFile(), M2tsDescriptor.class);
		assertThat(batch.getSteps().getFirst().toCommandTokens()).containsSubsequence("--pids", "4113,4354,4864,4608");
		assertThat(mux.getStreams()).anySatisfy(entry -> {
			assertThat(entry.getPid()).isEqualTo(0x1300);
			assertThat(entry.getStreamTypeByte()).isEqualTo(type.getCodingTypeByte());
		});
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
					null, "00001", true);
			assertThatThrownBy(() -> converter.prepare(request)).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("Exactly one primary");
		}
	}

	@ParameterizedTest
	@EnumSource(value = StreamCodingType.class, names = { "LPCM", "TEXT_SUBTITLE", "INTERACTIVE_GRAPHICS", "VC1" })
	void rejectsUnsupportedStreams(StreamCodingType type) throws Exception {
		var original = request();
		var tracks = new java.util.ArrayList<>(original.inventory().tracks());
		tracks.add(new M2tsShrinkInventory.Track(0x1300, type, "eng", null, 1));
		var inventory = new M2tsShrinkInventory(original.inventory().m2tsPath(), original.inventory().clpiPath(),
				2_000_000, 900_000, tracks);
		var plan = new M2tsShrinkPlanner().plan(inventory, "00001", original.config(), 1_000_000, Engine.FFMPEG);
		var request = new M2tsConversionRequest(inventory, plan, original.config(), original.destinationRoot(), null,
				"00001", true);
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

	@ParameterizedTest
	@EnumSource(Engine.class)
	void cachedPreparationReusesPinnedMetadataAndArtifactsWithValidationPreflight(Engine engine) throws Exception {
		var request = cachedRequest(false);
		AtomicInteger executions = new AtomicInteger();
		var converter = cachedConverter(engine, executions);
		var cache = ExtractionCache.validate(request.extractionCache());
		var sourceIdentity = ExtractionCache.identity(request.inventory().m2tsPath());
		var timingIdentity = ExtractionCache.identity(cache.directory().resolve("timing.json"));
		var prepared = converter.prepare(request);
		assertThat(executions).hasValue(0);
		assertThat(prepared.workspace().getParent())
				.isEqualTo(request.destinationRoot().resolve(".brts-shrink/00001/jobs"));
		assertThat(prepared.workspace().getFileName().toString()).matches("[0-9a-f]{64}");
		assertThat(prepared.workspace().resolve("extracted")).doesNotExist();
		assertThat(prepared.workspace().resolve("converted")).isDirectory();
		assertThat(prepared.outputPath()).doesNotExist();
		assertThat(prepared.outputClpi()).doesNotExist();
		Path referenceFile = prepared.workspace().resolve("cache-reference.json");
		assertThat(JsonMapperFactory.get().readValue(referenceFile.toFile(), ExtractionCache.Reference.class))
				.isEqualTo(request.extractionCache());
		var batch = JsonMapperFactory.get().readValue(prepared.batchDescriptor().toFile(), BatchRunDescriptor.class);
		assertThat(batch.getPreflightSteps()).hasSize(1);
		assertThat(batch.getPreflightSteps().getFirst().toCommandTokens()).containsExactly("m2ts-extract", "--input",
				request.inventory().m2tsPath().toString(), "--output", cache.directory().toString(),
				"--preserve-timing", "--cache-reference", referenceFile.toString(), "--validate-cache-only");
		assertThat(batch.getSteps()).extracting(s -> s.getCommand()).containsExactly("video-convert", "m2ts-create");
		assertThat(batch.getSteps().getFirst().toCommandTokens()).containsSubsequence("--input",
				cache.directory().resolve("cached_1011.264").toString(), "--output",
				prepared.workspace().resolve("converted/cached_1011.264").toString(), "--codec", "H264_AVC", "--engine",
				engine.name(), "--bitrate-kbps", Integer.toString(prepared.videoBitrateKbps()), "--source-frame-rate",
				"24/1");
		assertThat(batch.getSteps().getLast().toCommandTokens()).containsExactly("m2ts-create", "--descriptor",
				prepared.muxDescriptor().toString(), "--output", prepared.outputPath().getParent().toString(), "--clpi",
				prepared.outputClpi().getParent().toString());
		var mux = JsonMapperFactory.get().readValue(prepared.muxDescriptor().toFile(), M2tsDescriptor.class);
		assertThat(mux.getExtractionCacheReference()).isEqualTo(request.extractionCache());
		assertThat(mux.getTimingManifest()).isEqualTo(cache.directory().resolve("timing.json").toString());
		assertThat(mux.getSourceM2tsSha256()).isNull();
		assertThat(mux.getSourceClpiSha256()).isNull();
		assertThat(mux.getMaximumOutputBytes()).isEqualTo(request.shrinkPlan().targetBytes());
		assertThat(mux.getStreams()).extracting(s -> s.getPid()).containsExactly(0x1011, 0x1102, 0x1200);
		assertThat(mux.getStreams().get(1).getFile())
				.isEqualTo(cache.directory().resolve("cached_1102.dts").toString());
		assertThat(mux.getStreams().get(2).getFile())
				.isEqualTo(cache.directory().resolve("cached_1200.pgs").toString());
		assertThat(ExtractionCache.identity(request.inventory().m2tsPath())).isEqualTo(sourceIdentity);
		assertThat(ExtractionCache.identity(cache.directory().resolve("timing.json"))).isEqualTo(timingIdentity);
		assertThat(ExtractionCache.validate(request.extractionCache()).reference())
				.isEqualTo(request.extractionCache());
	}

	@Test
	void cachedShaOptInPinsHashesInDescriptorWithoutMutatingUnhashedTiming() throws Exception {
		var request = cachedRequest(true);
		var cache = ExtractionCache.validate(request.extractionCache());
		assertThat(cache.timing().sourceSha256()).isNull();
		byte[] timing = Files.readAllBytes(cache.directory().resolve("timing.json"));
		var prepared = cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(request);
		var mux = JsonMapperFactory.get().readValue(prepared.muxDescriptor().toFile(), M2tsDescriptor.class);
		assertThat(mux.getSourceM2tsSha256()).isEqualTo(SourceFingerprint.sha256(request.inventory().m2tsPath()));
		assertThat(mux.getSourceClpiSha256()).isEqualTo(SourceFingerprint.sha256(request.inventory().clpiPath()));
		assertThat(Files.readAllBytes(cache.directory().resolve("timing.json"))).containsExactly(timing);
		ExtractionCache.validate(request.extractionCache());
	}

	@Test
	void cachedJobKeyIsStableAndSeparatesTargetsEnginesAndHashPolicy() throws Exception {
		var request = cachedRequest(false);
		var converter = cachedConverter(Engine.FFMPEG, new AtomicInteger());
		var prepared = converter.prepare(request);
		Files.delete(prepared.batchDescriptor());
		Files.delete(prepared.muxDescriptor());
		Files.delete(prepared.workspace().resolve("cache-reference.json"));
		assertThat(converter.prepare(request).workspace()).isEqualTo(prepared.workspace());
		var otherPlan = new M2tsShrinkPlanner().plan(request.inventory(), "00001", request.config(), 90_000,
				Engine.FFMPEG);
		var otherTarget = new M2tsConversionRequest(request.inventory(), otherPlan, request.config(),
				request.destinationRoot(), null, "00001", false, request.extractionCache());
		var targetJob = converter.prepare(otherTarget);
		assertThat(targetJob.workspace()).isNotEqualTo(prepared.workspace());
		for (Engine engine : Engine.values()) {
			if (engine != Engine.FFMPEG) {
				assertThat(cachedConverter(engine, new AtomicInteger()).prepare(request).workspace())
						.isNotEqualTo(prepared.workspace()).isNotEqualTo(targetJob.workspace());
			}
		}
		var hashed = new M2tsConversionRequest(request.inventory(), request.shrinkPlan(), request.config(),
				request.destinationRoot(), null, "00001", true, request.extractionCache());
		assertThat(converter.prepare(hashed).workspace()).isNotEqualTo(prepared.workspace());
		var explicitSelection = new M2tsShrinkConfig();
		explicitSelection.setAudioPids(List.of(0x1102));
		explicitSelection.setSubtitlePids(List.of(0x1200));
		var configured = new M2tsConversionRequest(request.inventory(), request.shrinkPlan(), explicitSelection,
				request.destinationRoot(), null, "00001", false, request.extractionCache());
		assertThat(converter.prepare(configured).workspace()).isNotEqualTo(prepared.workspace());
	}

	@Test
	void cachedExplicitWorkspaceIsUsedAndDescriptorsCannotBeOverwritten() throws Exception {
		var request = cachedRequest(false);
		Path work = request.destinationRoot().resolve("explicit job");
		var explicit = withWorkspace(request, work);
		var converter = cachedConverter(Engine.FFMPEG, new AtomicInteger());
		var prepared = converter.prepare(explicit);
		assertThat(prepared.workspace()).isEqualTo(work);
		byte[] mux = Files.readAllBytes(prepared.muxDescriptor());
		byte[] batch = Files.readAllBytes(prepared.batchDescriptor());
		byte[] pin = Files.readAllBytes(work.resolve("cache-reference.json"));
		assertThatThrownBy(() -> converter.prepare(explicit)).isInstanceOf(IOException.class)
				.hasMessageContaining("already exist");
		assertThat(Files.readAllBytes(prepared.muxDescriptor())).containsExactly(mux);
		assertThat(Files.readAllBytes(prepared.batchDescriptor())).containsExactly(batch);
		assertThat(Files.readAllBytes(work.resolve("cache-reference.json"))).containsExactly(pin);
	}

	@ParameterizedTest
	@ValueSource(strings = { "cache-reference.json", "m2ts-descriptor.json", "batch-run.json" })
	void cachedPreparationRejectsAnyExistingDescriptorIncludingDanglingSymlinks(String file) throws Exception {
		var request = cachedRequest(false);
		Path work = request.destinationRoot().resolve("explicit");
		Files.createDirectories(work);
		Files.createSymbolicLink(work.resolve(file), temp.resolve("missing"));
		assertThatThrownBy(
				() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(withWorkspace(request, work)))
				.isInstanceOf(IOException.class).hasMessageContaining("already exist");
		assertThat(work.resolve("converted")).doesNotExist();
		assertThat(Files.isSymbolicLink(work.resolve(file))).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "source", "clpi", "timing.json", "cached_1102.dts", "cache.json" })
	void staleCacheFailsBeforeJobDirectoryCreationOrExecutor(String artifact) throws Exception {
		var request = cachedRequest(false);
		Path directory = Path.of(request.extractionCache().manifest()).getParent();
		Path path = switch (artifact) {
		case "source" -> request.inventory().m2tsPath();
		case "clpi" -> request.inventory().clpiPath();
		default -> directory.resolve(artifact);
		};
		Files.setLastModifiedTime(path, FileTime.fromMillis(Files.getLastModifiedTime(path).toMillis() + 10_000));
		AtomicInteger executions = new AtomicInteger();
		assertThatThrownBy(() -> cachedConverter(Engine.FFMPEG, executions).convert(request))
				.isInstanceOf(IOException.class).hasMessageContaining("Invalid extraction cache");
		assertThat(executions).hasValue(0);
		assertThat(request.destinationRoot().resolve(".brts-shrink/00001")).doesNotExist();
		assertThat(request.destinationRoot().resolve("BDMV")).doesNotExist();
	}

	@ParameterizedTest
	@ValueSource(strings = { "bytes", "duration", "count", "language", "bitrate", "codec", "duplicate", "missing" })
	void cachedPreparationRejectsInconsistentInventoryBeforeWriting(String mismatch) throws Exception {
		var request = cachedRequest(false);
		var inventory = request.inventory();
		var tracks = new ArrayList<>(inventory.tracks());
		var audio = tracks.get(1);
		switch (mismatch) {
		case "count" -> tracks.set(1, new M2tsShrinkInventory.Track(audio.pid(), audio.codingType(), audio.language(),
				audio.declaredBitrateKbps(), audio.sourcePacketCount() + 1));
		case "language" -> tracks.set(1, new M2tsShrinkInventory.Track(audio.pid(), audio.codingType(), "deu",
				audio.declaredBitrateKbps(), audio.sourcePacketCount()));
		case "bitrate" -> tracks.set(1, new M2tsShrinkInventory.Track(audio.pid(), audio.codingType(), audio.language(),
				768, audio.sourcePacketCount()));
		case "codec" -> tracks.set(1, new M2tsShrinkInventory.Track(audio.pid(), StreamCodingType.DOLBY_AC3,
				audio.language(), audio.declaredBitrateKbps(), audio.sourcePacketCount()));
		case "duplicate" -> tracks.add(audio);
		case "missing" -> tracks.remove(1);
		default -> {
		}
		}
		var changed = new M2tsShrinkInventory(inventory.m2tsPath(), inventory.clpiPath(),
				inventory.sourceBytes() + (mismatch.equals("bytes") ? 192 : 0),
				inventory.duration90k() + (mismatch.equals("duration") ? 1 : 0), tracks);
		var changedRequest = new M2tsConversionRequest(changed, request.shrinkPlan(), request.config(),
				request.destinationRoot(), null, "00001", false, request.extractionCache());
		assertThatThrownBy(() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(changedRequest))
				.isInstanceOf(IOException.class).hasMessageContaining("cache");
		assertThat(request.destinationRoot().resolve(".brts-shrink/00001")).doesNotExist();
		assertThat(request.destinationRoot().resolve("BDMV")).doesNotExist();
	}

	@ParameterizedTest
	@ValueSource(strings = { "source", "clpi", "target" })
	void cachedPreparationRequiresExactSourcePairAndTargetRoot(String mismatch) throws Exception {
		var request = cachedRequest(false);
		var inventory = request.inventory();
		Path source = inventory.m2tsPath();
		Path clpi = inventory.clpiPath();
		Path root = request.destinationRoot();
		if (mismatch.equals("target")) {
			root = temp.resolve("different output");
		} else {
			Path other = temp.resolve("other disc/BDMV")
					.resolve(mismatch.equals("source") ? "STREAM/00001.m2ts" : "CLIPINF/00001.clpi");
			Files.createDirectories(other.getParent());
			Files.copy(mismatch.equals("source") ? source : clpi, other);
			if (mismatch.equals("source"))
				source = other;
			else
				clpi = other;
		}
		var changed = new M2tsShrinkInventory(source, clpi, inventory.sourceBytes(), inventory.duration90k(),
				inventory.tracks());
		var changedRequest = new M2tsConversionRequest(changed, request.shrinkPlan(), request.config(), root, null,
				"00001", false, request.extractionCache());
		assertThatThrownBy(() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(changedRequest))
				.isInstanceOf(IOException.class).hasMessageContaining("identity mismatch");
		assertThat(temp.resolve("different output")).doesNotExist();
		assertThat(request.destinationRoot().resolve(".brts-shrink/00001")).doesNotExist();
	}

	@Test
	void cachedPreparationRequiresExactCoverageForBothPlanAndConfig() throws Exception {
		var request = cachedRequest(false);
		var config = new M2tsShrinkConfig();
		config.setAudioPids(List.of());
		var plan = new M2tsShrinkPlanner().plan(request.inventory(), "00001", config, 100_000, Engine.FFMPEG);
		var extraCoverage = new M2tsConversionRequest(request.inventory(), plan, config, request.destinationRoot(),
				null, "00001", false, request.extractionCache());
		var stalePlan = new M2tsConversionRequest(request.inventory(), request.shrinkPlan(), config,
				request.destinationRoot(), null, "00001", false, request.extractionCache());
		for (var invalid : List.of(extraCoverage, stalePlan)) {
			assertThatThrownBy(() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(invalid))
					.isInstanceOf(IOException.class).hasMessageContaining("PID coverage");
		}
		assertThat(request.destinationRoot().resolve(".brts-shrink/00001")).doesNotExist();
	}

	@Test
	void cachedPreparationRejectsMissingSelectedPidEvenForOtherwiseValidCache() throws Exception {
		var request = cachedRequest(false, List.of(0x1011, 0x1200));
		ExtractionCache.validate(request.extractionCache());
		assertThatThrownBy(() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(request))
				.isInstanceOf(IOException.class).hasMessageContaining("PID coverage");
		assertThat(request.destinationRoot().resolve(".brts-shrink/00001")).doesNotExist();
	}

	@Test
	void cachedPreparationHonorsEmptySelectionAndStillChecksRemovedTrackCounts() throws Exception {
		var original = cachedRequest(false, List.of(0x1011));
		var config = new M2tsShrinkConfig();
		config.setAudioPids(List.of());
		config.setSubtitlePids(List.of());
		var plan = new M2tsShrinkPlanner().plan(original.inventory(), "00001", config, 100_000, Engine.FFMPEG);
		var request = new M2tsConversionRequest(original.inventory(), plan, config, original.destinationRoot(), null,
				"00001", false, original.extractionCache());
		var prepared = cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(request);
		var descriptor = JsonMapperFactory.get().readValue(prepared.muxDescriptor().toFile(), M2tsDescriptor.class);
		assertThat(descriptor.getStreams()).extracting(s -> s.getPid()).containsExactly(0x1011);
		var tracks = new ArrayList<>(request.inventory().tracks());
		var audio = tracks.get(1);
		tracks.set(1, new M2tsShrinkInventory.Track(audio.pid(), audio.codingType(), audio.language(),
				audio.declaredBitrateKbps(), audio.sourcePacketCount() + 1));
		var inventory = new M2tsShrinkInventory(request.inventory().m2tsPath(), request.inventory().clpiPath(),
				request.inventory().sourceBytes(), request.inventory().duration90k(), tracks);
		Path otherWork = request.destinationRoot().resolve("bad counts");
		var invalid = new M2tsConversionRequest(inventory, plan, config, request.destinationRoot(), otherWork, "00001",
				false, request.extractionCache());
		assertThatThrownBy(() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(invalid))
				.isInstanceOf(IOException.class).hasMessageContaining("metadata/counts");
		assertThat(otherWork).doesNotExist();
	}

	@Test
	void legacyAndCachedWorkspacesMustStayInsideTargetIncludingSymlinkAncestors() throws Exception {
		var legacy = request();
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
		}, path -> metadata());
		Path outside = temp.resolve("outside work");
		assertThatThrownBy(() -> converter.prepare(withWorkspace(legacy, outside))).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
		assertThat(legacy.destinationRoot()).doesNotExist();
		assertThat(outside).doesNotExist();
		var cached = cachedRequest(false);
		assertThatThrownBy(
				() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(withWorkspace(cached, outside)))
				.isInstanceOf(IOException.class).hasMessageContaining("inside target root");
		Files.createDirectories(outside);
		Path link = Files.createSymbolicLink(cached.destinationRoot().resolve("escape"), outside);
		for (var request : List.of(legacy, cached)) {
			assertThatThrownBy(() -> converter.prepare(withWorkspace(request, link.resolve("job"))))
					.isInstanceOf(IOException.class).hasMessageContaining("inside target root");
		}
		assertThat(outside.resolve("job")).doesNotExist();
	}

	@Test
	void cachedWorkspaceCannotOverlapImmutableCache() throws Exception {
		var request = cachedRequest(false);
		Path directory = Path.of(request.extractionCache().manifest()).getParent();
		for (Path work : List.of(directory, directory.resolve("job"), directory.getParent())) {
			assertThatThrownBy(
					() -> cachedConverter(Engine.FFMPEG, new AtomicInteger()).prepare(withWorkspace(request, work)))
					.isInstanceOf(IOException.class).hasMessageContaining("separate from the immutable");
		}
		assertThat(directory.resolve("job")).doesNotExist();
	}

	@Test
	void cachedImmediateExecutionUsesSameTwoStepBatch() throws Exception {
		var request = cachedRequest(false);
		AtomicInteger executions = new AtomicInteger();
		var converter = new BatchWholeM2tsConverter(Engine.FFMPEG, path -> {
			executions.incrementAndGet();
			var batch = JsonMapperFactory.get().readValue(path.toFile(), BatchRunDescriptor.class);
			assertThat(batch.getPreflightSteps()).hasSize(1);
			assertThat(batch.getSteps()).extracting(s -> s.getCommand()).containsExactly("video-convert",
					"m2ts-create");
			Files.write(request.destinationRoot().resolve("BDMV/STREAM/00001.m2ts"), new byte[192]);
			Files.write(request.destinationRoot().resolve("BDMV/CLIPINF/00001.clpi"), new byte[1]);
		}, path -> {
			throw new AssertionError("Cached preparation must not parse source metadata");
		});
		assertThat(converter.convert(request).outputBytes()).isEqualTo(192);
		assertThat(executions).hasValue(1);
		ExtractionCache.validate(request.extractionCache());
	}

	private BatchWholeM2tsConverter cachedConverter(Engine engine, AtomicInteger executions) {
		return new BatchWholeM2tsConverter(engine, path -> executions.incrementAndGet(), path -> {
			throw new AssertionError("Cached preparation must not parse source metadata");
		});
	}

	private M2tsConversionRequest withWorkspace(M2tsConversionRequest request, Path workspace) {
		return new M2tsConversionRequest(request.inventory(), request.shrinkPlan(), request.config(),
				request.destinationRoot(), workspace, request.clipId(), request.computeSha256(),
				request.extractionCache());
	}

	/** Deliberately invalid transport bytes: preparation must use the persisted metadata, never a transport probe. */
	private M2tsConversionRequest cachedRequest(boolean hashes) throws IOException {
		return cachedRequest(hashes, List.of(0x1011, 0x1102, 0x1200));
	}

	private M2tsConversionRequest cachedRequest(boolean hashes, List<Integer> selected) throws IOException {
		var original = request();
		Files.write(original.inventory().m2tsPath(), new byte[1112 * 192]);
		var inventory = new M2tsShrinkInventory(original.inventory().m2tsPath().toRealPath(),
				original.inventory().clpiPath().toRealPath(), 1112 * 192L, original.inventory().duration90k(),
				original.inventory().tracks());
		var info = metadata();
		info.setSourcePath(inventory.m2tsPath().toString());
		info.setTotalPackets(1112);
		for (var stream : info.getStreams()) {
			stream.setLanguage(inventory.track(stream.getPid()).language());
		}
		Path root = original.destinationRoot();
		Path directory = root.resolve(".brts-shrink/cache/00001/fixture");
		Files.createDirectories(directory);
		List<M2tsTimingManifest.StreamTiming> streams = new ArrayList<>();
		Map<String, ExtractionCache.Identity> artifacts = new LinkedHashMap<>();
		for (var stream : info.getStreams()) {
			if (!selected.contains(stream.getPid())) {
				continue;
			}
			String extension = stream.getCodingType().isVideo() ? "264"
					: stream.getCodingType().isAudio() ? "dts" : "pgs";
			String name = String.format("cached_%04x.%s", stream.getPid(), extension);
			Files.write(directory.resolve(name), new byte[] { 1, 2, 3 });
			streams.add(new M2tsTimingManifest.StreamTiming(stream.getPid(), name, stream.getCodingType(), 90_000L,
					null, List.of(new M2tsTimingManifest.PesTiming(0, 0, 90_000L, null)), List.of()));
			var identity = ExtractionCache.identity(directory.resolve(name));
			artifacts.put(name,
					new ExtractionCache.Identity(name, identity.size(), identity.modified(), identity.fileKey()));
		}
		var timing = new M2tsTimingManifest(2, inventory.m2tsPath().toString(), inventory.sourceBytes(), 90_000L,
				streams, null, Map.of(0x1011, 1000L, 0x1102, 100L, 0x1200, 10L, 0, 2L));
		M2tsTiming.write(directory.resolve("timing.json"), timing);
		var timingIdentity = ExtractionCache.identity(directory.resolve("timing.json"));
		artifacts.put("timing.json", new ExtractionCache.Identity("timing.json", timingIdentity.size(),
				timingIdentity.modified(), timingIdentity.fileKey()));
		var manifest = new ExtractionCache.Manifest(ExtractionCache.VERSION, root.toRealPath().toString(),
				ExtractionCache.identity(inventory.m2tsPath()), ExtractionCache.identity(inventory.clpiPath()), info,
				selected, artifacts, inventory.duration90k());
		ExtractionCache.write(directory.resolve(ExtractionCache.FILE_NAME), manifest);
		var reference = ExtractionCache.reference(directory.resolve(ExtractionCache.FILE_NAME));
		ExtractionCache.validate(reference);
		var plan = new M2tsShrinkPlanner().plan(inventory, "00001", original.config(), 100_000, Engine.FFMPEG);
		return new M2tsConversionRequest(inventory, plan, original.config(), root, null, "00001", hashes, reference);
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
		return new M2tsConversionRequest(inventory, plan, config, temp.resolve("output disc"), null, "00001", true);
	}

	private M2tsConversionRequest withRoot(M2tsConversionRequest request, Path root) {
		return new M2tsConversionRequest(request.inventory(), request.shrinkPlan(), request.config(), root, null,
				"00001", true);
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
