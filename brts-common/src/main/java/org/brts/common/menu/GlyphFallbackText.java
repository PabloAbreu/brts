package org.brts.common.menu;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into runs rendered with a primary font, falling back to a secondary font for code points the primary font
 * cannot display (e.g. symbol glyphs such as arrows missing from the user-selected font).
 */
public final class GlyphFallbackText {

	private GlyphFallbackText() {
	}

	/** A contiguous slice of text to be rendered with a single font. */
	public record Run(Font font, String text) {
	}

	/** Builds the fallback font from {@code style}, reusing the primary font when no fallback is configured. */
	public static Font fallbackFont(TextStyle style, Font primary) {
		String name = style.getFallbackFontName();
		if (name == null || name.isBlank()) {
			return primary;
		}
		return new Font(name, style.getFontStyle(), style.getFontSize());
	}

	/**
	 * Splits {@code text} into runs, using {@code fallback} for any code point {@code primary} cannot display.
	 */
	public static List<Run> split(String text, Font primary, Font fallback) {
		List<Run> runs = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return runs;
		}
		if (primary.equals(fallback)) {
			runs.add(new Run(primary, text));
			return runs;
		}

		StringBuilder current = new StringBuilder();
		Font currentFont = null;
		int i = 0;
		while (i < text.length()) {
			int cp = text.codePointAt(i);
			int charCount = Character.charCount(cp);
			Font fontForCp = primary.canDisplay(cp) ? primary : fallback;

			if (currentFont != null && !currentFont.equals(fontForCp)) {
				runs.add(new Run(currentFont, current.toString()));
				current.setLength(0);
			}
			currentFont = fontForCp;
			current.appendCodePoint(cp);
			i += charCount;
		}
		if (current.length() > 0) {
			runs.add(new Run(currentFont, current.toString()));
		}
		return runs;
	}

	/** Sums the rendered width of {@code runs}, each measured with its own font. */
	public static int width(Graphics2D g, List<Run> runs) {
		int total = 0;
		for (Run run : runs) {
			total += g.getFontMetrics(run.font()).stringWidth(run.text());
		}
		return total;
	}

	/** Convenience combining {@link #split} and {@link #width}. */
	public static int width(Graphics2D g, String text, Font primary, Font fallback) {
		return width(g, split(text, primary, fallback));
	}

	/** Draws each run left-to-right starting at baseline {@code (x, y)}, using the caller's currently set colour. */
	public static void drawString(Graphics2D g, List<Run> runs, int x, int y) {
		int cx = x;
		for (Run run : runs) {
			g.setFont(run.font());
			g.drawString(run.text(), cx, y);
			cx += g.getFontMetrics(run.font()).stringWidth(run.text());
		}
	}

	/** Builds the combined glyph outline of {@code runs} positioned at baseline {@code (x, y)}. */
	public static Shape outline(Graphics2D g, List<Run> runs, int x, int y) {
		Area combined = new Area();
		int cx = x;
		for (Run run : runs) {
			GlyphVector gv = run.font().createGlyphVector(g.getFontRenderContext(), run.text());
			Shape runOutline = gv.getOutline(cx, y);
			combined.add(new Area(runOutline));
			cx += g.getFontMetrics(run.font()).stringWidth(run.text());
		}
		return combined;
	}

}
