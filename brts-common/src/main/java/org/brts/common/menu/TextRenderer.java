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
import java.util.ArrayList;
import java.util.List;

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

	/**
	 * Renders a text-only button in all three states, constraining the image width to {@code maxWidth} pixels.
	 * <p>
	 * When the text fits on a single line it is rendered at its natural width (no inflation). When it exceeds
	 * {@code maxWidth − 2 × paddingX} the text is word-wrapped onto multiple left-aligned lines. A single word that
	 * still exceeds the budget is truncated with a trailing {@code …}.
	 *
	 * @param text     the label text
	 * @param style    the fully-resolved text style (call {@link TextStyle#withDefaults()} first)
	 * @param maxWidth maximum outer image width in pixels
	 * @return the rendered button images
	 */
	public static ButtonImages renderTextButton(String text, TextStyle style, int maxWidth) {
		Font font = new Font(style.getFontName(), style.getFontStyle(), style.getFontSize());
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scratch.createGraphics();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		g.dispose();

		int padX = style.getPaddingX();
		int padY = style.getPaddingY();
		int maxContentWidth = Math.max(1, maxWidth - 2 * padX);

		int naturalWidth = (text != null && !text.isEmpty()) ? fm.stringWidth(text) : 0;

		if (naturalWidth <= maxContentWidth) {
			// Fast path: text fits — delegate to natural-width renderer unchanged
			return renderTextButton(text, style);
		}

		// Wrap path
		List<String> lines = wrapText(text != null ? text : "", fm, maxContentWidth);
		int imgWidth = maxWidth;
		int imgHeight = Math.max(lines.size() * fm.getHeight() + 2 * padY, 4);

		int normalColor = TextStyle.parseColor(style.getNormalColor());
		int selectedColor = TextStyle.parseColor(style.getSelectedColor());
		int activatedColor = TextStyle.parseColor(style.getActivatedColor());

		BufferedImage normalImg = renderMultilineState(imgWidth, imgHeight, lines, font, fm, normalColor, padX, padY,
				style);
		BufferedImage selectedImg = renderMultilineState(imgWidth, imgHeight, lines, font, fm, selectedColor, padX,
				padY, style);
		BufferedImage activatedImg = renderMultilineState(imgWidth, imgHeight, lines, font, fm, activatedColor, padX,
				padY, style);

		return new ButtonImages(normalImg, selectedImg, activatedImg, imgWidth, imgHeight);
	}

	/**
	 * Renders a text-only button in all three states at an explicit {@code (width, height)}.
	 * <p>
	 * The background shape (if configured) fills the entire bounding box. Text is centred vertically within the padded
	 * area. When the text exceeds {@code width − 2 × paddingX} it is word-wrapped; a single word that still exceeds the
	 * budget is truncated with a trailing {@code …}.
	 *
	 * @param text   the label text
	 * @param style  the fully-resolved text style (call {@link TextStyle#withDefaults()} first)
	 * @param width  exact outer image width in pixels
	 * @param height exact outer image height in pixels
	 * @return the rendered button images at the requested dimensions
	 */
	public static ButtonImages renderTextButton(String text, TextStyle style, int width, int height) {
		Font font = new Font(style.getFontName(), style.getFontStyle(), style.getFontSize());
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scratch.createGraphics();
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		g.dispose();

		int padX = style.getPaddingX();
		int padY = style.getPaddingY();
		int maxContentWidth = Math.max(1, width - 2 * padX);

		int naturalWidth = (text != null && !text.isEmpty()) ? fm.stringWidth(text) : 0;

		int normalColor = TextStyle.parseColor(style.getNormalColor());
		int selectedColor = TextStyle.parseColor(style.getSelectedColor());
		int activatedColor = TextStyle.parseColor(style.getActivatedColor());

		if (naturalWidth <= maxContentWidth) {
			BufferedImage normalImg = renderSingleState(width, height, text, font, fm, normalColor, padX, padY, style);
			BufferedImage selectedImg = renderSingleState(width, height, text, font, fm, selectedColor, padX, padY,
					style);
			BufferedImage activatedImg = renderSingleState(width, height, text, font, fm, activatedColor, padX, padY,
					style);
			return new ButtonImages(normalImg, selectedImg, activatedImg, width, height);
		}

		List<String> lines = wrapText(text != null ? text : "", fm, maxContentWidth);
		BufferedImage normalImg = renderMultilineState(width, height, lines, font, fm, normalColor, padX, padY, style);
		BufferedImage selectedImg = renderMultilineState(width, height, lines, font, fm, selectedColor, padX, padY,
				style);
		BufferedImage activatedImg = renderMultilineState(width, height, lines, font, fm, activatedColor, padX, padY,
				style);
		return new ButtonImages(normalImg, selectedImg, activatedImg, width, height);
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

	private static BufferedImage renderMultilineState(int width, int height, List<String> lines, Font font,
			FontMetrics fm, int textColorArgb, int padX, int padY, TextStyle style) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		drawBackgroundShape(g, width, height, style);

		g.setFont(font);
		int cx = padX;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			int lineY = padY + fm.getAscent() + i * fm.getHeight();

			if (Boolean.TRUE.equals(style.getShadow())) {
				g.setColor(new Color(TextStyle.parseColor(style.getShadowColor()), true));
				g.drawString(line, cx + 2, lineY + 2);
			}

			if (Boolean.TRUE.equals(style.getOutline())) {
				GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), line);
				Shape outline = gv.getOutline(cx, lineY);
				g.setColor(new Color(TextStyle.parseColor(style.getOutlineColor()), true));
				g.setStroke(new BasicStroke(style.getOutlineWidth()));
				g.draw(outline);
			}

			g.setColor(new Color(textColorArgb, true));
			g.drawString(line, cx, lineY);
		}

		g.dispose();
		return img;
	}

	/**
	 * Greedily wraps {@code text} at word boundaries so that each line's rendered width does not exceed
	 * {@code maxContentWidth}. A word that on its own exceeds the budget is truncated with {@code …}.
	 */
	static List<String> wrapText(String text, FontMetrics fm, int maxContentWidth) {
		List<String> result = new ArrayList<>();
		String[] words = text.split("\\s+", -1);
		StringBuilder current = new StringBuilder();

		for (String word : words) {
			if (word.isEmpty())
				continue;
			String candidate = current.length() == 0 ? word : current + " " + word;
			if (fm.stringWidth(candidate) <= maxContentWidth) {
				current = new StringBuilder(candidate);
			} else {
				if (current.length() > 0) {
					result.add(current.toString());
					current = new StringBuilder();
				}
				// The word alone — check if it fits or needs truncation
				if (fm.stringWidth(word) <= maxContentWidth) {
					current.append(word);
				} else {
					result.add(truncateWithEllipsis(word, fm, maxContentWidth));
				}
			}
		}

		if (current.length() > 0)
			result.add(current.toString());

		if (result.isEmpty())
			result.add("");

		return result;
	}

	/**
	 * Truncates {@code word} so that {@code word + "…"} fits within {@code maxContentWidth}, then appends {@code …}.
	 * Returns {@code "…"} when even that does not fit.
	 */
	static String truncateWithEllipsis(String word, FontMetrics fm, int maxContentWidth) {
		String ellipsis = "\u2026";
		int ellipsisWidth = fm.stringWidth(ellipsis);
		if (ellipsisWidth >= maxContentWidth)
			return ellipsis;
		int budget = maxContentWidth - ellipsisWidth;
		int end = 0;
		while (end < word.length() && fm.stringWidth(word.substring(0, end + 1)) <= budget) {
			end++;
		}
		return word.substring(0, end) + ellipsis;
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
