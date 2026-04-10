package org.brts.common.utils.composition;

import java.io.File;
import java.nio.file.Path;

import org.brts.common.json.JsonMapperFactory;
import org.junit.jupiter.api.Test;

public class CompositedVideoGeneratorTest {
    @Test
    void testVideoGeneration() {
        try {
            CompositedVideoGenerator generator = new CompositedVideoGenerator();
            final Path basePath = Path.of("../").toAbsolutePath().normalize();
            final Path resources = basePath.resolve("brt-common/src/test/resources/");
            String imageConfig = resources.resolve("images_composition2.json").toString();
            ImagesComposition composition = JsonMapperFactory.get().readValue(new File(imageConfig),
                    ImagesComposition.class);
            Path outputPath = resources.resolve("output");
            outputPath.toFile().mkdirs();
            CompositedVideoGenerator.Config config = new CompositedVideoGenerator.Config();
            config.setFps(24);
            config.setFrameCount(200);
            generator.generate(composition, outputPath, "00001", config, basePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}