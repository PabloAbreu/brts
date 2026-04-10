package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.lowlevel.bdmv.IndexBdmvWriter;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.parser.IndexBdmvParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * CLI for low-level {@code BDMV/index.bdmv} operations.
 * <p>
 * Sub-commands:
 * <ul>
 *   <li><b>index-parse</b> — parse a binary {@code index.bdmv} file and emit JSON</li>
 *   <li><b>index-write</b> — generate a binary {@code index.bdmv} from a JSON model</li>
 * </ul>
 */
public class IndexBdmvCli {

    // -------------------------------------------------------------------------
    // Parse command:  binary index.bdmv → JSON
    // -------------------------------------------------------------------------

    static class ParseOptions {
        @Option(name = "--input", required = true,
                usage = "Path to the binary index.bdmv file to parse")
        File input;

        @Option(name = "--output",
                usage = "Output JSON file path (omit to print to stdout)")
        File output;
    }

    public static class Parse extends FeatureRunner<ParseOptions> {
        @Override public String getCommandName() { return "index-parse"; }
        @Override public String getDescription() { return "Parse a binary index.bdmv file to JSON"; }
        @Override protected ParseOptions createOptions() { return new ParseOptions(); }

        @Override
        protected void execute(ParseOptions opts) throws Exception {
            IndexBdmv index = new IndexBdmvParser().parse(opts.input.toPath());
            if (opts.output != null) {
                if (opts.output.getParentFile() != null) {
                    opts.output.getParentFile().mkdirs();
                }
            }
            writeJson(opts.output, index);
            if (opts.output != null) {
                System.out.println("Parsed index.bdmv → " + opts.output);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Write command:  JSON → binary index.bdmv
    // -------------------------------------------------------------------------

    static class WriteOptions {
        @Option(name = "--input", required = true,
                usage = "Path to the JSON model file (as produced by index-parse)")
        File input;

        @Option(name = "--output", required = true,
                usage = "Output path for the generated index.bdmv file")
        File output;
    }

    public static class Write extends FeatureRunner<WriteOptions> {
        @Override public String getCommandName() { return "index-write"; }
        @Override public String getDescription() { return "Generate a binary index.bdmv from a JSON model"; }
        @Override protected WriteOptions createOptions() { return new WriteOptions(); }

        @Override
        protected void execute(WriteOptions opts) throws Exception {
            IndexBdmv index = loadJson(opts.input, IndexBdmv.class);
            if (opts.output.getParentFile() != null) {
                opts.output.getParentFile().mkdirs();
            }
            try (OutputStream out = new FileOutputStream(opts.output)) {
                new IndexBdmvWriter().write(index, out);
            }
            System.out.println("Wrote index.bdmv → " + opts.output);
        }
    }
}
