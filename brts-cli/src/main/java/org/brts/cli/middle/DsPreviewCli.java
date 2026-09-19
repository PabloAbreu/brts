package org.brts.cli.middle;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/middle/DsPreviewCli.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.io.File;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.middle.preview.DisplaySetLoader;
import org.brts.middle.preview.DisplaySetPreviewFrame;
import org.brts.middle.preview.DisplaySetPreviewModel;
import org.kohsuke.args4j.Option;

/**
 * CLI for the middle-level "ds-preview" command.
 */
public class DsPreviewCli {

	static class Options extends BaseOptions {

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
