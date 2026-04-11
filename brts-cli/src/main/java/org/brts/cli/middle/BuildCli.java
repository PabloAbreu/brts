package org.brts.cli.middle;

import java.io.File;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.kohsuke.args4j.Option;

public class BuildCli {
	public static class BuildOptions {
		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the middle-level disc JSON descriptor")
		DiscDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for low-level descriptors and script")
		File outputDir;
	}

	public static class Run extends FeatureRunner<BuildOptions> {
		@Override
		public String getCommandName() {
			return "build";
		}

		@Override
		public String getDescription() {
			return "Build a Blu-ray disc from a middle-level descriptor";
		}

		@Override
		protected void execute(BuildOptions opts) throws Exception {
			SimpleTitleBuilder titleBuilder = new SimpleTitleBuilder(new MkvSourceMediaParser());
			MiddleLevelOrchestrator orchestrator = new MiddleLevelOrchestrator(titleBuilder);
			orchestrator.orchestrate(opts.descriptor, opts.outputDir.toPath());

			System.out.println("Middle-level orchestration complete. Check " + opts.outputDir);
		}
	}
}