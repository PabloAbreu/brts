package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.lowlevel.igs.RleConverter;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.PaletteEntry;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;

/**
 * CLI for converting IGS RLE bitmaps to standard image formats (PNG).
 */
public class RleConverterCli {

    static class Options {
        @Option(name = "--input", required = true,
                usage = "Path to a demuxed IGS directory (with igs_manifest.json) or a single .rle file")
        File input;

        @Option(name = "--output",
                usage = "Output directory (directory mode) or output PNG file (single-file mode). " +
                        "In directory mode, if omitted, PNGs are written alongside the RLE files.")
        File output;

        @Option(name = "--width",
                usage = "Image width in pixels (required for single-file mode)")
        int width = -1;

        @Option(name = "--height",
                usage = "Image height in pixels (required for single-file mode)")
        int height = -1;

        @Option(name = "--palette-id",
                usage = "Palette index to use (directory mode only; default: first palette in display set)")
        int paletteId = -1;
    }

    public static class Convert extends FeatureRunner<Options> {
        @Override public String getCommandName() { return "rle-to-png"; }
        @Override public String getDescription() { return "Convert IGS RLE bitmaps to PNG images"; }
        @Override protected Options createOptions() { return new Options(); }

        @Override
        protected void execute(Options opts) throws Exception {
            RleConverter converter = new RleConverter();
            Path inputPath = opts.input.toPath();

            if (opts.input.isDirectory()) {
                Path outputDir = opts.output != null ? opts.output.toPath() : null;
                converter.convertDirectory(inputPath, outputDir);
                System.out.println("RLE → PNG conversion complete → "
                        + (outputDir != null ? outputDir : inputPath));
            } else {
                if (opts.width <= 0 || opts.height <= 0) {
                    System.err.println("Error: --width and --height are required when converting a single .rle file.");
                    printUsage(System.err);
                    System.exit(1);
                }

                Path outputFile;
                if (opts.output != null) {
                    outputFile = opts.output.toPath();
                } else {
                    String name = inputPath.getFileName().toString().replaceAll("\\.rle$", ".png");
                    outputFile = inputPath.resolveSibling(name);
                }

                IgsPalette palette = buildGreyscalePalette();
                converter.convertToPng(inputPath, opts.width, opts.height, palette, outputFile);
                System.out.println("RLE → PNG: " + outputFile);
            }
        }
    }

    /**
     * Builds a simple greyscale palette (256 entries: index 0 = transparent,
     * 1–255 = increasingly bright white) for standalone RLE conversion when
     * no palette JSON is available.
     */
    private static IgsPalette buildGreyscalePalette() {
        IgsPalette pal = new IgsPalette();
        pal.setId(0);
        pal.setVersion(0);
        for (int i = 0; i < 256; i++) {
            PaletteEntry e = new PaletteEntry();
            e.setEntryId(i);
            e.setY(i);
            e.setCr(128);
            e.setCb(128);
            e.setAlpha(i == 0 ? 0 : 255);
            pal.getEntries().add(e);
        }
        return pal;
    }
}
