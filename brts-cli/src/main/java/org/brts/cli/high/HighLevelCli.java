/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/high/HighLevelCli.java' is part of BRTS.
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

package org.brts.cli.high;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.cli.LevelDispatcher;
import org.brts.cli.OrchestratorOptions;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.validation.ExistingDirectory;
import org.brts.highlevel.descriptor.HighLevelDiscDescriptor;
import org.brts.highlevel.orchestration.HighLevelOrchestrator;
import org.brts.highlevel.preview.TitleMenuPreviewRenderer;
import org.brts.highlevel.preview.TitleMenuPreviewRenderer.ButtonState;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.kohsuke.args4j.Option;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.brts.highlevel.shrink.DiscShrinkDescriptor;
import org.brts.highlevel.shrink.DiscShrinkOrchestrator;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;

/**
 * CLI for high-level disc authoring.
 */
public class HighLevelCli {

	public static class BuildOptions extends OrchestratorOptions {
		public BuildOptions() {
			outputType = OutputType.BASH;
		}

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the high-level disc JSON descriptor")
		HighLevelDiscDescriptor descriptor;

		@AssertTrue(message = "--output-type IMMEDIATE is not supported for high-level builds")
		public boolean isBashOutputRequired() {
			return outputType == OutputType.BASH;
		}

	}

	public static class Build extends FeatureRunner<BuildOptions> {

		@Override
		public String getCommandName() {
			return "build";
		}

		@Override
		public String getDescription() {
			return "Build a Blu-ray disc from a high-level template descriptor";
		}

		@Override
		protected void execute(BuildOptions opts) throws Exception {

			SimpleTitleBuilder titleBuilder = new SimpleTitleBuilder(new MkvSourceMediaParser());
			MiddleLevelOrchestrator middleOrch = new MiddleLevelOrchestrator(titleBuilder,
					opts.getLaunchGenerator(getInvocator()));
			HighLevelOrchestrator highOrch = new HighLevelOrchestrator(middleOrch);

			highOrch.orchestrate(opts.descriptor, opts.outputDir);
			System.out.println("High-level orchestration complete. Check " + opts.outputDir);
		}

	}

	public static class SimulateOptions extends BaseOptions {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the high-level disc JSON descriptor")
		HighLevelDiscDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output JPEG preview file")
		File output;

		@Option(name = "--base-dir", usage = "Base directory for resolving relative media paths (default: current directory)")
		File baseDir;

		@Option(name = "--time-seconds", usage = "Background sample time in seconds (default: 0)")
		double timeSeconds = 0;

		@Option(name = "--selected-title", usage = "1-based title number to show in the requested button state (default: 1)")
		int selectedTitle = 1;

		@Option(name = "--button-state", usage = "Visual state for the selected title: normal, selected, or activated (default: selected)")
		String buttonState = "selected";
	}

	public static class Simulate extends FeatureRunner<SimulateOptions> {

		@Override
		public String getCommandName() {
			return "title-menu-simulate";
		}

		@Override
		public String getDescription() {
			return "Render a single JPEG preview frame of a high-level title menu";
		}

		@Override
		protected void execute(SimulateOptions opts) throws Exception {
			ButtonState state = ButtonState.parse(opts.buttonState);
			Path output = opts.output.toPath().toAbsolutePath().normalize();
			if (output.getParent() != null) {
				Files.createDirectories(output.getParent());
			}
			Path baseDir = opts.baseDir == null ? Path.of(".").toAbsolutePath().normalize()
					: opts.baseDir.toPath().toAbsolutePath().normalize();
			BufferedImage image = new TitleMenuPreviewRenderer().render(opts.descriptor, baseDir, opts.timeSeconds,
					opts.selectedTitle, state);
			if (!ImageIO.write(image, "jpeg", output.toFile())) {
				throw new IllegalStateException("No JPEG image writer is available for output: " + output);
			}
			System.out.println("Title menu preview written to " + output);
		}
	}

	public static class DiscShrinkOptions extends BaseOptions {

		@ExistingDirectory
		@Option(name = "--input", required = true, usage = "Source disc root containing BDMV")
		Path input;

		@Option(name = "--output", required = true, usage = "Separate target disc root")
		Path output;

		@JsonInputOption(name = "--descriptor", usage = "Optional stream-language preference JSON descriptor")
		DiscShrinkDescriptor descriptor;

		@Positive(message = "--target-bytes must be positive")
		@Option(name = "--target-bytes", required = true, usage = "Hard upper limit for the entire output tree in bytes")
		long targetBytes;

		@Option(name = "--engine", usage = "Video conversion engine (FFMPEG, NVENCC, VCEENCC, QSVENCC)")
		Engine engine;
	}

	public static class DiscShrink extends FeatureRunner<DiscShrinkOptions> {

		@Override
		public String getCommandName() {
			return "disc-shrink";
		}

		@Override
		public String getDescription() {
			return "Reduce a disc-root to fit a hard whole-output size limit";
		}

		@Override
		protected void execute(DiscShrinkOptions opts) throws Exception {
			Engine engine = opts.engine != null ? opts.engine
					: Engine.parse(
							BrtsFileConfig.getInstance().propertyOrDefault("brts.video.converter.engine", "ffmpeg"));
			DiscShrinkOrchestrator orchestrator = new DiscShrinkOrchestrator(descriptor -> {
				try {
					new org.brts.cli.low.BatchRunCli.Run().runDescriptor(descriptor, false);
				} catch (java.io.IOException e) {
					throw e;
				} catch (Exception e) {
					throw new java.io.IOException("Disc-shrink conversion batch failed: " + descriptor, e);
				}
			});
			var result = orchestrator.shrink(opts.input, opts.output,
					opts.descriptor == null ? new DiscShrinkDescriptor() : opts.descriptor, opts.targetBytes, engine);
			System.out.println("Disc shrink complete: " + result.actualBytes() + " bytes at " + result.output());
			for (String diagnostic : result.diagnostics()) {
				System.err.println("Warning: " + diagnostic);
			}
		}
	}

	private static final LevelDispatcher dispatcher;

	static {
		dispatcher = new LevelDispatcher("high");
		dispatcher.register(new Build());
		dispatcher.register(new Simulate());
		dispatcher.register(new DiscShrink());
	}

	public static LevelDispatcher getLevelDispatcher() {
		return dispatcher;
	}

	public static void main(String[] args) throws Exception {
		dispatcher.dispatch(args);
	}

}
