package org.brts.cli.low;

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

		@Option(name = "--offset-seconds", required = false, usage = "Offset added to each chapter start before grabbing the frame (default: brts.thumbnail.offsetSeconds)")
		Double offsetSeconds;

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
			if (opts.width <= 0 && opts.height <= 0 && opts.offsetSeconds == null) {
				return new ChapterThumbnailExtractor();
			}
			ChapterThumbnailExtractor defaults = new ChapterThumbnailExtractor();
			return new ChapterThumbnailExtractor(opts.width > 0 ? opts.width : defaults.getThumbnailWidth(),
					opts.height > 0 ? opts.height : defaults.getThumbnailHeight(),
					opts.offsetSeconds != null ? opts.offsetSeconds : defaults.getCaptureOffsetSeconds());
		}

	}

}
