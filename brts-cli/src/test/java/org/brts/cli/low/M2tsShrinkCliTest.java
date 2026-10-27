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
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.brts.cli.LowLevelDispatcher;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.DescriptorValidator;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
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
	void deferredWritesPlannerReportAndBatchWithoutExecutingMedia() throws Exception {
		var options = conversionOptions();
		options.defer = true;
		options.report = temp.resolve("report.json").toFile();
		AtomicInteger calls = new AtomicInteger();
		var runner = runner(options, calls);
		runner.execute(options);
		assertThat(calls).hasValue(0);
		assertThat(options.output.resolve(".brts-shrink/00001/batch-run.json")).exists();
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
	void videoDecisionRequiresOutputEvenWhenDeferred() throws Exception {
		var options = conversionOptions();
		options.defer = true;
		var runner = runner(options, new AtomicInteger());
		options.output = null;
		assertThatThrownBy(() -> runner.execute(options)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("--output is required");
	}

	@Test
	void nonVideoDecisionRemainsAnalysisOnlyWithoutOutput() throws Exception {
		var options = conversionOptions();
		options.targetBytes = 20_000_000;
		AtomicInteger calls = new AtomicInteger();
		var runner = runner(options, calls);
		options.output = null;
		runner.execute(options);
		assertThat(calls).hasValue(0);
	}

	private M2tsShrinkCli.Options conversionOptions() throws Exception {
		var options = new M2tsShrinkCli.Options();
		options.discRoot = temp.resolve("source");
		Files.createDirectories(options.discRoot.resolve("BDMV/STREAM"));
		Files.createDirectories(options.discRoot.resolve("BDMV/CLIPINF"));
		Files.write(options.discRoot.resolve("BDMV/STREAM/00001.m2ts"), new byte[192]);
		Files.write(options.discRoot.resolve("BDMV/CLIPINF/00001.clpi"), new byte[1]);
		options.clipId = "00001";
		options.config = new M2tsShrinkConfig();
		options.targetBytes = 1_000_000;
		options.engine = Engine.FFMPEG;
		options.output = temp.resolve("destination");
		return options;
	}

	private M2tsShrinkCli.Shrink runner(M2tsShrinkCli.Options options, AtomicInteger calls) throws Exception {
		var inventory = new M2tsShrinkInventory(options.discRoot.resolve("BDMV/STREAM/00001.m2ts"),
				options.discRoot.resolve("BDMV/CLIPINF/00001.clpi"), 2_000_000, 900_000,
				List.of(new M2tsShrinkInventory.Track(0x1011, StreamCodingType.H264_AVC, null, null, 1000)));
		var stream = new M2tsStreamInfo();
		stream.setPid(0x1011);
		stream.setCodingType(StreamCodingType.H264_AVC);
		stream.setStreamTypeByte(0x1B);
		stream.setVideoFormat(6);
		stream.setFrameRateFps(24.0);
		var info = new M2tsInfo();
		info.setStreams(List.of(stream));
		Path destination = options.output;
		return new M2tsShrinkCli.Shrink((root, clip) -> inventory, new M2tsShrinkPlanner(), batch -> {
			calls.incrementAndGet();
			assertThat(batch).exists();
			Path output = destination.resolve("BDMV/STREAM/00001.m2ts");
			Path clpi = destination.resolve("BDMV/CLIPINF/00001.clpi");
			Files.createDirectories(output.getParent());
			Files.createDirectories(clpi.getParent());
			Files.write(output, new byte[192]);
			Files.write(clpi, new byte[1]);
		}, path -> info);
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
		String usage = bytes.toString();
		assertThat(usage).contains("--defer").contains("--disc-root").contains("--clip-id").contains("--config")
				.contains("--target-bytes").contains("--engine").contains("--output").contains("--work-dir");
	}
}
