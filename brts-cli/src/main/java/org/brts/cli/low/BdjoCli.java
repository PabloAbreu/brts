package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.lowlevel.bdmv.BdjoWriter;
import org.brts.lowlevel.model.bdmv.Bdjo;
import org.brts.lowlevel.parser.BdjoParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * CLI for low-level {@code BDMV/BDJO/XXXXX.bdjo} operations.
 * <p>
 * Sub-commands:
 * <ul>
 *   <li><b>bdjo-parse</b> — parse a binary {@code .bdjo} file and emit JSON</li>
 *   <li><b>bdjo-write</b> — generate a binary {@code .bdjo} from a JSON model</li>
 * </ul>
 */
public class BdjoCli {

    // -------------------------------------------------------------------------
    // Parse command:  binary .bdjo → JSON
    // -------------------------------------------------------------------------

    static class ParseOptions {
        @Option(name = "--input", required = true,
                usage = "Path to the binary .bdjo file to parse")
        File input;

        @Option(name = "--output",
                usage = "Output JSON file path (omit to print to stdout)")
        File output;
    }

    public static class Parse extends FeatureRunner<ParseOptions> {
        @Override public String getCommandName() { return "bdjo-parse"; }
        @Override public String getDescription() { return "Parse a binary .bdjo file to JSON"; }
        @Override protected ParseOptions createOptions() { return new ParseOptions(); }

        @Override
        protected void execute(ParseOptions opts) throws Exception {
            Bdjo bdjo = new BdjoParser().parse(opts.input.toPath());
            if (opts.output != null && opts.output.getParentFile() != null) {
                opts.output.getParentFile().mkdirs();
            }
            writeJson(opts.output, bdjo);
            if (opts.output != null) {
                System.out.println("Parsed .bdjo → " + opts.output);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Write command:  JSON → binary .bdjo
    // -------------------------------------------------------------------------

    static class WriteOptions {
        @Option(name = "--input", required = true,
                usage = "Path to the JSON model file (as produced by bdjo-parse)")
        File input;

        @Option(name = "--output", required = true,
                usage = "Output path for the generated .bdjo file")
        File output;
    }

    public static class Write extends FeatureRunner<WriteOptions> {
        @Override public String getCommandName() { return "bdjo-write"; }
        @Override public String getDescription() { return "Generate a binary .bdjo from a JSON model"; }
        @Override protected WriteOptions createOptions() { return new WriteOptions(); }

        @Override
        protected void execute(WriteOptions opts) throws Exception {
            Bdjo bdjo = loadJson(opts.input, Bdjo.class);
            if (opts.output.getParentFile() != null) {
                opts.output.getParentFile().mkdirs();
            }
            try (OutputStream out = new FileOutputStream(opts.output)) {
                new BdjoWriter().write(bdjo, out);
            }
            System.out.println("Wrote .bdjo → " + opts.output);
        }
    }
}
