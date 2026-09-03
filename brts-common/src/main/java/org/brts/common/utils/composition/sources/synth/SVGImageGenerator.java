package org.brts.common.utils.composition.sources.synth;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.brts.common.utils.composition.CompositionEngineFactory;
import org.brts.common.utils.composition.ImageFrame;
import org.brts.common.utils.composition.ImageReference;
import org.w3c.dom.Document;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Renders SVG content to BufferedImage using Apache Batik. Supports inline SVG ({@code data} field) or file-based SVG
 * ({@code srcPath} field). Animated SVGs (SMIL) are rendered at time = frameNumber / frameRate when frameRate is set.
 * Static SVGs are rendered once and cached. The SVG document is parsed once and cloned per frame, since Batik's
 * bridge/CSS engine attaches mutable state to a document during GVT construction.
 */
@Slf4j
public class SVGImageGenerator implements SyntheticImageGenerator {

	private final Document parsedDocument;
	private final Double frameRate;
	private ImageFrame cachedStaticFrame;

	public SVGImageGenerator(ImageReference.SyntheticImageSource content) {
		this.frameRate = content.getFrameRate();
		this.parsedDocument = parseSvgDocument(resolveSvgContent(content));
	}

	@Override
	public ImageFrame generate(int frameNumber) {
		if (isStatic()) {
			if (cachedStaticFrame == null) {
				cachedStaticFrame = CompositionEngineFactory.get().fromBufferedImage(render(0f));
			}
			return cachedStaticFrame;
		}
		float snapshotTime = frameNumber / frameRate.floatValue();
		return CompositionEngineFactory.get().fromBufferedImage(render(snapshotTime));
	}

	@Override
	public void close() {
		if (cachedStaticFrame != null) {
			cachedStaticFrame.close();
			cachedStaticFrame = null;
		}
	}

	private boolean isStatic() {
		return frameRate == null || frameRate <= 0;
	}

	private BufferedImage render(float snapshotTime) {
		BufferedImageTranscoder transcoder = new BufferedImageTranscoder();

		if (!isStatic()) {
			transcoder.addTranscodingHint(ImageTranscoder.KEY_SNAPSHOT_TIME, snapshotTime);
		}

		// Bridge/GVT construction mutates the document, so each render needs its own copy of the parsed tree.
		Document documentForFrame = (Document) parsedDocument.cloneNode(true);
		try {
			TranscoderInput input = new TranscoderInput(documentForFrame);
			transcoder.transcode(input, new TranscoderOutput());
		} catch (TranscoderException e) {
			throw new IllegalStateException("Failed to render SVG at snapshotTime=" + snapshotTime, e);
		}

		BufferedImage result = transcoder.getImage();
		if (result == null) {
			throw new IllegalStateException("Batik transcoder produced no image");
		}
		return result;
	}

	private static Document parseSvgDocument(String svgContent) {
		String parser = XMLResourceDescriptor.getXMLParserClassName();
		SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
		byte[] bytes = svgContent.getBytes(StandardCharsets.UTF_8);
		try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
			return factory.createDocument(null, is);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to parse SVG content", e);
		}
	}

	private static String resolveSvgContent(ImageReference.SyntheticImageSource content) {
		if (content.getData() != null && !content.getData().isBlank()) {
			log.debug("Using inline SVG data ({} chars)", content.getData().length());
			return content.getData();
		}
		if (content.getSrcPath() != null && !content.getSrcPath().isBlank()) {
			Path path = Path.of(content.getSrcPath());
			log.debug("Loading SVG from file: {}", path);
			try {
				return Files.readString(path, StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new IllegalArgumentException("Cannot read SVG file: " + path, e);
			}
		}
		throw new IllegalArgumentException("SyntheticImageSource has neither 'data' nor 'srcPath' set");
	}

	/**
	 * Custom ImageTranscoder that captures the rendered BufferedImage in memory.
	 */
	private static class BufferedImageTranscoder extends ImageTranscoder {
		private @Getter BufferedImage image;

		@Override
		public BufferedImage createImage(int width, int height) {
			return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		}

		@Override
		public void writeImage(BufferedImage img, TranscoderOutput output) {
			this.image = img;
		}
	}
}
