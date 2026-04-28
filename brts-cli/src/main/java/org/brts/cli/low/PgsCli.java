package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.lowlevel.pgs.PgsGenerator;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.kohsuke.args4j.Option;

import java.io.File;

/**
 * CLI for PGS (Presentation Graphic Stream) subtitle generation.
 */
public class PgsCli {

	public static class CreateOptions {

		@Option(name = "--input", required = true, usage = "Path to the subtitle file (.srt, .ssa, .ass)")
		File input;

		@Option(name = "--output", required = true, usage = "Output PGS file path (.sup)")
		File output;

		@Option(name = "--resolution", usage = "Video resolution as WIDTHxHEIGHT (default: 1920x1080)")
		String resolution;

		@Option(name = "--font-name", usage = "Font family name (default: SansSerif)")
		String fontName;

		@Option(name = "--font-size", usage = "Font size in points (default: 48)")
		Integer fontSize;

		@Option(name = "--font-color", usage = "Text colour as RRGGBB hex (default: FFFFFF = white)")
		String fontColor;

		@Option(name = "--outline-color", usage = "Outline colour as RRGGBB hex (default: 000000 = black)")
		String outlineColor;

		@Option(name = "--outline-width", usage = "Outline stroke width in pixels (default: 3.0)")
		Float outlineWidth;

		@Option(name = "--position", usage = "Vertical position as fraction 0.0–1.0 (default: 0.90 = near bottom)")
		Double position;

		@Option(name = "--frame-rate", usage = "Frame rate code: 1=23.976, 2=24, 3=25, 4=29.97, 6=50, 7=59.94 (default: 1)")
		Integer frameRate;

		@Option(name = "--margin", usage = "Horizontal margin in pixels (default: 120)")
		Integer margin;

	}

	public static class Create extends FeatureRunner<CreateOptions> {

		@Override
		public String getCommandName() {
			return "pgs-create";
		}

		@Override
		public String getDescription() {
			return "Generate a PGS subtitle stream from SRT/SSA/ASS";
		}

		@Override
		protected void execute(CreateOptions opts) throws Exception {
			PgsRenderConfig config = new PgsRenderConfig();

			if (opts.resolution != null) {
				String[] parts = opts.resolution.toLowerCase().split("x");
				if (parts.length != 2) {
					throw new IllegalArgumentException(
							"Invalid resolution format: " + opts.resolution + " (expected WIDTHxHEIGHT)");
				}
				config.setScreenWidth(Integer.parseInt(parts[0].trim()));
				config.setScreenHeight(Integer.parseInt(parts[1].trim()));
			}

			if (opts.fontName != null)
				config.setFontName(opts.fontName);
			if (opts.fontSize != null)
				config.setFontSize(opts.fontSize);
			if (opts.fontColor != null)
				config.setFontColor(parseColor(opts.fontColor));
			if (opts.outlineColor != null)
				config.setOutlineColor(parseColor(opts.outlineColor));
			if (opts.outlineWidth != null)
				config.setOutlineWidth(opts.outlineWidth);
			if (opts.position != null)
				config.setVerticalPositionRatio(opts.position);
			if (opts.frameRate != null)
				config.setFrameRateCode(opts.frameRate);
			if (opts.margin != null)
				config.setHorizontalMargin(opts.margin);

			PgsGenerator generator = new PgsGenerator(config);
			generator.generate(opts.input.toPath(), opts.output.toPath());

			System.out.println("PGS created: " + opts.output);
			System.out.printf("  Resolution: %dx%d, Font: %s %dpt%n", config.getScreenWidth(), config.getScreenHeight(),
					config.getFontName(), config.getFontSize());
		}

	}

	/**
	 * Parses a hex colour string (RRGGBB or AARRGGBB) to an ARGB int.
	 */
	private static int parseColor(String hex) {
		hex = hex.startsWith("#") ? hex.substring(1) : hex;
		if (hex.length() == 6) {
			return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
		}
		return (int) Long.parseUnsignedLong(hex, 16);
	}

}
