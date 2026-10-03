package org.brts.cli.middle;



import java.io.File;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.middle.preview.PgsPreviewFrame;
import org.brts.middle.preview.PgsPreviewModel;
import org.brts.middle.preview.PgsStreamLoader;
import org.kohsuke.args4j.Option;

/**
 * CLI for the middle-level "pgs-preview" command.
 */
public class PgsPreviewCli {

	static class Options extends BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to a standalone .sup PGS file or an .m2ts file containing a PGS stream")
		File input;

		@Option(name = "--pg-index", usage = "Zero-based index among PGS streams in the .m2ts (default: 0, the first). Mutually exclusive with --pid.")
		Integer pgIndex;

		@Option(name = "--pid", usage = "Explicit PID (decimal or 0x-prefixed hex) of the PGS stream in the .m2ts. Mutually exclusive with --pg-index.")
		String pidText;

		Integer parsePid() {
			if (pidText == null || pidText.isBlank()) {
				return null;
			}
			String s = pidText.trim();
			return s.startsWith("0x") || s.startsWith("0X") ? Integer.parseInt(s.substring(2), 16)
					: Integer.parseInt(s);
		}

	}

	public static class Run extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "pgs-preview";
		}

		@Override
		public String getDescription() {
			return "Interactive Swing preview for PGS subtitle streams (standalone .sup or .m2ts)";
		}

		@Override
		protected Options createOptions() {
			return new Options();
		}

		@Override
		protected void execute(Options opts) throws Exception {
			Integer pid = opts.parsePid();
			if (opts.pgIndex != null && pid != null) {
				throw new IllegalArgumentException("Specify either --pg-index or --pid, not both.");
			}

			System.out.println("Loading PGS from " + opts.input + "…");

			PgsStreamLoader loader = new PgsStreamLoader();
			PgsPreviewModel model = loader.load(opts.input.toPath(), opts.pgIndex, pid);

			System.out.println("Loaded: " + model.getScreenWidth() + "×" + model.getScreenHeight() + ", "
					+ model.getItems().size() + " subtitle item(s), background=" + model.getBackgroundMode());
			System.out.println("Opening preview window… (close the window or press Escape to exit)");

			PgsPreviewFrame.showAndWait(model);

			System.out.println("Preview closed.");
		}

	}

}
