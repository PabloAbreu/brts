/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/VideoConvertCliTest.java' is part of BRTS.
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.brts.cli.LowLevelDispatcher;
import org.brts.cli.low.VideoConvertCli.Convert;
import org.brts.cli.low.VideoConvertCli.ConvertOptions;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.utils.video.FrameRate;
import org.brts.lowlevel.utils.video.VideoConversionParams;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.VideoConversionParams.RateControl;
import org.brts.lowlevel.utils.video.VideoConversionResult;
import org.brts.lowlevel.utils.video.VideoConversionResult.Compliance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

/** Argument/dispatch tests only: no media files, native probes or encoder processes. */
class VideoConvertCliTest {

	private static String[] arguments(String... extra) {
		List<String> args = new ArrayList<>(List.of("--input", "source.264", "--output", "converted.264"));
		args.addAll(List.of(extra));
		return args.toArray(String[]::new);
	}

	private static VideoConversionParams params(String... extra) throws CmdLineException {
		ConvertOptions opts = new ConvertOptions();
		new CmdLineParser(opts).parseArgument(arguments(extra));
		return VideoConvertCli.toParams(opts);
	}

	@Test
	void registeredWithDiscoverableOptions() {
		assertThat(LowLevelDispatcher.getLevelDispatcher().getRunners())
				.anyMatch(runner -> runner instanceof Convert && runner.getCommandName().equals("video-convert"));
		assertThat(new Convert().getOptionsType()).isEqualTo(ConvertOptions.class);
	}

	@Test
	void inheritedHiddenOptionsRemainAccepted() throws Exception {
		ConvertOptions opts = new ConvertOptions();
		new CmdLineParser(opts).parseArgument(arguments("--no-banner", "--error-details"));
		assertThat(opts.isNoBanner()).isTrue();
		assertThat(opts.isErrorDetails()).isTrue();
	}

	@Test
	void requiredPathsAreEnforcedWithoutInvokingRunnerExit() {
		assertThatThrownBy(() -> new CmdLineParser(new ConvertOptions()).parseArgument("--output", "out.264"))
				.isInstanceOf(CmdLineException.class).hasMessageContaining("--input");
		assertThatThrownBy(() -> new CmdLineParser(new ConvertOptions()).parseArgument("--input", "in.264"))
				.isInstanceOf(CmdLineException.class).hasMessageContaining("--output");
	}

	@Test
	void defaultsRetainSourceAndConfigurationPrecedence() throws Exception {
		VideoConversionParams params = params();
		assertThat(params.getInputPath()).isEqualTo(Path.of("source.264"));
		assertThat(params.getOutputPath()).isEqualTo(Path.of("converted.264"));
		assertThat(params.getCodec()).isEqualTo(StreamCodingType.H264_AVC);
		assertThat(params.getEngine()).isNull();
		assertThat(params.getExecutablePath()).isNull();
		assertThat(params.getWidth()).isNull();
		assertThat(params.getHeight()).isNull();
		assertThat(params.getFrameRate()).isNull();
		assertThat(params.getSourceFrameRate()).isNull();
		assertThat(params.getBitrateKbps()).isNull();
		assertThat(params.getRateControl()).isNull();
		assertThat(params.getEngineOptions()).isEmpty();
		assertThat(params.getTimeout()).isNull();
		assertThat(params.isOverwrite()).isFalse();
	}

	@Test
	void mapsEverySuppliedField() throws Exception {
		VideoConversionParams params = params("--codec", "h265_hevc", "--engine", "ffmpeg", "--executable",
				"/custom path/ffmpeg", "--width", "1920", "--height", "1080", "--frame-rate", "23.976",
				"--source-frame-rate", "25", "--bitrate-kbps", "20000", "--rate-control", "cbr", "--engine-option",
				"preset=slow", "--engine-option", "threads=8", "--overwrite", "--timeout-seconds", "1800");
		assertThat(params.getCodec()).isEqualTo(StreamCodingType.H265_HEVC);
		assertThat(params.getEngine()).isEqualTo(Engine.FFMPEG);
		assertThat(params.getExecutablePath()).isEqualTo("/custom path/ffmpeg");
		assertThat(params.getWidth()).isEqualTo(1920);
		assertThat(params.getHeight()).isEqualTo(1080);
		assertThat(params.getFrameRate()).isEqualTo(FrameRate.FPS_23976);
		assertThat(params.getSourceFrameRate()).isEqualTo(FrameRate.FPS_25);
		assertThat(params.getBitrateKbps()).isEqualTo(20000);
		assertThat(params.getRateControl()).isEqualTo(RateControl.CBR);
		assertThat(params.getEngineOptions()).hasSize(2).containsAllEntriesOf(Map.of("preset", "slow", "threads", "8"));
		assertThat(params.isOverwrite()).isTrue();
		assertThat(params.getTimeout()).isEqualTo(Duration.ofMinutes(30));
	}

	@ParameterizedTest
	@EnumSource(Engine.class)
	void acceptsEveryEngineCaseInsensitively(Engine engine) throws Exception {
		assertThat(params("--engine", engine.name().toLowerCase(java.util.Locale.ROOT)).getEngine()).isEqualTo(engine);
	}

	@ParameterizedTest
	@EnumSource(RateControl.class)
	void acceptsEveryRateControlIncludingHyphenatedNames(RateControl mode) throws Exception {
		assertThat(params("--rate-control", mode.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))
				.getRateControl()).isEqualTo(mode);
	}

	@Test
	void qualityOptionsNeedNoSeparateBitrateAndPreserveNativeValues() throws Exception {
		VideoConversionParams params = params("--engine", "NVEncC", "--frame-rate", "24000/1001", "--engine-option",
				"cqp=18:20:22", "--engine-option", "preset=P4", "--engine-option", "device=0");
		assertThat(params.getFrameRate()).isEqualTo(FrameRate.FPS_23976);
		assertThat(params.getBitrateKbps()).isNull();
		assertThat(params.getEngineOptions()).containsEntry("cqp", "18:20:22").containsEntry("preset", "P4")
				.containsEntry("device", "0");
	}

	@Test
	void genericOptionsDoNotShellParseOrApplyEngineAllowlists() throws Exception {
		assertThat(params("--engine-option", "FutureKey= a=b,c:d value ").getEngineOptions())
				.containsExactlyEntriesOf(Map.of("FutureKey", " a=b,c:d value "));
	}

	@ParameterizedTest
	@ValueSource(strings = { "missing", "=value", "key=", " =value", "key= ", "-crf=20", "--preset=slow" })
	void rejectsMalformedEngineOptions(String argument) {
		assertThatThrownBy(() -> params("--engine-option", argument)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("--engine-option");
	}

	@Test
	void rejectsDuplicateOptionsRatherThanSilentlyReplacingThem() {
		assertThatThrownBy(() -> params("--engine-option", "crf=20", "--engine-option", "crf=22"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Duplicate --engine-option key: crf");
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1" })
	void rejectsNonpositiveTimeout(String seconds) {
		assertThatThrownBy(() -> params("--timeout-seconds", seconds)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("--timeout-seconds must be positive");
	}

	@Test
	void rejectsInvalidNamesAndTimingWithContext() {
		assertThatThrownBy(() -> params("--codec", "MPEG2_VIDEO")).hasMessageContaining("--codec");
		assertThatThrownBy(() -> params("--codec", "unknown")).hasMessageContaining("--codec");
		assertThatThrownBy(() -> params("--engine", "unknown")).hasMessageContaining("Unknown video converter engine");
		assertThatThrownBy(() -> params("--rate-control", "unknown")).hasMessageContaining("--rate-control");
		assertThatThrownBy(() -> params("--frame-rate", "invalid")).hasMessageContaining("--frame-rate");
		assertThatThrownBy(() -> params("--source-frame-rate", "0/1")).hasMessageContaining("--source-frame-rate");
	}

	@Test
	void runnerConvertsOncePerRunWithoutRetainingOptionsAndReportsStatus() throws Exception {
		List<VideoConversionParams> calls = new ArrayList<>();
		Convert runner = new Convert(request -> {
			calls.add(request);
			return new VideoConversionResult(request.getOutputPath(), null, List.of(), Compliance.BEST_EFFORT);
		});
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream previous = System.out;
		try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
			System.setOut(capture);
			runner.run(arguments("--engine-option", "crf=20", "--overwrite"));
			runner.run(arguments("--bitrate-kbps", "20000"));
		} finally {
			System.setOut(previous);
		}
		assertThat(calls).hasSize(2);
		assertThat(calls.getFirst().getEngineOptions()).containsEntry("crf", "20");
		assertThat(calls.get(1).getEngineOptions()).isEmpty();
		assertThat(calls.get(1).isOverwrite()).isFalse();
		assertThat(output.toString(StandardCharsets.UTF_8)).contains("converted.264", "BEST_EFFORT");
	}

	@Test
	void helpDoesNotConvertAndListsAllOptions() throws Exception {
		Convert runner = new Convert(request -> {
			throw new AssertionError("Help must not convert");
		});
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream previous = System.err;
		try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
			System.setErr(capture);
			runner.run(new String[] { "--help" });
		} finally {
			System.setErr(previous);
		}
		assertThat(output.toString(StandardCharsets.UTF_8)).contains("video-convert", "--input", "--output", "--codec",
				"--engine", "--executable", "--width", "--height", "--frame-rate", "--source-frame-rate",
				"--bitrate-kbps", "--rate-control", "--engine-option", "--overwrite", "--timeout-seconds");
		assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("--no-banner", "--error-details");
	}

	@Test
	void mappingFailsBeforeConversionAndConverterFailuresPropagate() {
		List<VideoConversionParams> calls = new ArrayList<>();
		IOException failure = new IOException("Encoder unavailable");
		Convert runner = new Convert(request -> {
			calls.add(request);
			throw failure;
		});
		assertThatThrownBy(() -> runner.run(arguments("--engine-option", "bad")))
				.hasMessageContaining("--engine-option");
		assertThat(calls).isEmpty();
		assertThatThrownBy(() -> runner.run(arguments("--bitrate-kbps", "20000"))).isSameAs(failure);
		assertThat(calls).hasSize(1);
	}
}
