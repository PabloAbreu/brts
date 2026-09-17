package org.brts.cli.middle;

import java.io.File;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.SimpleBuildDescriptor;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.kohsuke.args4j.Option;

/**
 * Build a single-title disc with no top menu, from a simplified descriptor.
 */
public class SimpleBuildCli {

	public static class SimpleBuildOptions extends BaseOptions {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the simplified single-title JSON descriptor")
		SimpleBuildDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for low-level descriptors and script")
		File outputDir;

	}

	public static class Run extends FeatureRunner<SimpleBuildOptions> {

		@Override
		public String getCommandName() {
			return "simple-build";
		}

		@Override
		public String getDescription() {
			return "Build a single-title Blu-ray disc with no top menu, from a simplified descriptor";
		}

		@Override
		protected void execute(SimpleBuildOptions opts) throws Exception {
			SimpleTitleBuilder titleBuilder = new SimpleTitleBuilder(new MkvSourceMediaParser());
			MiddleLevelOrchestrator orchestrator = new MiddleLevelOrchestrator(titleBuilder);
			orchestrator.orchestrate(opts.descriptor.toDiscDescriptor(), opts.outputDir.toPath());

			System.out.println("Middle-level orchestration complete. Check " + opts.outputDir);
		}

	}

}
