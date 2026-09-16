package org.brts.common.utils.composition;

import java.io.File;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.test.sampledata.RequiresSamples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@RequiresSamples("PB/BDMV/STREAM/00617.m2ts")
public class CompositedVideoGeneratorTest {

	@Test
	void testVideoGeneration(@TempDir Path tempDir) {
		try {
			CompositedVideoGenerator generator = new CompositedVideoGenerator();
			final Path basePath = Path.of("../").toAbsolutePath().normalize();
			final Path resources = basePath.resolve("brts-common/src/test/resources/");
			String imageConfig = resources.resolve("images_composition2.json").toString();
			ImagesComposition composition = JsonMapperFactory.get().readValue(new File(imageConfig),
					ImagesComposition.class);
			Path outputPath = tempDir.resolve("output_temp");
			outputPath.toFile().mkdirs();
			CompositedVideoGenerator.Config config = new CompositedVideoGenerator.Config();
			config.setFps(24);
			config.setFrameCount(300);
			generator.generate(composition, outputPath, "00001", config, basePath);
		} catch (Exception e) {
			e.printStackTrace();
			Assertions.fail("an exception occurred during video generation: " + e.getMessage());
		}
	}

}