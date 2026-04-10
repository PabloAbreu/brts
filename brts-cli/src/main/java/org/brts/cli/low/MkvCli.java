package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.mkv.SourceMediaInfo;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;

/**
 * CLI for MKV source media inspection.
 * <p>
 * Sub-commands:
 * <ul>
 *   <li><b>mkv-info</b>: parse an MKV file and emit the discovered track metadata as JSON</li>
 * </ul>
 */
public class MkvCli {

    static class InfoOptions {
        @Option(name = "--input", required = true, usage = "Path to the MKV file to inspect")
        File input;

        @Option(name = "--output", usage = "Output JSON file path (default: stdout)")
        File output;
    }

    public static class Info extends FeatureRunner<InfoOptions> {
        @Override public String getCommandName() { return "mkv-info"; }
        @Override public String getDescription() { return "Inspect an MKV file and emit track metadata as JSON"; }
        @Override protected InfoOptions createOptions() { return new InfoOptions(); }

        @Override
        protected void execute(InfoOptions opts) throws Exception {
            Path inputPath = opts.input.toPath();
            SourceMediaInfo info = new MkvSourceMediaParser().parse(inputPath);
            writeJson(opts.output, info);
            if (opts.output != null) {
                System.out.println("Parsed MKV → " + opts.output);
            }
        }
    }
}
