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
import org.brts.highlevel.descriptor.HighLevelDiscDescriptor;
import org.brts.highlevel.orchestration.HighLevelOrchestrator;
import org.brts.highlevel.preview.TitleMenuPreviewRenderer;
import org.brts.highlevel.preview.TitleMenuPreviewRenderer.ButtonState;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.kohsuke.args4j.Option;

import jakarta.validation.constraints.AssertTrue;

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

	private static final LevelDispatcher dispatcher;

	static {
		dispatcher = new LevelDispatcher("high");
		dispatcher.register(new Build());
		dispatcher.register(new Simulate());
	}

	public static LevelDispatcher getLevelDispatcher() {
		return dispatcher;
	}

	public static void main(String[] args) throws Exception {
		dispatcher.dispatch(args);
	}

}
