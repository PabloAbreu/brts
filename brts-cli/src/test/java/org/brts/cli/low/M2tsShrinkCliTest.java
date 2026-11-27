/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/M2tsShrinkCliTest.java' is part of BRTS.
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

package org.brts.cli.low;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.brts.cli.LowLevelDispatcher;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.ExtractionCache;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.shrink.M2tsShrinkCache;
import org.brts.lowlevel.shrink.M2tsShrinkConfig;
import org.brts.lowlevel.shrink.M2tsShrinkInventory;
import org.brts.lowlevel.shrink.M2tsShrinkPlan;
import org.brts.lowlevel.shrink.M2tsShrinkPlanner;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class M2tsShrinkCliTest {
	@TempDir
	Path temp;

	@Test
	void deferredWritesPlannerReportAndCachedBatchWithoutEncodingOrMuxing() throws Exception {
		var options = conversionOptions();
		options.defer = true;
		options.report = temp.resolve("report.json").toFile();
		AtomicInteger calls = new AtomicInteger();
		var runner = runner(options, calls);
		runner.execute(options);
		assertThat(calls).hasValue(0);
		Path batchPath = findBatch(options.output);
		assertThat(batchPath.getParent().getParent()).isEqualTo(options.output.resolve(".brts-shrink/00001/jobs"));
		var batch = JsonMapperFactory.get().readValue(batchPath.toFile(), BatchRunDescriptor.class);
		assertThat(batch.getSteps()).extracting(step -> step.getCommand()).containsExactly("video-convert",
				"m2ts-create");
		assertThat(batch.getPreflightSteps()).singleElement().satisfies(step -> {
			assertThat(step.getCommand()).isEqualTo("m2ts-extract");
			assertThat(step.toCommandTokens()).contains("--validate-cache-only", "--cache-reference");
		});
		assertThat(ExtractionCache.validate(
				ExtractionCache.reference(options.output.resolve(".brts-shrink/cache/00001/fixture/cache.json"))))
				.isNotNull();
		assertThat(options.output.resolve("BDMV/STREAM/00001.m2ts")).doesNotExist();
		var report = JsonMapperFactory.get().readValue(options.report, M2tsShrinkPlan.class);
		assertThat(report.decision()).isEqualTo(M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED);
	}

	@Test
	void immediateUsesInjectedBatchExecutor() throws Exception {
		var options = conversionOptions();
		AtomicInteger calls = new AtomicInteger();
		runner(options, calls).execute(options);
		assertThat(calls).hasValue(1);
		assertThat(options.output.resolve("BDMV/STREAM/00001.m2ts")).exists();
	}

	@Test
	void videoDecisionRequiresOutputEvenWhenDeferredBeforeCacheResolution() throws Exception {
		var options = conversionOptions();
		options.defer = true;
		var runner = noResolutionRunner();
		options.output = null;
		assertThatThrownBy(() -> runner.execute(options)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("--output is required");
	}

	@Test
	void nonVideoDecisionRequiresOutputBeforeCacheResolution() throws Exception {
		var options = conversionOptions();
		options.targetBytes = 20_000_000;
		options.output = null;
		assertThatThrownBy(() -> noResolutionRunner().execute(options)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("--output is required");
	}

	@Test
	void noVideoDecisionRetainsSelectedCacheWithoutBatchOrEncoding() throws Exception {
		var options = conversionOptions();
		options.targetBytes = 1_300_000;
		options.config.setAudioPids(List.of());
		options.report = temp.resolve("no-video-report.json").toFile();
		AtomicInteger calls = new AtomicInteger();
		runner(options, calls).execute(options);
		assertThat(calls).hasValue(0);
		var report = JsonMapperFactory.get().readValue(options.report, M2tsShrinkPlan.class);
		assertThat(report.decision()).isEqualTo(M2tsShrinkPlan.Decision.AUDIO_PID_REMOVAL_FITS_ESTIMATE);
		assertThat(report.exactDroppedTransportBytes()).isEqualTo(4000 * 192L);
		var cached = ExtractionCache.validate(
				ExtractionCache.reference(options.output.resolve(".brts-shrink/cache/00001/fixture/cache.json")));
		assertThat(cached.manifest().pids()).containsExactly(0x1011);
		assertThat(cached.directory().resolve("pid_1011.h264")).isNotEmptyFile();
		try (var paths = Files.walk(options.output)) {
			assertThat(paths.filter(path -> path.getFileName().toString().equals("batch-run.json")).toList()).isEmpty();
		}
		assertThat(options.output.resolve("BDMV/STREAM/00001.m2ts")).doesNotExist();
	}

	@Test
	void rejectsOutsideWorkspaceBeforeCacheResolution() throws Exception {
		var options = conversionOptions();
		options.workDir = temp.resolve("outside-work");
		assertThatThrownBy(() -> noResolutionRunner().execute(options)).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
	}

	@Test
	void rejectsSourceOutputOverlapBeforeCacheResolution() throws Exception {
		var options = conversionOptions();
		options.output = options.discRoot.resolve("output");
		assertThatThrownBy(() -> noResolutionRunner().execute(options)).isInstanceOf(IOException.class)
				.hasMessageContaining("non-overlapping");
	}

	private M2tsShrinkCli.Shrink noResolutionRunner() {
		return new M2tsShrinkCli.Shrink((root, clip, config, output, work) -> {
			throw new AssertionError("Must reject options before cache/source resolution");
		}, new M2tsShrinkPlanner(), batch -> {
			throw new AssertionError("Must not execute a batch");
		});
	}

	private M2tsShrinkCli.Options conversionOptions() throws Exception {
		var options = new M2tsShrinkCli.Options();
		options.discRoot = temp.resolve("source");
		Files.createDirectories(options.discRoot.resolve("BDMV/STREAM"));
		Files.createDirectories(options.discRoot.resolve("BDMV/CLIPINF"));
		byte[] packets = new byte[10_000 * 192];
		for (int index = 0; index < 10_000; index++) {
			int pid = index < 5000 ? 0x1011 : index < 9000 ? 0x1100 : 0x1FFF;
			int offset = index * 192;
			packets[offset + 4] = 0x47;
			packets[offset + 5] = (byte) (pid >> 8);
			packets[offset + 6] = (byte) pid;
			packets[offset + 7] = 0x10;
		}
		Files.write(options.discRoot.resolve("BDMV/STREAM/00001.m2ts"), packets);
		Files.write(options.discRoot.resolve("BDMV/CLIPINF/00001.clpi"), new byte[1]);
		options.clipId = "00001";
		options.config = new M2tsShrinkConfig();
		options.targetBytes = 1_000_000;
		options.engine = Engine.FFMPEG;
		options.output = temp.resolve("destination");
		return options;
	}

	private M2tsShrinkCli.Shrink runner(M2tsShrinkCli.Options options, AtomicInteger calls) throws Exception {
		Path destination = options.output;
		return new M2tsShrinkCli.Shrink(this::syntheticCache, new M2tsShrinkPlanner(), batch -> {
			calls.incrementAndGet();
			assertThat(batch).exists();
			Path output = destination.resolve("BDMV/STREAM/00001.m2ts");
			Path clpi = destination.resolve("BDMV/CLIPINF/00001.clpi");
			Files.createDirectories(output.getParent());
			Files.createDirectories(clpi.getParent());
			Files.write(output, new byte[192]);
			Files.write(clpi, new byte[1]);
		}, path -> {
			throw new AssertionError("Cache-backed preparation must not reread source metadata");
		});
	}

	/** Real pinned metadata/artifacts; synthetic media is never encoded by these orchestration tests. */
	private M2tsShrinkCache.Prepared syntheticCache(Path root, String clip, M2tsShrinkConfig config, Path output,
			Path work) throws IOException {
		Path source = root.resolve("BDMV/STREAM/" + clip + ".m2ts").toRealPath();
		Path clpi = root.resolve("BDMV/CLIPINF/" + clip + ".clpi").toRealPath();
		var video = new M2tsStreamInfo();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setStreamTypeByte(0x1B);
		video.setVideoFormat(6);
		video.setFrameRateFps(24.0);
		var audio = new M2tsStreamInfo();
		audio.setPid(0x1100);
		audio.setCodingType(StreamCodingType.DOLBY_AC3);
		audio.setStreamTypeByte(0x81);
		audio.setLanguage("eng");
		var info = new M2tsInfo();
		info.setSourcePath(source.toString());
		info.setTotalPackets(10_000);
		info.setStreams(List.of(video, audio));
		var inventory = new M2tsShrinkInventory(source, clpi, Files.size(source), 900_000,
				List.of(new M2tsShrinkInventory.Track(0x1011, StreamCodingType.H264_AVC, null, null, 5000),
						new M2tsShrinkInventory.Track(0x1100, StreamCodingType.DOLBY_AC3, "eng", null, 4000)));
		Path directory = Files.createDirectories(output.resolve(".brts-shrink/cache/" + clip + "/fixture"))
				.toRealPath();
		var timings = new ArrayList<M2tsTimingManifest.StreamTiming>();
		for (var stream : info.getStreams()) {
			if (stream.getCodingType().isAudio() && config.getAudioPids() != null
					&& !config.getAudioPids().contains(stream.getPid()))
				continue;
			String name = stream.getCodingType().isVideo() ? "pid_1011.h264" : "pid_1100.ac3";
			Files.write(directory.resolve(name), new byte[] { 1, 2 });
			timings.add(new M2tsTimingManifest.StreamTiming(stream.getPid(), name, stream.getCodingType(), 0L, null,
					List.of(new M2tsTimingManifest.PesTiming(0, 0, 0L, null)), List.of()));
		}
		M2tsTiming.write(directory.resolve(M2tsTimingManifest.FILE_NAME), new M2tsTimingManifest(2, source.toString(),
				Files.size(source), null, timings, null, Map.of(0x1011, 5000L, 0x1100, 4000L, 0x1FFF, 1000L)));
		Map<String, ExtractionCache.Identity> artifacts = new LinkedHashMap<>();
		var names = new ArrayList<>(timings.stream().map(M2tsTimingManifest.StreamTiming::fileName).toList());
		names.add(M2tsTimingManifest.FILE_NAME);
		for (String name : names) {
			var identity = ExtractionCache.identity(directory.resolve(name));
			artifacts.put(name,
					new ExtractionCache.Identity(name, identity.size(), identity.modified(), identity.fileKey()));
		}
		Path marker = directory.resolve(ExtractionCache.FILE_NAME);
		ExtractionCache.write(marker, new ExtractionCache.Manifest(ExtractionCache.VERSION,
				output.toRealPath().toString(), ExtractionCache.identity(source), ExtractionCache.identity(clpi), info,
				timings.stream().map(M2tsTimingManifest.StreamTiming::pid).sorted().toList(), artifacts, 900_000));
		return new M2tsShrinkCache.Prepared(inventory, ExtractionCache.validate(ExtractionCache.reference(marker)));
	}

	private Path findBatch(Path output) throws IOException {
		try (var paths = Files.walk(output)) {
			var batches = paths.filter(path -> path.getFileName().toString().equals("batch-run.json")).toList();
			assertThat(batches).hasSize(1);
			return batches.getFirst();
		}
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "1234", "123456", "abcde", " 0001" })
	void rejectsInvalidClipIds(String clipId) {
		var options = new M2tsShrinkCli.Options();
		options.clipId = clipId;
		options.targetBytes = 1;
		assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(options, "options"))
				.isInstanceOf(DescriptorValidationException.class)
				.hasMessageContaining("--clip-id must contain exactly five digits");
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1, Long.MIN_VALUE })
	void rejectsNonPositiveTargetBytes(long targetBytes) {
		var options = new M2tsShrinkCli.Options();
		options.clipId = "00001";
		options.targetBytes = targetBytes;
		assertThatThrownBy(() -> DescriptorValidator.validateOrThrow(options, "options"))
				.isInstanceOf(DescriptorValidationException.class)
				.hasMessageContaining("--target-bytes must be positive");
	}

	@Test
	void acceptsValidClipIdAndPositiveTargetBytes() {
		var options = new M2tsShrinkCli.Options();
		options.clipId = "00001";
		options.targetBytes = 1;
		DescriptorValidator.validateOrThrow(options, "options");
	}

	@Test
	void commandIsRegisteredAtLowLevel() {
		assertThat(LowLevelDispatcher.getLevelDispatcher().getRunners())
				.anyMatch(runner -> runner.getCommandName().equals("m2ts-shrink"));
	}

	@Test
	void helpExplainsDeferredBehaviorAndRequiredInputs() {
		var runner = new M2tsShrinkCli.Shrink();
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		runner.printUsage(new PrintStream(bytes));
		String usage = bytes.toString().replaceAll("\\s+", " ");
		assertThat(usage).contains("--defer").contains("--disc-root").contains("--clip-id").contains("--config")
				.contains("--target-bytes").contains("--engine").contains("--output").contains("--work-dir")
				.contains("extraction cache").contains("always required").contains("jobs/<key>");
	}
}
