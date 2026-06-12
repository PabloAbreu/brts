package org.brts.cli.low;

import java.io.File;
import java.nio.file.Path;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.common.utils.composition.CompositedVideoGenerator;
import org.brts.common.utils.composition.VideoGenDescriptor;
import org.kohsuke.args4j.Option;

/**
 * CLI for the low-level {@code video-gen} command.
 *
 * <p>
 * Generates a composited M2TS video (and the matching CLPI) from an {@link VideoGenDescriptor} JSON descriptor that
 * embeds an {@link org.brts.common.utils.composition.ImagesComposition} recipe and optional
 * {@link CompositedVideoGenerator.Config} generation parameters.
 *
 * <p>
 * Usage:
 *
 * <pre>
 *   brts-cli.jar low video-gen --descriptor my-composition.json --output /tmp/out
 * </pre>
 */
public class VideoGenCli {

	public static class Options {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the video-gen JSON descriptor (ImagesComposition + optional Config)")
		VideoGenDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory where <clipName>.m2ts and <clipName>.clpi are written")
		File outputDir;

		@Option(name = "--base-dir", required = false, usage = "Base directory for resolving relative paths in the descriptor (defaults to output directory)")
		File baseDir;

	}

	public static class Generate extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "video-gen";
		}

		@Override
		public String getDescription() {
			return "Generate a composited M2TS video from an ImagesComposition descriptor";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			VideoGenDescriptor descriptor = opts.descriptor;

			String clipName = (descriptor.getClipName() != null && !descriptor.getClipName().isBlank())
					? descriptor.getClipName()
					: "00001";

			CompositedVideoGenerator.Config config = descriptor.getConfig() != null ? descriptor.getConfig()
					: new CompositedVideoGenerator.Config();

			Path outputDir = opts.outputDir.toPath();
			Path baseDir = opts.baseDir != null ? opts.baseDir.toPath() : outputDir.toAbsolutePath();

			System.out.println("Generating composited video '" + clipName + "' → " + outputDir);

			new CompositedVideoGenerator().generate(descriptor.getComposition(), outputDir, clipName, config, baseDir);

			System.out.println("video-gen complete. Check " + outputDir);
		}

	}

}
