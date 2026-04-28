package org.brts.cli.middle;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.middle.menu.SetupMenuGenerator;
import org.brts.middle.menu.descriptor.SetupMenuDescriptor;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * CLI for the middle-level "create-setup-menu" command.
 */
public class SetupMenuCli {

	public static class Options {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the setup menu JSON descriptor")
		SetupMenuDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for the generated M2TS and intermediate files")
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
		protected void execute(Options opts) throws Exception {
			System.out.println("Generating setup menu from: " + opts.descriptor);
			System.out.println("Output directory: " + opts.outputDir);

			SetupMenuGenerator generator = new SetupMenuGenerator();
			generator.generate(opts.descriptor, opts.outputDir.toPath());

			System.out.println("Setup menu generation complete. Check " + opts.outputDir);
		}

	}

}
