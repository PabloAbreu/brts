package org.brts.lowlevel.thumbnail;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/thumbnail/ChapterThumbnailExtractorTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class ChapterThumbnailExtractorTest {

	@Test
	void luminanceVariationRejectsUniformDarkAndBrightImages() {
		assertThat(ChapterThumbnailExtractor.luminanceVariation(uniformImage(Color.BLACK))).isCloseTo(0.0,
				within(0.001));
		assertThat(ChapterThumbnailExtractor.luminanceVariation(uniformImage(Color.WHITE))).isCloseTo(0.0,
				within(0.001));
	}

	@Test
	void luminanceVariationAcceptsImageWithVisibleStructure() {
		BufferedImage image = checkerboardImage();

		assertThat(ChapterThumbnailExtractor.luminanceVariation(image)).isCloseTo(127.5, within(0.0001));
	}

	@Test
	void luminanceVariationAcceptsSmallObjectOnFlatBackground() {
		// A small dark object on an otherwise flat bright background has almost no adjacent-pixel difference on
		// average, but is a visually good, high-contrast thumbnail: the global standard deviation must catch it.
		BufferedImage image = objectOnBackgroundImage();

		assertThat(ChapterThumbnailExtractor.luminanceVariation(image)).isGreaterThan(20.0);
	}

	@Test
	void candidateSelectorAcceptsFirstImageAboveThreshold() {
		var selector = new ChapterThumbnailExtractor.ThumbnailCandidateSelector(8.0);
		BufferedImage dark = uniformImage(Color.BLACK);
		BufferedImage structured = checkerboardImage();

		assertThat(selector.consider(dark)).isFalse();
		assertThat(selector.consider(structured)).isTrue();
		assertThat(selector.lastCandidate()).isSameAs(structured);
	}

	@Test
	void candidateSelectorFallsBackToFourthCandidate() {
		var selector = new ChapterThumbnailExtractor.ThumbnailCandidateSelector(8.0);
		BufferedImage last = null;

		for (int attempt = 1; attempt <= ChapterThumbnailExtractor.MAX_CAPTURE_ATTEMPTS; attempt++) {
			last = uniformImage(new Color(attempt, attempt, attempt));
			assertThat(selector.consider(last)).isEqualTo(attempt == ChapterThumbnailExtractor.MAX_CAPTURE_ATTEMPTS);
		}
		assertThat(selector.lastCandidate()).isSameAs(last);
	}

	private BufferedImage checkerboardImage() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setRGB(x, y, (x + y) % 2 == 0 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
			}
		}
		return image;
	}

	private BufferedImage objectOnBackgroundImage() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				boolean inObject = x >= 6 && x < 10 && y >= 6 && y < 10;
				image.setRGB(x, y, inObject ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
			}
		}
		return image;
	}

	private BufferedImage uniformImage(Color color) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_3BYTE_BGR);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setRGB(x, y, color.getRGB());
			}
		}
		return image;
	}
}
