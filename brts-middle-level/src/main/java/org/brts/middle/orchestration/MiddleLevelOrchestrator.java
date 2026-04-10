package org.brts.middle.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.descriptor.ClipDescriptor;
import org.brts.lowlevel.descriptor.PlaylistDescriptor;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.TitleDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Middle-level orchestrator: processes a {@link DiscDescriptor}, builds low-level descriptors
 * for each title, and writes them to an output directory along with an orchestration script.
 * <p>
 * Output structure:
 * <pre>
 * &lt;outputDir&gt;/
 *   descriptors/
 *     00001.clip-descriptor.json
 *     00001.playlist-descriptor.json
 *     ...
 *   orchestrate.sh   ← shell script invoking brt-cli low-level commands in order
 * </pre>
 */
public class MiddleLevelOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MiddleLevelOrchestrator.class);

    private final SimpleTitleBuilder titleBuilder;
    private final ObjectMapper mapper = JsonMapperFactory.get();

    public MiddleLevelOrchestrator(SimpleTitleBuilder titleBuilder) {
        this.titleBuilder = titleBuilder;
    }

    /**
     * Processes the disc descriptor and writes low-level descriptors + orchestration script.
     *
     * @param disc      the middle-level disc description
     * @param outputDir directory where descriptors and the script will be written
     */
    public void orchestrate(DiscDescriptor disc, Path outputDir) throws IOException {
        Path descriptorsDir = outputDir.resolve("descriptors");
        Files.createDirectories(descriptorsDir);

        List<String> scriptLines = new ArrayList<>();
        scriptLines.add("#!/usr/bin/env bash");
        scriptLines.add("# Auto-generated middle-level orchestration script");
        scriptLines.add("# Run each step in order to produce the low-level Blu-ray files");
        scriptLines.add("set -euo pipefail");
        scriptLines.add("");
        scriptLines.add("BRT_CLI=\"java -jar brt-cli.jar\"");
        scriptLines.add("");

        for (TitleDescriptor title : disc.getTitles()) {
            SimpleTitleBuilder.TitleBuildResult result = titleBuilder.build(title);

            String clipName     = result.clipDescriptor().getClipName();
            String clipFile     = clipName + ".clip-descriptor.json";
            String playlistFile = clipName + ".playlist-descriptor.json";

            // Write JSON descriptors
            mapper.writeValue(descriptorsDir.resolve(clipFile).toFile(), result.clipDescriptor());
            mapper.writeValue(descriptorsDir.resolve(playlistFile).toFile(), result.playlistDescriptor());

            log.info("Wrote descriptors for title {}", title.getTitleId());

            // Emit script lines
            scriptLines.add("# Title " + title.getTitleId() + " — " + title.getSourceMkv());
            scriptLines.add("$BRT_CLI low clip-write --descriptor descriptors/" + clipFile);
            scriptLines.add("$BRT_CLI low playlist-write --descriptor descriptors/" + playlistFile);
            scriptLines.add("");
        }

        // Emit index/movie-object generation step (placeholders)
        scriptLines.add("# Generate BDMV index and MovieObject");
        scriptLines.add("$BRT_CLI low index-write --auto");
        scriptLines.add("$BRT_CLI low movie-objects-write --auto");
        scriptLines.add("");
        scriptLines.add("echo \"Done. Check BDMV/ for output.\"");

        // Write orchestration script
        Path scriptPath = outputDir.resolve("orchestrate.sh");
        Files.writeString(scriptPath, String.join("\n", scriptLines) + "\n");
        scriptPath.toFile().setExecutable(true);
        log.info("Wrote orchestration script to {}", scriptPath);
    }
}
