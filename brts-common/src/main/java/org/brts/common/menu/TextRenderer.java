package org.brts.common.menu;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Renders text labels as ARGB images for the three IGS button states (normal, selected, activated).
 * <p>
 * This utility lives in {@code brts-common} so that both low-level and middle-level modules can produce text-based
 * button images without code duplication.
 */
public final class TextRenderer {

	/** Gap between icon and text when both are present. */
	private static final int ICON_TEXT_GAP = 12;

	private TextRenderer() {
	}

	/**
	 * Result of rendering a single button's three state images.
	 *
	 * @param normal    normal (idle) state image
	 * @param selected  selected (focused) state image
	 * @param activated activated (pressed) state image
	 * @param width     common width of all three images
	 * @param height    common height of all three images
	 */
	public record ButtonImages(BufferedImage normal, BufferedImage selected, BufferedImage activated, int width,
			int height) {
	}

	/**
	 * Renders a text-only button in all three states.
	 *
	 * @param text  the label text
	 * @param style the fully-resolved text style (call {@link TextStyle#withDefaults()} first)
	 * @return the rendered button images
	 */
	public static ButtonImages renderTextButton(String text, TextStyle style) {
		Font font = new Font(style.getFontName(), style.getFontStyle(), style.getFontSize());
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scratch.createGraphics();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();

		int textWidth = (text != null && !text.isEmpty()) ? fm.stringWidth(text) : 0;
		int textHeight = fm.getHeight();
		g.dispose();

		int contentWidth = textWidth;
		int contentHeight = textHeight;

		int padX = style.getPaddingX();
		int padY = style.getPaddingY();

		int imgWidth = Math.max(contentWidth + 2 * padX, 4);
		int imgHeight = Math.max(contentHeight + 2 * padY, 4);

		int normalColor = TextStyle.parseColor(style.getNormalColor());
		int selectedColor = TextStyle.parseColor(style.getSelectedColor());
		int activatedColor = TextStyle.parseColor(style.getActivatedColor());

		BufferedImage normalImg = renderSingleState(imgWidth, imgHeight, text, font, fm, normalColor, padX, padY,
				style);
		BufferedImage selectedImg = renderSingleState(imgWidth, imgHeight, text, font, fm, selectedColor, padX, padY,
				style);
		BufferedImage activatedImg = renderSingleState(imgWidth, imgHeight, text, font, fm, activatedColor, padX, padY,
				style);

		return new ButtonImages(normalImg, selectedImg, activatedImg, imgWidth, imgHeight);
	}

	// ── Internal ────────────────────────────────────────────────────────────

	private static BufferedImage renderSingleState(int width, int height, String text, Font font, FontMetrics fm,
			int textColorArgb, int padX, int padY, TextStyle style) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		// 1. Background shape
		drawBackgroundShape(g, width, height, style);

		// 2. Compute content origin
		int cx = padX;
		int contentHeight = fm.getHeight();
		int textY = padY + fm.getAscent() + (height - 2 * padY - contentHeight) / 2;

		// 3. Text
		if (text != null && !text.isEmpty()) {
			g.setFont(font);

			// Shadow
			if (Boolean.TRUE.equals(style.getShadow())) {
				g.setColor(new Color(TextStyle.parseColor(style.getShadowColor()), true));
				g.drawString(text, cx + 2, textY + 2);
			}

			// Outline
			if (Boolean.TRUE.equals(style.getOutline())) {
				GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
				Shape outline = gv.getOutline(cx, textY);
				g.setColor(new Color(TextStyle.parseColor(style.getOutlineColor()), true));
				g.setStroke(new BasicStroke(style.getOutlineWidth()));
				g.draw(outline);
			}

			// Main text
			g.setColor(new Color(textColorArgb, true));
			g.drawString(text, cx, textY);
		}

		g.dispose();
		return img;
	}

	private static void drawBackgroundShape(Graphics2D g, int width, int height, TextStyle style) {
		if (style.getBackgroundShape() == null || style.getBackgroundShape() == TextStyle.BackgroundShape.NONE) {
			return;
		}

		int bgColor = TextStyle.parseColor(style.getBackgroundColor());
		int alpha = style.getBackgroundAlpha();
		bgColor = (alpha << 24) | (bgColor & 0x00FFFFFF);

		g.setColor(new Color(bgColor, true));

		switch (style.getBackgroundShape()) {
		case RECTANGLE -> g.fillRect(0, 0, width, height);
		case ROUNDED_RECTANGLE -> {
			int r = style.getCornerRadius();
			g.fill(new RoundRectangle2D.Double(0, 0, width, height, r, r));
		}
		default -> {
			/* NONE — handled above */ }
		}
	}

}
