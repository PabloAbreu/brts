package org.brts.common.utils.composition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.brts.common.utils.composition.sources.synth.SVGImageGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SVGImageGeneratorTest {

	private static final String SIMPLE_SVG = """
			<svg xmlns="http://www.w3.org/2000/svg" width="200" height="100">
				<rect fill="red" width="200" height="100"/>
			</svg>
			""";

	private static final String TEMPLATED_SVG = """
			<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
				<rect fill="red" width="${width}" height="${height}"/>
			</svg>
			""";

	private static final String ANIMATED_SVG = """
			<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
				<rect fill="blue" width="100" height="100">
					<animate attributeName="opacity" from="0" to="1" dur="2s" fill="freeze"/>
				</rect>
			</svg>
			""";

	@Test
	void staticInlineSvg_rendersCorrectDimensions() throws Exception {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");
		source.setData(SIMPLE_SVG);

		try (SVGImageGenerator generator = new SVGImageGenerator(source)) {
			ImageFrame image = generator.generate(0);

			assertThat(image).isNotNull();
			assertThat(image.width()).isEqualTo(200);
			assertThat(image.height()).isEqualTo(100);
		}
	}

	@Test
	void staticInlineSvg_returnsSameInstanceOnMultipleCalls() throws Exception {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");
		source.setData(SIMPLE_SVG);

		try (SVGImageGenerator generator = new SVGImageGenerator(source)) {
			ImageFrame first = generator.generate(0);
			ImageFrame second = generator.generate(5);

			assertThat(first).isSameAs(second);
		}
	}

	@Test
	void fileSvg_loadsAndRenders(@TempDir Path tempDir) throws Exception {
		Path svgFile = tempDir.resolve("test.svg");
		Files.writeString(svgFile, SIMPLE_SVG);

		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");
		source.setSrcPath(svgFile.toString());

		try (SVGImageGenerator generator = new SVGImageGenerator(source)) {
			ImageFrame image = generator.generate(0);

			assertThat(image).isNotNull();
			assertThat(image.width()).isEqualTo(200);
			assertThat(image.height()).isEqualTo(100);
		}
	}

	@Test
	void animatedSvg_rendersDifferentFrames() throws Exception {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");
		source.setData(ANIMATED_SVG);
		source.setFrameRate(24.0);

		try (SVGImageGenerator generator = new SVGImageGenerator(source)) {
			ImageFrame frame0 = generator.generate(0);
			ImageFrame frame48 = generator.generate(48);

			assertThat(frame0).isNotNull();
			assertThat(frame48).isNotNull();
			assertThat(frame0.width()).isEqualTo(100);
			assertThat(frame48.width()).isEqualTo(100);
			// Different frames should be different object instances (not cached)
			assertThat(frame0).isNotSameAs(frame48);
		}
	}

	@Test
	void dataModel_rendersTemplatedSvg() throws Exception {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");
		source.setData(TEMPLATED_SVG);

		try (SVGImageGenerator generator = new SVGImageGenerator(source)) {
			ImageFrame image = generator.generate(0, Map.of("width", 150, "height", 75));

			assertThat(image).isNotNull();
			assertThat(image.width()).isEqualTo(150);
			assertThat(image.height()).isEqualTo(75);
		}
	}

	@Test
	void dataModel_isNotCachedAcrossCalls() throws Exception {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");
		source.setData(TEMPLATED_SVG);

		try (SVGImageGenerator generator = new SVGImageGenerator(source)) {
			ImageFrame first = generator.generate(0, Map.of("width", 150, "height", 75));
			ImageFrame second = generator.generate(0, Map.of("width", 300, "height", 60));

			assertThat(first.width()).isEqualTo(150);
			assertThat(second.width()).isEqualTo(300);
		}
	}

	@Test
	void missingContent_throwsIllegalArgument() {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");

		assertThatThrownBy(() -> new SVGImageGenerator(source)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("neither 'data' nor 'srcPath'");
	}

	@Test
	void invalidFilePath_throwsIllegalArgument() {
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("SVG");
		source.setSrcPath("/nonexistent/path/to/file.svg");

		assertThatThrownBy(() -> new SVGImageGenerator(source)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Cannot read SVG file");
	}
}
