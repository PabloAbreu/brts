package org.brts.cli.middle;

import org.brts.cli.FeatureRunner;
import org.brts.middle.menu.SetupMenuGenerator;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * CLI for the middle-level "create-setup-menu" command.
 */
public class SetupMenuCli {

	static class Options {

		@Option(name = "--descriptor", required = true, usage = "Path to the setup menu JSON descriptor")
		File descriptor;

		@Option(name = "--output", required = true,
				usage = "Output directory for the generated M2TS and intermediate files")
		File outputDir;

	}

	public static class Run extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "create-setup-menu";
		}

		@Override
		public String getDescription() {
			return "Generate a Blu-ray setup/settings menu M2TS";
		}

		@Override
		protected Options createOptions() {
			return new Options();
		}

		@Override
		protected void execute(Options opts) throws Exception {
			System.out.println("Generating setup menu from: " + opts.descriptor);
			System.out.println("Output directory: " + opts.outputDir);

			SetupMenuGenerator generator = new SetupMenuGenerator();
			generator.generate(opts.descriptor.toPath(), opts.outputDir.toPath());

			System.out.println("Setup menu generation complete. Check " + opts.outputDir);
		}

	}

}
