package org.brts.lowlevel.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/utils/composition/CompositedVideoGeneratorTest.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.io.File;
import java.nio.file.Path;

import org.assertj.core.api.Assertions;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.test.sampledata.RequiresSamples;
import org.brts.common.utils.composition.ImagesComposition;
import org.brts.lowlevel.utils.composition.CompositedVideoGenerator;
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
