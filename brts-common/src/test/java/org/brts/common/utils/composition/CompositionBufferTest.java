package org.brts.common.utils.composition;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.brts.common.json.JsonMapperFactory;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CompositionBufferTest {

	private static final ObjectMapper mapper = JsonMapperFactory.get();

	private CompositionBuffer makeBuffer(String path) throws Exception {
		ImagesComposition config = mapper.readValue(new File(path), ImagesComposition.class);
		MediaRepository mediaRepository = new MediaRepositoryImpl();
		return new CompositionBuffer(config, mediaRepository, new CompositionContextImpl(0, config, Samples.root()));
	}

	@Test
	public void testBuffer() {
		try {
			CompositionBuffer compositionBuffer = makeBuffer("src/test/resources/images_composition.json");
			BufferedImage result = compositionBuffer.compose();
			writePng(result, "src/test/resources/composition_result.png");
		} catch (Exception e) {
			e.printStackTrace();
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
	public void testBufferWithVideo() {
		try {
			CompositionBuffer compositionBuffer = makeBuffer("src/test/resources/images_composition2.json");
			BufferedImage result = compositionBuffer.compose();
			writePng(result, "src/test/resources/composition_result2.png");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
