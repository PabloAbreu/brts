package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/ChapterThumbnailsCli.java' is part of BRTS.
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

import org.brts.cli.FeatureRunner;
import org.brts.lowlevel.thumbnail.ChapterThumbnailExtractor;
import org.brts.lowlevel.thumbnail.ChapterThumbnailsDescriptor;
import org.kohsuke.args4j.Option;

/**
 * CLI for the low-level "chapter-thumbnails" command.
 * <p>
 * Extracts one PNG thumbnail per chapter of a source media file plus a JSON descriptor referencing them.
 */
public class ChapterThumbnailsCli {

	public static class Options extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Source media file (MKV) holding the chapters")
		File input;

		@Option(name = "--output", required = true, usage = "Output directory for the PNG thumbnails and the JSON descriptor")
		File outputDir;

		@Option(name = "--width", required = false, usage = "Thumbnail width in pixels (default: brts.thumbnail.width)")
		int width;

		@Option(name = "--height", required = false, usage = "Thumbnail height in pixels (default: derived from the source aspect ratio)")
		int height;

		@Option(name = "--minimum-luminance-variation", required = false, usage = "Minimum luminance standard deviation (RMS contrast) across the image, from 0 to 255 (default: brts.thumbnail.minimumLuminanceVariation)")
		Double minimumLuminanceVariation;

	}

	public static class Extract extends FeatureRunner<Options> {

		@Override
		public String getCommandName() {
			return "chapter-thumbnails";
		}

		@Override
		public String getDescription() {
			return "Extract per-chapter PNG thumbnails and a JSON descriptor from a source media file";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			ChapterThumbnailExtractor extractor = buildExtractor(opts);
			ChapterThumbnailsDescriptor descriptor = extractor.extract(opts.input.toPath(), opts.outputDir.toPath());
			System.out.println(
					"Extracted " + descriptor.getChapters().size() + " chapter thumbnails to " + opts.outputDir);
		}

		private ChapterThumbnailExtractor buildExtractor(Options opts) {
			if (opts.width <= 0 && opts.height <= 0 && opts.minimumLuminanceVariation == null) {
				return new ChapterThumbnailExtractor();
			}
			ChapterThumbnailExtractor defaults = new ChapterThumbnailExtractor();
			return new ChapterThumbnailExtractor(opts.width > 0 ? opts.width : defaults.getThumbnailWidth(),
					opts.height > 0 ? opts.height : defaults.getThumbnailHeight(),
					opts.minimumLuminanceVariation != null ? opts.minimumLuminanceVariation
							: defaults.getMinimumLuminanceVariation());
		}

	}

}
