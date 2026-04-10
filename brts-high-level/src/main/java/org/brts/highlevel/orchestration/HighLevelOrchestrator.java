package org.brts.highlevel.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.brts.common.json.JsonMapperFactory;
import org.brts.highlevel.descriptor.HighLevelDiscDescriptor;
import org.brts.highlevel.descriptor.MovieDiscDescriptor;
import org.brts.highlevel.descriptor.TvSeriesDiscDescriptor;
import org.brts.highlevel.template.DiscTemplate;
import org.brts.highlevel.template.MovieDiscTemplate;
import org.brts.highlevel.template.TvSeriesDiscTemplate;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.orchestration.MiddleLevelOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * High-level orchestrator: reads a {@link HighLevelDiscDescriptor}, selects the
 * appropriate template, expands it to a middle-level {@link DiscDescriptor},
 * and writes the middle-level JSON descriptors + an orchestration script
 * that calls the middle-level commands.
 */
public class HighLevelOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(HighLevelOrchestrator.class);

    private final Map<String, DiscTemplate<?>> templates;
    private final MiddleLevelOrchestrator middleOrchestrator;
    private final ObjectMapper mapper = JsonMapperFactory.get();

    public HighLevelOrchestrator(MiddleLevelOrchestrator middleOrchestrator) {
        this.middleOrchestrator = middleOrchestrator;
        this.templates = Map.of(
            "MOVIE",     new MovieDiscTemplate(),
            "TV_SERIES", new TvSeriesDiscTemplate()
        );
    }

    /**
     * Main entry point: expand high-level descriptor → middle-level → write artefacts.
     *
     * @param descriptor the high-level disc descriptor
     * @param outputDir  root output directory
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void orchestrate(HighLevelDiscDescriptor descriptor, Path outputDir) throws IOException {
        String type = descriptor.getTemplateType();
        DiscTemplate template = templates.get(type);
        if (template == null) {
            throw new IllegalArgumentException("No template registered for type: " + type);
        }

        log.info("Expanding high-level descriptor (type={}) …", type);
        DiscDescriptor middleDescriptor = template.expand(descriptor);

        // Write the middle-level disc descriptor for traceability
        Path middleDir = outputDir.resolve("middle-level");
        Files.createDirectories(middleDir);
        mapper.writeValue(middleDir.resolve("disc.json").toFile(), middleDescriptor);
        log.info("Wrote middle-level disc descriptor to {}", middleDir.resolve("disc.json"));

        if (!descriptor.isDryRun()) {
            // Delegate to middle-level orchestrator
            middleOrchestrator.orchestrate(middleDescriptor, middleDir);
        }

        // Write high-level orchestration script
        List<String> lines = new ArrayList<>();
        lines.add("#!/usr/bin/env bash");
        lines.add("# Auto-generated high-level orchestration script");
        lines.add("set -euo pipefail");
        lines.add("");
        lines.add("BRT_CLI=\"java -jar brt-cli.jar\"");
        lines.add("");
        lines.add("# Step 1: run the middle-level orchestration");
        lines.add("cd middle-level && bash orchestrate.sh && cd ..");
        lines.add("");
        lines.add("echo \"High-level orchestration complete.\"");

        Path scriptPath = outputDir.resolve("orchestrate.sh");
        Files.writeString(scriptPath, String.join("\n", lines) + "\n");
        scriptPath.toFile().setExecutable(true);
        log.info("Wrote high-level orchestration script to {}", scriptPath);
    }
}
