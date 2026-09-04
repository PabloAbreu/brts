package org.brts.lowlevel.thumbnail;

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