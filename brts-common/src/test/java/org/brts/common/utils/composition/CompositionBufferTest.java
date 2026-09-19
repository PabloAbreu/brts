package org.brts.common.utils.composition;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/test/java/org/brts/common/utils/composition/CompositionBufferTest.java' is part of BRTS.
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

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.assertj.core.api.Assertions;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.test.sampledata.RequiresSamples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CompositionBufferTest {

	private static final ObjectMapper mapper = JsonMapperFactory.get();

	private CompositionBuffer makeBuffer(String path, Path basePath) throws Exception {
		ImagesComposition config = mapper.readValue(new File(path), ImagesComposition.class);
		MediaRepository mediaRepository = new MediaRepositoryImpl();
		return new CompositionBuffer(config, mediaRepository, new CompositionContextImpl(0, config, basePath));
	}

	@Test
	public void testBuffer(@TempDir Path tempDir) {
		try {
			CompositionBuffer compositionBuffer = makeBuffer("src/test/resources/images_composition.json",
					Path.of("").toAbsolutePath().normalize());
			try (ImageFrame result = compositionBuffer.compose()) {
				Path outputFile = tempDir.resolve("composition_result.png");
				writePng(result.toBufferedImage(), outputFile.toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
			Assertions.fail("an exception occurred during composition: " + e.getMessage());
		}
	}

	private void writePng(BufferedImage image, String path) {
		try {
			File out = new File(path);
			if (ImageIO.write(image, "png", out))
				System.out.println("Composition result saved to " + out.getAbsolutePath());
			else
				System.err.println("Failed to save composition result to " + out.getAbsolutePath());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	@RequiresSamples("PB/BDMV/STREAM/00617.m2ts")
	public void testBufferWithVideo(@TempDir Path tempDir) {
		try {
			CompositionBuffer compositionBuffer = makeBuffer("src/test/resources/images_composition2.json",
					Path.of("..").toAbsolutePath().normalize());
			try (ImageFrame result = compositionBuffer.compose()) {
				Path outputFile = tempDir.resolve("composition_result2.png");
				writePng(result.toBufferedImage(), outputFile.toString());
			}
		} catch (Exception e) {
			e.printStackTrace();
			Assertions.fail("an exception occurred during composition: " + e.getMessage());
		}
	}

}
