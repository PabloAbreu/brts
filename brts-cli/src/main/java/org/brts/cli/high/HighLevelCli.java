package org.brts.cli.high;

import org.brts.cli.FeatureRunner;
import org.brts.cli.LevelDispatcher;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.highlevel.descriptor.HighLevelDiscDescriptor;
import org.brts.highlevel.orchestration.HighLevelOrchestrator;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.brts.middle.orchestration.launch.BashDeferredLaunchGenerator;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * CLI for high-level disc authoring.
 */
public class HighLevelCli {

	public static class BuildOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--descriptor", required = true, usage = "Path to the high-level disc JSON descriptor")
		File descriptor;

		@Option(name = "--output", required = true, usage = "Output directory")
		File outputDir;

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
			HighLevelDiscDescriptor descriptor = loadJson(opts.descriptor, HighLevelDiscDescriptor.class);

			SimpleTitleBuilder titleBuilder = new SimpleTitleBuilder(new MkvSourceMediaParser());
			MiddleLevelOrchestrator middleOrch = new MiddleLevelOrchestrator(titleBuilder,
					new BashDeferredLaunchGenerator());
			HighLevelOrchestrator highOrch = new HighLevelOrchestrator(middleOrch);

			highOrch.orchestrate(descriptor, opts.outputDir.toPath());
			System.out.println("High-level orchestration complete. Check " + opts.outputDir);
		}

	}

	private static final LevelDispatcher dispatcher;

	static {
		dispatcher = new LevelDispatcher("high");
		dispatcher.register(new Build());
	}

	public static LevelDispatcher getLevelDispatcher() {
		return dispatcher;
	}

	public static void main(String[] args) throws Exception {
		dispatcher.dispatch(args);
	}

}
