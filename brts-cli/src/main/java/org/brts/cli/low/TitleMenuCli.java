package org.brts.cli.low;

import java.io.File;
import java.nio.file.Path;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.lowlevel.titlemenu.TitleMenuGenerator;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.kohsuke.args4j.Option;

/**
 * CLI for the low-level "create-title-menu" command.
 * <p>
 * Generates a Blu-ray title selection menu (MPLS + 2 M2TS/CLPI pairs) from a JSON descriptor.
 */
public class TitleMenuCli {

	public static class Options extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the title menu JSON descriptor")
		TitleMenuDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for the generated BDMV structure")
		File outputDir;

		@Option(name = "--base-dir", required = false, usage = "Base directory for resolving relative paths in the descriptor (defaults to descriptor's parent)")
		File baseDir;

	}

	public static class Create extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "create-title-menu";
		}

		@Override
		public String getDescription() {
			return "Generate a Blu-ray title selection menu (MPLS + M2TS/CLPI)";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			System.out.println("Generating title menu...");
			System.out.println("Output directory: " + opts.outputDir);

			Path baseDir = opts.baseDir != null ? opts.baseDir.toPath() : opts.outputDir.toPath().toAbsolutePath();

			TitleMenuGenerator generator = new TitleMenuGenerator();
			generator.generate(opts.descriptor, opts.outputDir.toPath(), baseDir);

			System.out.println("Title menu generation complete. Check " + opts.outputDir);
		}

	}

}
