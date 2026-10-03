package org.brts.cli.high;



import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.brts.cli.BrtsMain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TitleMenuSimulateCliTest {

	@Test
	void highLevelCommandWritesJpegPreview(@TempDir Path tempDir) throws Exception {
		Path styleDir = Files.createDirectories(tempDir.resolve("style"));
		BufferedImage background = new BufferedImage(48, 24, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < background.getHeight(); y++) {
			for (int x = 0; x < background.getWidth(); x++) {
				background.setRGB(x, y, new Color(20, 30, 40).getRGB());
			}
		}
		ImageIO.write(background, "png", styleDir.resolve("background.png").toFile());
		Files.writeString(styleDir.resolve("style-template.json"), """
				{"name":"cli-test","titleMenu":{"backgroundSource":{"imagePath":"background.png"}}}
				""");

		Path descriptor = tempDir.resolve("movie.json");
		Files.writeString(descriptor, """
				{
				  "templateType":"MOVIE",
				  "discTitle":"CLI preview",
				  "styleTemplate":"%s",
				  "mainFeature":{"sourceMkv":"not-needed.mkv","label":"Feature"}
				}
				""".formatted(styleDir.toString().replace("\\", "\\\\")));
		Path output = tempDir.resolve("nested").resolve("preview.jpg");

		BrtsMain.main(new String[] { "high", "title-menu-simulate", "--no-banner", "--descriptor",
				descriptor.toString(), "--output", output.toString() });

		assertThat(output).exists();
		BufferedImage rendered = ImageIO.read(output.toFile());
		assertThat(rendered).isNotNull();
		assertThat(rendered.getWidth()).isEqualTo(1920);
		assertThat(rendered.getHeight()).isEqualTo(1080);
	}

	@Test
	void commandIsRegisteredAtHighLevel() {
		assertThat(HighLevelCli.getLevelDispatcher().getRunners())
				.anyMatch(runner -> runner.getCommandName().equals("title-menu-simulate"));
	}
}
