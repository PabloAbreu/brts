package org.brts.cli.middle;

import org.brts.cli.FeatureRunner;
import org.brts.middle.preview.DisplaySetLoader;
import org.brts.middle.preview.DisplaySetPreviewFrame;
import org.brts.middle.preview.DisplaySetPreviewModel;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * CLI for the middle-level "ds-preview" command.
 */
public class DsPreviewCli {

	static class Options {

		@Option(name = "--input", required = true, usage = "Path to the .m2ts file containing an IGS stream")
		File input;

		@Option(name = "--ds-index", usage = "Zero-based index of the display set to preview (default: 0)")
		int displaySetIndex = 0;

	}

	public static class Run extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "ds-preview";
		}

		@Override
		public String getDescription() {
			return "Interactive Swing preview for IGS menus in M2TS";
		}

		@Override
		protected Options createOptions() {
			return new Options();
		}

		@Override
		protected void execute(Options opts) throws Exception {
			System.out.println("Loading IGS from " + opts.input + " (display set " + opts.displaySetIndex + ")…");

			DisplaySetLoader loader = new DisplaySetLoader();
			DisplaySetPreviewModel model = loader.load(opts.input.toPath(), opts.displaySetIndex);

			System.out.println("Loaded: " + model.getScreenWidth() + "×" + model.getScreenHeight() + ", "
					+ model.getPages().size() + " page(s), " + model.getObjectImages().size() + " decoded object(s).");
			System.out.println("Opening preview window… (close the window or press Escape to exit)");

			DisplaySetPreviewFrame.showAndWait(model);

			System.out.println("Preview closed.");
		}

	}

}
