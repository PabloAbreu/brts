package org.brts.highlevel.preview;



import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.brts.highlevel.descriptor.MovieDiscDescriptor;
import org.brts.highlevel.descriptor.MovieDiscDescriptor.MovieFeature;
import org.brts.highlevel.preview.TitleMenuPreviewRenderer.ButtonState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TitleMenuPreviewRendererTest {

	@TempDir
	Path tempDir;

	@Test
	void rendersOneTitlePreviewEvenThoughMovieBuildOmitsTheMenu() throws Exception {
		Path styleDir = writeStyleTemplate();
		MovieDiscDescriptor descriptor = movie(styleDir, "Feature");
		TitleMenuPreviewRenderer renderer = new TitleMenuPreviewRenderer();

		BufferedImage selected = renderer.render(descriptor, tempDir, 0, 1, ButtonState.SELECTED);
		BufferedImage normal = renderer.render(descriptor, tempDir, 0, 1, ButtonState.NORMAL);

		assertThat(selected.getWidth()).isEqualTo(1920);
		assertThat(selected.getHeight()).isEqualTo(1080);
		assertThat(selected.getRGB(1800, 1000)).isEqualTo(new Color(12, 24, 48).getRGB());
		assertThat(imagesDiffer(selected, normal)).isTrue();
	}

	@Test
	void rejectsASelectedTitleNotPresentInDescriptor() throws Exception {
		MovieDiscDescriptor descriptor = movie(writeStyleTemplate(), "Feature");

		assertThatThrownBy(() -> new TitleMenuPreviewRenderer().render(descriptor, tempDir, 0, 2, ButtonState.SELECTED))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Selected title number 2");
	}

	@Test
	void rejectsNegativeAndNonFinitePreviewTimes() throws Exception {
		MovieDiscDescriptor descriptor = movie(writeStyleTemplate(), "Feature");
		TitleMenuPreviewRenderer renderer = new TitleMenuPreviewRenderer();

		assertThatThrownBy(() -> renderer.render(descriptor, tempDir, -1, 1, ButtonState.SELECTED))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finite non-negative");
		assertThatThrownBy(() -> renderer.render(descriptor, tempDir, Double.NaN, 1, ButtonState.SELECTED))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("finite non-negative");
	}

	private Path writeStyleTemplate() throws Exception {
		Path styleDir = Files.createDirectories(tempDir.resolve("sci-fi"));
		BufferedImage background = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < background.getHeight(); y++) {
			for (int x = 0; x < background.getWidth(); x++) {
				background.setRGB(x, y, new Color(12, 24, 48).getRGB());
			}
		}
		ImageIO.write(background, "png", styleDir.resolve("background.png").toFile());
		Files.writeString(styleDir.resolve("style-template.json"), """
				{
				  "name": "test-preview",
				  "style": {"fontName":"SansSerif", "fontSize":64, "fontStyle":1},
				  "titleMenu": {
				    "layoutType":"TEXT_LIST",
				    "boundingBox":{"x":100,"y":100,"width":1000,"height":800},
				    "style":{"normalColor":"#FFFF0000", "selectedColor":"#FF00FF00", "activatedColor":"#FF0000FF"},
				    "backgroundSource":{"imagePath":"background.png"}
				  }
				}
				""");
		return styleDir;
	}

	private static MovieDiscDescriptor movie(Path styleDir, String label) {
		MovieDiscDescriptor descriptor = new MovieDiscDescriptor();
		descriptor.setDiscTitle("Preview test");
		descriptor.setStyleTemplate(styleDir.toString());
		MovieFeature feature = new MovieFeature();
		feature.setSourceMkv("not-needed-for-text-list.mkv");
		feature.setLabel(label);
		descriptor.setMainFeature(feature);
		return descriptor;
	}

	private static boolean imagesDiffer(BufferedImage left, BufferedImage right) {
		for (int y = 0; y < left.getHeight(); y++) {
			for (int x = 0; x < left.getWidth(); x++) {
				if (left.getRGB(x, y) != right.getRGB(x, y)) {
					return true;
				}
			}
		}
		return false;
	}
}
