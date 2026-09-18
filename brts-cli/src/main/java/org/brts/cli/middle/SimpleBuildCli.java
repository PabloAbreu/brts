package org.brts.cli.middle;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.cli.OrchestratorOptions;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.SimpleBuildDescriptor;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;

import jakarta.validation.Valid;

/**
 * Build a single-title disc with no top menu, from a simplified descriptor.
 */
public class SimpleBuildCli {

	public static class SimpleBuildOptions extends OrchestratorOptions {
		@Valid
		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the simplified single-title JSON descriptor")
		SimpleBuildDescriptor descriptor;
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
			MiddleLevelOrchestrator orchestrator = new MiddleLevelOrchestrator(titleBuilder,
					opts.getLaunchGenerator(getInvocator()));

			orchestrator.orchestrate(opts.descriptor.toDiscDescriptor(), opts.outputDir);

			System.out.println("Middle-level orchestration complete.");
		}

	}

}
