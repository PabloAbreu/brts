package org.brts.middle.menu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;

/**
 * Text rendering style for menu button labels. All fields are optional — sensible
 * defaults are applied when {@code null}.
 *
 * <h2>Defaults</h2>
 * <ul>
 * <li>fontName: "SansSerif"</li>
 * <li>fontSize: 28</li>
 * <li>normalColor: white (#FFFFFF)</li>
 * <li>selectedColor: yellow (#FFD700)</li>
 * <li>activatedColor: orange (#FF6600)</li>
 * <li>shadow: false</li>
 * <li>outline: false</li>
 * <li>backgroundShape: NONE</li>
 * <li>backgroundAlpha: 128 (50 % transparent)</li>
 * </ul>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TextStyle {

	/**
	 * Font family name (e.g. "SansSerif", "Serif", "Monospaced", or a system font name).
	 */
	private String fontName;

	/** Font size in points. */
	private Integer fontSize;

	/** Font style: PLAIN=0, BOLD=1, ITALIC=2, BOLD_ITALIC=3. */
	private Integer fontStyle;

	// ── Per-state colours (ARGB hex, e.g. "#FFFFFFFF") ──────────────────────

	/** Text colour in the normal (idle) state, as "#AARRGGBB" or "#RRGGBB". */
	private String normalColor;

	/** Text colour in the selected (focused) state. */
	private String selectedColor;

	/** Text colour in the activated (pressed) state. */
	private String activatedColor;

	// ── Text effects ────────────────────────────────────────────────────────

	/** Whether to draw a drop shadow behind the text. */
	private Boolean shadow;

	/** Shadow colour as "#AARRGGBB" (default: semi-transparent black). */
	private String shadowColor;

	/** Whether to draw an outline stroke around the text glyphs. */
	private Boolean outline;

	/** Outline colour as "#AARRGGBB" (default: black). */
	private String outlineColor;

	/** Outline stroke width in pixels (default: 2). */
	private Float outlineWidth;

	// ── Background shape ────────────────────────────────────────────────────

	/**
	 * Shape drawn underneath the text. Possible values: NONE, RECTANGLE,
	 * ROUNDED_RECTANGLE.
	 */
	private BackgroundShape backgroundShape;

	/** Background shape fill colour as "#AARRGGBB". */
	private String backgroundColor;

	/**
	 * Alpha (0–255) of the background shape. Overrides the alpha in backgroundColor if
	 * set.
	 */
	private Integer backgroundAlpha;

	/** Corner radius for ROUNDED_RECTANGLE (default: 12). */
	private Integer cornerRadius;

	/** Horizontal padding around the text inside the background shape. */
	private Integer paddingX;

	/** Vertical padding around the text inside the background shape. */
	private Integer paddingY;

	// ── Background shape enum ───────────────────────────────────────────────

	public enum BackgroundShape {

		NONE, RECTANGLE, ROUNDED_RECTANGLE

	}

	// ── Merge / defaults ────────────────────────────────────────────────────

	/**
	 * Returns a new {@code TextStyle} that merges this style's non-null fields over the
	 * given base (global) style. Fields set on {@code this} take precedence; unset fields
	 * fall back to the base.
	 */
	public TextStyle mergeOver(TextStyle base) {
		if (base == null)
			return this;
		TextStyle merged = new TextStyle();
		merged.fontName = coalesce(this.fontName, base.fontName);
		merged.fontSize = coalesce(this.fontSize, base.fontSize);
		merged.fontStyle = coalesce(this.fontStyle, base.fontStyle);
		merged.normalColor = coalesce(this.normalColor, base.normalColor);
		merged.selectedColor = coalesce(this.selectedColor, base.selectedColor);
		merged.activatedColor = coalesce(this.activatedColor, base.activatedColor);
		merged.shadow = coalesce(this.shadow, base.shadow);
		merged.shadowColor = coalesce(this.shadowColor, base.shadowColor);
		merged.outline = coalesce(this.outline, base.outline);
		merged.outlineColor = coalesce(this.outlineColor, base.outlineColor);
		merged.outlineWidth = coalesce(this.outlineWidth, base.outlineWidth);
		merged.backgroundShape = coalesce(this.backgroundShape, base.backgroundShape);
		merged.backgroundColor = coalesce(this.backgroundColor, base.backgroundColor);
		merged.backgroundAlpha = coalesce(this.backgroundAlpha, base.backgroundAlpha);
		merged.cornerRadius = coalesce(this.cornerRadius, base.cornerRadius);
		merged.paddingX = coalesce(this.paddingX, base.paddingX);
		merged.paddingY = coalesce(this.paddingY, base.paddingY);
		return merged;
	}

	/**
	 * Returns a fully-resolved copy of this style where all {@code null} fields are
	 * replaced with sensible defaults.
	 */
	public TextStyle withDefaults() {
		TextStyle d = new TextStyle();
		d.fontName = coalesce(fontName, "SansSerif");
		d.fontSize = coalesce(fontSize, 28);
		d.fontStyle = coalesce(fontStyle, Font.BOLD);
		d.normalColor = coalesce(normalColor, "#FFFFFFFF");
		d.selectedColor = coalesce(selectedColor, "#FFFFD700");
		d.activatedColor = coalesce(activatedColor, "#FFFF6600");
		d.shadow = coalesce(shadow, false);
		d.shadowColor = coalesce(shadowColor, "#80000000");
		d.outline = coalesce(outline, false);
		d.outlineColor = coalesce(outlineColor, "#FF000000");
		d.outlineWidth = coalesce(outlineWidth, 2.0f);
		d.backgroundShape = coalesce(backgroundShape, BackgroundShape.NONE);
		d.backgroundColor = coalesce(backgroundColor, "#80000000");
		d.backgroundAlpha = coalesce(backgroundAlpha, 128);
		d.cornerRadius = coalesce(cornerRadius, 12);
		d.paddingX = coalesce(paddingX, 16);
		d.paddingY = coalesce(paddingY, 8);
		return d;
	}

	private static <T> T coalesce(T a, T b) {
		return a != null ? a : b;
	}

	// ── Colour parsing ──────────────────────────────────────────────────────

	/**
	 * Parses an "#AARRGGBB" or "#RRGGBB" colour string to an ARGB int.
	 */
	public static int parseColor(String colorStr) {
		if (colorStr == null || colorStr.isEmpty())
			return 0xFFFFFFFF;
		String hex = colorStr.startsWith("#") ? colorStr.substring(1) : colorStr;
		if (hex.length() == 6) {
			return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
		}
		return (int) Long.parseUnsignedLong(hex, 16);
	}

}
