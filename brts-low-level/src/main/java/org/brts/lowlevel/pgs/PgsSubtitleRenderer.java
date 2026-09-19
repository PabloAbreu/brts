package org.brts.lowlevel.pgs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/pgs/PgsSubtitleRenderer.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.brts.lowlevel.subtitle.model.SubtitlePosition;

import lombok.RequiredArgsConstructor;

/**
 * Renders subtitle text into ARGB {@link BufferedImage}s suitable for PGS Object Definition Segments.
 * <p>
 * The renderer supports:
 * <ul>
 * <li>Multi-line text (line breaks in the cue text)</li>
 * <li>Inline HTML-style formatting tags: {@code <b>}, {@code <i>}, {@code <u>}</li>
 * <li>Configurable font, size, colour, and outline for legibility</li>
 * <li>Per-cue position overrides (absolute or alignment-based)</li>
 * </ul>
 *
 * <h2>Rendering pipeline</h2>
 * <ol>
 * <li>Strip HTML tags from text and build an {@link AttributedString} with bold/italic/underline ranges.</li>
 * <li>Measure the text bounding box.</li>
 * <li>Draw outline (thick stroke in outline colour) then fill (text colour) for legibility.</li>
 * </ol>
 *
 * @see PgsRenderConfig
 */
@RequiredArgsConstructor
public class PgsSubtitleRenderer {

	/** Pattern matching supported HTML tags and their closes. */
	private static final Pattern HTML_TAG = Pattern.compile("</?([biuBIU])>");

	private final PgsRenderConfig config;

	/**
	 * Result of rendering a single subtitle cue.
	 *
	 * @param image   the rendered ARGB bitmap (tightly cropped around the text)
	 * @param screenX the X coordinate where this image should be placed on the full screen
	 * @param screenY the Y coordinate where this image should be placed on the full screen
	 */
	public record RenderResult(BufferedImage image, int screenX, int screenY) {
	}

	/**
	 * Renders a subtitle text into an image and computes its screen position.
	 *
	 * @param text     the subtitle text (may contain {@code <b>}, {@code <i>}, {@code <u>} tags and newlines)
	 * @param position per-cue position override, or {@code null} for default
	 * @return the rendered result
	 */
	public RenderResult render(String text, SubtitlePosition position) {
		// Parse inline tags to produce plain text + style ranges
		ParsedText parsed = parseHtmlTags(text);

		// Split by newlines and render each line
		String[] lines = parsed.plainText.split("\n");
		List<LineMeasurement> measurements = new ArrayList<>();

		Font baseFont = new Font(config.getFontName(), Font.PLAIN, config.getFontSize());

		// Measure each line
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D gMeasure = scratch.createGraphics();
		gMeasure.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		gMeasure.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int maxLineWidth = config.getScreenWidth() - 2 * config.getHorizontalMargin();

		// We need to track character offsets per line
		int charOffset = 0;
		int totalHeight = 0;
		int maxWidth = 0;

		for (String line : lines) {
			if (line.isEmpty()) {
				// Empty line — add some spacing
				int lineHeight = gMeasure.getFontMetrics(baseFont).getHeight();
				measurements.add(new LineMeasurement(line, 0, charOffset, 0, lineHeight));
				totalHeight += lineHeight;
				charOffset += line.length() + 1; // +1 for the \n
				continue;
			}

			AttributedString attrLine = buildAttributedLine(line, charOffset, parsed.ranges, baseFont);

			TextLayout layout = new TextLayout(attrLine.getIterator(), gMeasure.getFontRenderContext());

			int lineWidth = (int) Math.ceil(layout.getAdvance());
			int lineHeight = (int) Math.ceil(layout.getAscent() + layout.getDescent() + layout.getLeading());

			measurements.add(new LineMeasurement(line, lineWidth, charOffset, 0, lineHeight));

			maxWidth = Math.max(maxWidth, lineWidth);
			totalHeight += lineHeight;
			charOffset += line.length() + 1;
		}
		gMeasure.dispose();

		// Add padding for the outline stroke
		int outlinePad = (int) Math.ceil(config.getOutlineWidth()) + 2;
		int imgWidth = Math.min(maxWidth + 2 * outlinePad, maxLineWidth + 2 * outlinePad);
		int imgHeight = totalHeight + 2 * outlinePad;

		// Ensure minimum size
		imgWidth = Math.max(imgWidth, 4);
		imgHeight = Math.max(imgHeight, 4);

		// Render
		BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

		// Draw each line centred horizontally
		charOffset = 0;
		float yPos = outlinePad;

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			LineMeasurement meas = measurements.get(i);

			if (line.isEmpty()) {
				yPos += meas.height;
				charOffset += line.length() + 1;
				continue;
			}

			AttributedString attrLine = buildAttributedLine(line, charOffset, parsed.ranges, baseFont);

			TextLayout layout = new TextLayout(attrLine.getIterator(), g.getFontRenderContext());

			float xPos = outlinePad + (maxWidth - meas.width) / 2.0f;
			yPos += layout.getAscent();

			// Draw outline (thick stroke)
			Shape textShape = layout.getOutline(AffineTransform.getTranslateInstance(xPos, yPos));
			g.setColor(new Color(config.getOutlineColor(), true));
			g.setStroke(new BasicStroke(config.getOutlineWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(textShape);

			// Draw fill
			g.setColor(new Color(config.getFontColor(), true));
			g.fill(textShape);

			yPos += layout.getDescent() + layout.getLeading();
			charOffset += line.length() + 1;
		}
		g.dispose();

		// Compute screen position
		int screenX;
		int screenY;

		if (position != null && position.isAbsolutePosition()) {
			screenX = position.getX();
			screenY = position.getY();
		} else {
			int alignment = (position != null) ? position.getAlignment() : 2;
			int[] pos = computeAlignedPosition(imgWidth, imgHeight, alignment);
			screenX = pos[0];
			screenY = pos[1];
		}

		// Clamp to screen bounds
		screenX = Math.max(0, Math.min(screenX, config.getScreenWidth() - imgWidth));
		screenY = Math.max(0, Math.min(screenY, config.getScreenHeight() - imgHeight));

		return new RenderResult(image, screenX, screenY);
	}

	// ── Alignment computation ───────────────────────────────────────────────

	/**
	 * Computes screen (x, y) for numpad-style alignment.
	 */
	private int[] computeAlignedPosition(int imgWidth, int imgHeight, int alignment) {
		int sw = config.getScreenWidth();
		int sh = config.getScreenHeight();
		int margin = config.getHorizontalMargin();
		double vRatio = config.getVerticalPositionRatio();

		int x, y;

		// Horizontal: 1,4,7 = left; 2,5,8 = centre; 3,6,9 = right
		int hPos = ((alignment - 1) % 3); // 0=left, 1=centre, 2=right
		switch (hPos) {
		case 0 -> x = margin;
		case 2 -> x = sw - margin - imgWidth;
		default -> x = (sw - imgWidth) / 2;
		}

		// Vertical: 1,2,3 = bottom; 4,5,6 = middle; 7,8,9 = top
		int vPos = ((alignment - 1) / 3); // 0=bottom, 1=middle, 2=top
		switch (vPos) {
		case 2 -> y = (int) (sh * 0.05); // top with small margin
		case 1 -> y = (sh - imgHeight) / 2; // middle
		default -> y = (int) (sh * vRatio) - imgHeight; // bottom
		}

		return new int[] { x, y };
	}

	// ── HTML tag parsing ────────────────────────────────────────────────────

	/**
	 * Strips HTML tags from the text and records style ranges.
	 */
	static ParsedText parseHtmlTags(String text) {
		List<StyleRange> ranges = new ArrayList<>();
		StringBuilder plain = new StringBuilder();

		// Track open tag positions
		int boldStart = -1, italicStart = -1, underlineStart = -1;

		Matcher m = HTML_TAG.matcher(text);
		int lastEnd = 0;

		while (m.find()) {
			// Append text before this tag
			plain.append(text, lastEnd, m.start());
			lastEnd = m.end();

			String tag = m.group(1).toLowerCase();
			boolean isClose = m.group().charAt(1) == '/';
			int pos = plain.length();

			switch (tag) {
			case "b" -> {
				if (!isClose) {
					boldStart = pos;
				} else if (boldStart >= 0) {
					ranges.add(new StyleRange(boldStart, pos, StyleRange.Type.BOLD));
					boldStart = -1;
				}
			}
			case "i" -> {
				if (!isClose) {
					italicStart = pos;
				} else if (italicStart >= 0) {
					ranges.add(new StyleRange(italicStart, pos, StyleRange.Type.ITALIC));
					italicStart = -1;
				}
			}
			case "u" -> {
				if (!isClose) {
					underlineStart = pos;
				} else if (underlineStart >= 0) {
					ranges.add(new StyleRange(underlineStart, pos, StyleRange.Type.UNDERLINE));
					underlineStart = -1;
				}
			}
			}
		}
		// Append remainder
		plain.append(text.substring(lastEnd));

		// Close any unclosed tags at end of text
		int end = plain.length();
		if (boldStart >= 0)
			ranges.add(new StyleRange(boldStart, end, StyleRange.Type.BOLD));
		if (italicStart >= 0)
			ranges.add(new StyleRange(italicStart, end, StyleRange.Type.ITALIC));
		if (underlineStart >= 0)
			ranges.add(new StyleRange(underlineStart, end, StyleRange.Type.UNDERLINE));

		return new ParsedText(plain.toString(), ranges);
	}

	/**
	 * Builds an {@link AttributedString} for a single line of text, applying style ranges that overlap this line's
	 * character range within the full plain text.
	 */
	private AttributedString buildAttributedLine(String line, int lineStartInFull, List<StyleRange> allRanges,
			Font baseFont) {

		if (line.isEmpty()) {
			// Fallback for empty lines
			AttributedString as = new AttributedString(" ");
			as.addAttribute(TextAttribute.FONT, baseFont);
			return as;
		}

		AttributedString as = new AttributedString(line);
		as.addAttribute(TextAttribute.FONT, baseFont, 0, line.length());

		int lineEnd = lineStartInFull + line.length();

		for (StyleRange range : allRanges) {
			// Compute overlap with this line
			int start = Math.max(range.start, lineStartInFull) - lineStartInFull;
			int end = Math.min(range.end, lineEnd) - lineStartInFull;
			if (start >= end || start >= line.length())
				continue;
			end = Math.min(end, line.length());

			switch (range.type) {
			case BOLD -> {
				as.addAttribute(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, start, end);
			}
			case ITALIC -> {
				as.addAttribute(TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, start, end);
			}
			case UNDERLINE -> {
				as.addAttribute(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON, start, end);
			}
			}
		}

		return as;
	}

	// ── Internal types ──────────────────────────────────────────────────────

	record ParsedText(String plainText, List<StyleRange> ranges) {
	}

	record StyleRange(int start, int end, Type type) {
		enum Type {

			BOLD, ITALIC, UNDERLINE

		}
	}

	private record LineMeasurement(String text, int width, int charOffset, int x, int height) {
	}

}
