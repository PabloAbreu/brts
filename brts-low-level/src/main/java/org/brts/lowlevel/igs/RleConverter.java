package org.brts.lowlevel.igs;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.PaletteEntry;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Converts IGS RLE-compressed bitmap files (as produced by {@link IgsDemuxer}) into standard image formats (e.g. PNG).
 * <p>
 * The RLE files are raw PG/IG RLE-encoded bitmaps. Each pixel is a palette index. To produce a viewable image, a
 * palette (from the corresponding {@code display_set.json}) is needed to map indices to YCbCr+alpha colours, which are
 * then converted to ARGB.
 *
 * <h2>RLE encoding (PG/IG Blu-ray spec)</h2>
 * <ul>
 * <li>Non-zero byte → single pixel of that colour index</li>
 * <li>{@code 0x00 0x00} → end-of-line marker (len=0, color=0)</li>
 * <li>{@code 0x00 0b00LLLLLL} → L pixels of colour 0 (transparent)</li>
 * <li>{@code 0x00 0b01LLLLLL LLLLLLLL} → 14-bit L pixels of colour 0</li>
 * <li>{@code 0x00 0b10LLLLLL CC} → L pixels of colour C</li>
 * <li>{@code 0x00 0b11LLLLLL LLLLLLLL CC} → 14-bit L pixels of colour C</li>
 * </ul>
 */
@Slf4j
public class RleConverter {

	private final ObjectMapper mapper = JsonMapperFactory.get();

	/**
	 * Converts a single RLE object file to a PNG image.
	 *
	 * @param rleFile    path to the {@code obj_NNNN.rle} file
	 * @param width      image width in pixels (from display_set.json metadata)
	 * @param height     image height in pixels (from display_set.json metadata)
	 * @param palette    the palette to use for colour lookup
	 * @param outputFile target PNG file path
	 * @throws IOException on I/O error or invalid RLE data
	 */
	public void convertToPng(Path rleFile, int width, int height, IgsPalette palette, Path outputFile)
			throws IOException {
		byte[] rleData = Files.readAllBytes(rleFile);
		convertToPng(rleData, width, height, palette, outputFile);
	}

	/**
	 * Converts a single RLE object to a PNG, reading raw bytes.
	 *
	 * @param rleData    raw RLE bytes
	 * @param width      image width
	 * @param height     image height
	 * @param palette    palette for colour lookup
	 * @param outputFile target PNG path
	 * @throws IOException on I/O error
	 */
	public void convertToPng(byte[] rleData, int width, int height, IgsPalette palette, Path outputFile)
			throws IOException {
		BufferedImage image = decodeRle(rleData, width, height, palette);
		if (image != null) {
			Files.createDirectories(outputFile.getParent());
			ImageIO.write(image, "png", outputFile.toFile());
			log.info("RLE → PNG: ({}×{}) → {}", width, height, outputFile);
		} else {
			log.warn("Failed to decode RLE data for output: {}", outputFile);
		}
	}

	/**
	 * Converts all RLE objects in a demuxed IGS directory to PNG images.
	 * <p>
	 * Walks every {@code ds_NNNN/} sub-directory, reads the {@code display_set.json} for object dimensions and palette,
	 * and writes one PNG per object next to its RLE file.
	 *
	 * @param igsDir    root directory of a demuxed IGS (containing {@code igs_manifest.json})
	 * @param outputDir target directory for PNG files (mirrors structure). If {@code null}, PNGs are written alongside
	 *                  the RLE files in the source directory.
	 * @throws IOException on I/O error
	 */
	public void convertDirectory(Path igsDir, Path outputDir) throws IOException {
		Path manifestPath = igsDir.resolve("igs_manifest.json");
		if (!Files.exists(manifestPath)) {
			throw new IOException("igs_manifest.json not found in " + igsDir);
		}

		IgsDemuxer.IgsManifest manifest = mapper.readValue(manifestPath.toFile(), IgsDemuxer.IgsManifest.class);

		for (IgsDemuxer.IgsManifest.DisplaySetRef dsRef : manifest.getDisplaySets()) {
			Path dsDir = igsDir.resolve(dsRef.getDirectory());
			Path dsJsonPath = dsDir.resolve("display_set.json");
			if (!Files.exists(dsJsonPath)) {
				log.warn("display_set.json not found in {}, skipping", dsDir);
				continue;
			}

			IgsDemuxer.DisplaySetJson dsJson = mapper.readValue(dsJsonPath.toFile(), IgsDemuxer.DisplaySetJson.class);

			// Use the first palette in the display set (palette id 0 is the default)
			IgsPalette palette = findPalette(dsJson);
			if (palette == null) {
				log.warn("No palette in {}, skipping objects", dsRef.getDirectory());
				continue;
			}

			if (dsJson.getObjects() == null)
				continue;

			for (IgsDemuxer.ObjectMetadata objMeta : dsJson.getObjects()) {
				if (objMeta.getRleFile() == null)
					continue;

				Path rleFile = dsDir.resolve(objMeta.getRleFile());
				if (!Files.exists(rleFile)) {
					log.warn("RLE file not found: {}", rleFile);
					continue;
				}

				String pngName = objMeta.getRleFile().replaceAll("\\.rle$", ".png");
				Path pngPath;
				if (outputDir != null) {
					pngPath = outputDir.resolve(dsRef.getDirectory()).resolve(pngName);
				} else {
					pngPath = dsDir.resolve(pngName);
				}

				convertToPng(rleFile, objMeta.getWidth(), objMeta.getHeight(), palette, pngPath);
			}
		}
	}

	// -------------------------------------------------------------------------
	// RLE decoder
	// -------------------------------------------------------------------------

	/**
	 * Decodes PG/IG RLE data into a {@link BufferedImage} using the given palette.
	 *
	 * @param rleData raw RLE bytes
	 * @param width   image width
	 * @param height  image height
	 * @param palette palette for index→colour mapping
	 * @return ARGB image
	 */
	public BufferedImage decodeRle(byte[] rleData, int width, int height, IgsPalette palette) {
		if (width <= 0 || height <= 0 || rleData == null || palette == null || rleData.length == 0) {
			log.warn("Invalid image. Dimensions: {}×{}", width, height);
			return null;
		}
		int[] argbPalette = buildArgbPalette(palette);
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		int x = 0;
		int y = 0;
		int pos = 0;

		while (pos < rleData.length && y < height) {
			int first = rleData[pos++] & 0xFF;

			if (first != 0) {
				// Single pixel of colour 'first'
				if (x < width) {
					img.setRGB(x, y, argbPalette[first]);
				}
				x++;
			} else {
				// Escape byte — read second byte
				if (pos >= rleData.length)
					break;
				int second = rleData[pos++] & 0xFF;

				int flag = (second >> 6) & 0x03;
				switch (flag) {
				case 0 -> {
					// 0b00LLLLLL: L pixels of colour 0
					int len = second & 0x3F;
					if (len == 0) {
						// End-of-line
						x = 0;
						y++;
					} else {
						fillPixels(img, argbPalette[0], x, y, len, width);
						x += len;
					}
				}
				case 1 -> {
					// 0b01LLLLLL LLLLLLLL: 14-bit length, colour 0
					if (pos >= rleData.length)
						break;
					int len = ((second & 0x3F) << 8) | (rleData[pos++] & 0xFF);
					fillPixels(img, argbPalette[0], x, y, len, width);
					x += len;
				}
				case 2 -> {
					// 0b10LLLLLL CC: 6-bit length, colour C
					if (pos >= rleData.length)
						break;
					int len = second & 0x3F;
					int color = rleData[pos++] & 0xFF;
					fillPixels(img, argbPalette[color], x, y, len, width);
					x += len;
				}
				case 3 -> {
					// 0b11LLLLLL LLLLLLLL CC: 14-bit length, colour C
					if (pos + 1 >= rleData.length)
						break;
					int len = ((second & 0x3F) << 8) | (rleData[pos++] & 0xFF);
					int color = rleData[pos++] & 0xFF;
					fillPixels(img, argbPalette[color], x, y, len, width);
					x += len;
				}
				}
			}
		}

		return img;
	}

	// -------------------------------------------------------------------------
	// Palette helpers
	// -------------------------------------------------------------------------

	/**
	 * Builds a 256-entry ARGB palette from the IGS palette. Unmapped entries default to fully transparent black.
	 */
	static int[] buildArgbPalette(IgsPalette palette) {
		int[] argb = new int[256]; // all zeros = transparent black
		if (palette == null || palette.getEntries() == null)
			return argb;

		for (PaletteEntry e : palette.getEntries()) {
			int idx = e.getEntryId() & 0xFF;
			argb[idx] = ycbcrToArgb(e.getY(), e.getCb(), e.getCr(), e.getAlpha());
		}
		return argb;
	}

	/**
	 * Converts YCbCr + alpha to ARGB using BT.709 (Blu-ray standard).
	 * <p>
	 * ITU-R BT.709:
	 *
	 * <pre>
	 *   R = clip(Y + 1.5748 * (Cr - 128))
	 *   G = clip(Y - 0.1873 * (Cb - 128) - 0.4681 * (Cr - 128))
	 *   B = clip(Y + 1.8556 * (Cb - 128))
	 * </pre>
	 */
	static int ycbcrToArgb(int y, int cb, int cr, int alpha) {
		double yy = y;
		double cb2 = cb - 128.0;
		double cr2 = cr - 128.0;

		int r = clamp((int) Math.round(yy + 1.5748 * cr2));
		int g = clamp((int) Math.round(yy - 0.1873 * cb2 - 0.4681 * cr2));
		int b = clamp((int) Math.round(yy + 1.8556 * cb2));

		return ((alpha & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	private static void fillPixels(BufferedImage img, int argb, int x, int y, int len, int width) {
		for (int i = 0; i < len && (x + i) < width; i++) {
			if (y < img.getHeight()) {
				img.setRGB(x + i, y, argb);
			}
		}
	}

	/**
	 * Finds the first available palette in a display set JSON.
	 */
	private IgsPalette findPalette(IgsDemuxer.DisplaySetJson dsJson) {
		if (dsJson.getPalettes() != null && !dsJson.getPalettes().isEmpty()) {
			return dsJson.getPalettes().get(0);
		}
		return null;
	}

}
