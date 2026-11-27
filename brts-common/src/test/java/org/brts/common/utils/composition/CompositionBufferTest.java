/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/utils/composition/CompositionBufferTest.java' is part of BRTS.
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

package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.assertj.core.api.Assertions;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.test.sampledata.RequiresSamples;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CompositionBufferTest {

	private static final ObjectMapper mapper = JsonMapperFactory.get();

	private static final String SVG = """
			<svg xmlns="http://www.w3.org/2000/svg" width="20" height="10">
				<rect fill="red" width="20" height="10"/>
			</svg>
			""";

	@Test
	void syntheticFileResolvesAgainstBasePathWithoutMutatingSource(@TempDir Path basePath) throws Exception {
		Files.writeString(basePath.resolve("background.svg"), SVG);
		ImagesComposition config = syntheticComposition("background.svg");
		ImageReference.SyntheticImageSource source = config.getImages().getFirst().getSyntheticImage();
		source.setFrameRate(25.0);

		try (MediaRepository repository = new MediaRepositoryImpl()) {
			assertSyntheticWidth(config, repository, basePath, 20);
			assertSyntheticWidth(config, repository, basePath, 20);
		}
		Assertions.assertThat(source.getSrcPath()).isEqualTo("background.svg");
		Assertions.assertThat(source.getFrameRate()).isEqualTo(25.0);
	}

	@Test
	void syntheticCacheDistinguishesBasePaths(@TempDir Path tempDir) throws Exception {
		Path firstBase = Files.createDirectory(tempDir.resolve("first"));
		Path secondBase = Files.createDirectory(tempDir.resolve("second"));
		Files.writeString(firstBase.resolve("background.svg"), SVG);
		Files.writeString(secondBase.resolve("background.svg"), SVG.replace("width=\"20\"", "width=\"30\""));
		ImagesComposition config = syntheticComposition("background.svg");

		try (MediaRepository repository = new MediaRepositoryImpl()) {
			assertSyntheticWidth(config, repository, firstBase, 20);
			assertSyntheticWidth(config, repository, secondBase, 30);
		}
		Assertions.assertThat(config.getImages().getFirst().getSyntheticImage().getSrcPath())
				.isEqualTo("background.svg");
	}

	@Test
	void syntheticAbsolutePathIsPreserved(@TempDir Path tempDir) throws Exception {
		Path svgFile = tempDir.resolve("background.svg");
		Files.writeString(svgFile, SVG);
		Path basePath = Files.createDirectory(tempDir.resolve("other"));
		ImagesComposition config = syntheticComposition(svgFile.toString());

		try (MediaRepository repository = new MediaRepositoryImpl()) {
			assertSyntheticWidth(config, repository, basePath, 20);
		}
	}

	@Test
	void syntheticInlineDataTakesPrecedenceOverPath(@TempDir Path basePath) {
		ImagesComposition config = syntheticComposition("../unused.svg");
		config.getImages().getFirst().getSyntheticImage().setData(SVG);

		try (MediaRepository repository = new MediaRepositoryImpl()) {
			assertSyntheticWidth(config, repository, basePath, 20);
		}
	}

	@Test
	void syntheticRelativePathCannotEscapeBasePath(@TempDir Path basePath) {
		ImagesComposition config = syntheticComposition("../outside.svg");

		try (MediaRepository repository = new MediaRepositoryImpl()) {
			CompositionBuffer buffer = new CompositionBuffer(config, repository,
					new CompositionContextImpl(0, config, basePath));
			Assertions.assertThatThrownBy(buffer::compose).isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("outside of base path");
		}
	}

	private ImagesComposition syntheticComposition(String srcPath) {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("svg");
		source.setSrcPath(srcPath);
		ImageReference ref = new ImageReference();
		ref.setImageId("background");
		ref.setSyntheticImage(source);
		ImagesComposition config = new ImagesComposition();
		config.setImages(List.of(ref));
		config.setCompositions(List.of());
		return config;
	}

	private void assertSyntheticWidth(ImagesComposition config, MediaRepository repository, Path basePath, int width) {
		CompositionBuffer buffer = new CompositionBuffer(config, repository,
				new CompositionContextImpl(0, config, basePath));
		try (ImageFrame result = buffer.compose()) {
			Assertions.assertThat(result.width()).isEqualTo(width);
			Assertions.assertThat(result.height()).isEqualTo(10);
		}
	}

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
