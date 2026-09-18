package org.brts.cli.middle;

import java.io.File;
import java.nio.file.Path;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.validation.WritableDirectory;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.SimpleBuildDescriptor;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.brts.middle.orchestration.launch.BashDeferredLaunchGenerator;
import org.brts.middle.orchestration.launch.ImmediateLaunchGenerator;
import org.kohsuke.args4j.Option;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * Build a single-title disc with no top menu, from a simplified descriptor.
 */
public class SimpleBuildCli {

	public static class SimpleBuildOptions extends BaseOptions {
		public static enum OutputType {
			// TODO one day add cmd or powershell for those who need that
			BASH, IMMEDIATE
		}

		@Valid
		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the simplified single-title JSON descriptor")
		SimpleBuildDescriptor descriptor;

		@WritableDirectory(createIfMissing = true)
		@Option(name = "--output", required = false, usage = "Output directory for low-level descriptors and script. Required when not using IMMEDIATE.")
		Path outputDir;

		@Option(name = "--output-type", required = false, usage = "Type of output: BASH or IMMEDIATE")
		OutputType outputType = OutputType.IMMEDIATE;

		@AssertTrue(message = "--output is required unless --output-type is IMMEDIATE")
		public boolean isOutputDirValid() {
			return outputType == OutputType.IMMEDIATE || outputDir != null;
		}
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
			MiddleLevelOrchestrator orchestrator;
			if (opts.outputType == SimpleBuildOptions.OutputType.BASH) {
				orchestrator = new MiddleLevelOrchestrator(titleBuilder, new BashDeferredLaunchGenerator());
			} else {
				orchestrator = new MiddleLevelOrchestrator(titleBuilder, new ImmediateLaunchGenerator(getInvocator()));
			}
			orchestrator.orchestrate(opts.descriptor.toDiscDescriptor(),
					opts.outputDir);

			System.out.println("Middle-level orchestration complete.");
		}

	}

}
