package org.brts.cli.middle;

import org.brts.cli.FeatureRunner;
import org.brts.cli.LevelDispatcher;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * CLI for middle-level disc authoring.
 */
public class MiddleLevelCli {

	static class BuildOptions {

		@Option(name = "--descriptor", required = true, usage = "Path to the middle-level disc JSON descriptor")
		File descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for low-level descriptors and script")
		File outputDir;

	}

	public static class Build extends FeatureRunner<BuildOptions> {

		@Override
		public String getCommandName() {
			return "build";
		}

		@Override
		public String getDescription() {
			return "Build a Blu-ray disc from a middle-level descriptor";
		}

		@Override
		protected BuildOptions createOptions() {
			return new BuildOptions();
		}

		@Override
		protected void execute(BuildOptions opts) throws Exception {
			DiscDescriptor disc = loadJson(opts.descriptor, DiscDescriptor.class);

			SimpleTitleBuilder titleBuilder = new SimpleTitleBuilder(new MkvSourceMediaParser());
			MiddleLevelOrchestrator orchestrator = new MiddleLevelOrchestrator(titleBuilder);
			orchestrator.orchestrate(disc, opts.outputDir.toPath());

			System.out.println("Middle-level orchestration complete. Check " + opts.outputDir);
		}

	}

	private static final LevelDispatcher dispatcher;

	static {
		dispatcher = new LevelDispatcher("mid");
		dispatcher.register(new Build())
			.register(new ScanPlaylistsCli.Run())
			.register(new DsPreviewCli.Run())
			.register(new SetupMenuCli.Run())
			.register(new FindFirstPlaylistCli.Run());
	}

	public static LevelDispatcher getLevelDispatcher() {
		return dispatcher;
	}

	public static void main(String[] args) throws Exception {
		dispatcher.dispatch(args);
	}

}
