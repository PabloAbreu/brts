package org.brts.common.utils;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImageUtils {

	/**
	 * Returns {@code true} if every pixel in the image has an alpha value of 0.
	 */
	public static boolean isFullyTransparent(BufferedImage img) {
		int w = img.getWidth();
		int h = img.getHeight();
		// Sample a sparse grid first for an early exit on large images
		int step = Math.max(1, Math.min(w, h) / 16);
		for (int y = 0; y < h; y += step) {
			for (int x = 0; x < w; x += step) {
				if ((img.getRGB(x, y) >>> 24) != 0)
					return false;
			}
		}
		// Full scan for certainty
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if ((img.getRGB(x, y) >>> 24) != 0)
					return false;
			}
		}
		return true;
	}

	/**
	 * Alpha multiplier applied when generating ghost images from the selected state.
	 */
	private static final float GHOST_OPACITY = 0.65f;

	/** Marker symbol stamped on the bottom-right corner of ghost images. */
	private static final String GHOST_MARKER = "\u25CC"; // ◌ (dotted circle —
															// "placeholder")

	/**
	 * Creates a "ghost" copy of the given image: every pixel's alpha is multiplied by {@link #GHOST_OPACITY}, and a
	 * small marker symbol is painted in the bottom-right corner to signal the image is synthetic.
	 */
	public static BufferedImage createGhostImage(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage ghost = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		// Copy pixels with reduced alpha
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = src.getRGB(x, y);
				int alpha = (argb >>> 24) & 0xFF;
				int newAlpha = Math.round(alpha * GHOST_OPACITY);
				ghost.setRGB(x, y, (newAlpha << 24) | (argb & 0x00FFFFFF));
			}
		}

		// Stamp a marker symbol in the bottom-right corner
		Graphics2D g = ghost.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int fontSize = Math.max(10, Math.min(w, h) / 4);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));
		FontMetrics fm = g.getFontMetrics();
		int tw = fm.stringWidth(GHOST_MARKER);
		int margin = Math.max(2, fontSize / 6);
		int tx = w - tw - margin;
		int ty = h - margin;
		// Dark outline for readability
		g.setColor(new Color(0, 0, 0, 140));
		g.drawString(GHOST_MARKER, tx - 1, ty);
		g.drawString(GHOST_MARKER, tx + 1, ty);
		g.drawString(GHOST_MARKER, tx, ty - 1);
		g.drawString(GHOST_MARKER, tx, ty + 1);
		// Foreground
		g.setColor(new Color(255, 200, 60, 200));
		g.drawString(GHOST_MARKER, tx, ty);
		g.dispose();

		return ghost;
	}

	public static BufferedImage create(Path filePath) {
		try {
			return ImageIO.read(filePath.toFile());
		} catch (IOException e) {
			throw new RuntimeException("Failed to load image from path: " + filePath, e);
		}
	}

	/**
	 * Creates a deep copy of the provided BufferedImage.
	 *
	 * @param src the source BufferedImage to be copied
	 * @return a new BufferedImage that is a copy of the source image with ARGB color model
	 * @throws NullPointerException if src is null
	 */
	public static BufferedImage copy(BufferedImage src) {
		int w = src.getWidth();
		int h = src.getHeight();
		BufferedImage copy = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return copy;
	}

	/**
	 * Composes the overlay image onto the background image using the specified transform, start point, and opacity.
	 *
	 * @return the resulting composed image (same instance as background, modified in place)
	 */
	public static BufferedImage compose(BufferedImage background, BufferedImage overlay, AffineTransform transform,
			int x, int y, float opacity) {
		Graphics2D g = background.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g.translate(x, y);
		g.transform(transform);
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
		log.debug("Composing image at ({}, {}) with opacity {} and transform {}. background size : {}x{}", x, y,
				opacity, transform, background.getWidth(), background.getHeight());
		g.drawImage(overlay, 0, 0, null);
		g.dispose();
		return background;
	}

	public static BufferedImage scaleImage(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return dst;
	}

	public static BufferedImage convertToBgr(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = dst.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return dst;
	}
}
