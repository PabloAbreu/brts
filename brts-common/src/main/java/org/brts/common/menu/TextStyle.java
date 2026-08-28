package org.brts.common.menu;

import java.awt.Font;

import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.BrtsValue;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Text rendering style for menu button labels. All fields are optional — sensible defaults are applied when
 * {@code null}.
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
@ToString
@Slf4j
@BrtsValue("textStyle")
public class TextStyle {

	/**
	 * Font family name (e.g. "SansSerif", "Serif", "Monospaced", or a system font name).
	 */
	private @BrtsValue String fontName;

	/** Font family used to render glyphs the primary font cannot display (same size/style/colour). */
	private @BrtsValue String fallbackFontName;

	/** Font size in points. */
	private @BrtsValue Integer fontSize;

	/** Font style: PLAIN=0, BOLD=1, ITALIC=2, BOLD_ITALIC=3. */
	private @BrtsValue Integer fontStyle;

	// ── Per-state colours (ARGB hex, e.g. "#FFFFFFFF") ──────────────────────

	/** Text colour in the normal (idle) state, as "#AARRGGBB" or "#RRGGBB". */
	private @BrtsValue String normalColor;

	/** Text colour in the selected (focused) state. */
	private @BrtsValue String selectedColor;

	/** Text colour in the activated (pressed) state. */
	private @BrtsValue String activatedColor;

	// ── Text effects ────────────────────────────────────────────────────────

	/** Whether to draw a drop shadow behind the text. */
	private @BrtsValue Boolean shadow;

	/** Shadow colour as "#AARRGGBB" (default: semi-transparent black). */
	private @BrtsValue String shadowColor;

	/** Whether to draw an outline stroke around the text glyphs. */
	private @BrtsValue Boolean outline;

	/** Outline colour as "#AARRGGBB" (default: black). */
	private @BrtsValue String outlineColor;

	/** Outline stroke width in pixels (default: 2). */
	private @BrtsValue Float outlineWidth;

	// ── Background shape ────────────────────────────────────────────────────

	/**
	 * Shape drawn underneath the text. Possible values: NONE, RECTANGLE, ROUNDED_RECTANGLE.
	 */
	private @BrtsValue BackgroundShape backgroundShape;

	/** Background shape fill colour as "#AARRGGBB". */
	private @BrtsValue String backgroundColor;

	/**
	 * Alpha (0–255) of the background shape. Overrides the alpha in backgroundColor if set.
	 */
	private @BrtsValue Integer backgroundAlpha;

	/** Corner radius for ROUNDED_RECTANGLE (default: 12). */
	private @BrtsValue Integer cornerRadius;

	/** Horizontal padding around the text inside the background shape. */
	private @BrtsValue Integer paddingX;

	/** Vertical padding around the text inside the background shape. */
	private @BrtsValue Integer paddingY;

	// ── Background shape enum ───────────────────────────────────────────────

	public enum BackgroundShape {

		NONE, RECTANGLE, ROUNDED_RECTANGLE

	}

	// ── Merge / defaults ────────────────────────────────────────────────────

	/**
	 * Returns a new {@code TextStyle} that merges this style's non-null fields over the given base (global) style.
	 * Fields set on {@code this} take precedence; unset fields fall back to the base.
	 */
	public TextStyle mergeOver(TextStyle base) {
		if (base == null)
			return this;
		return coalesce(this, base);
	}

	/**
	 * Returns a fully-resolved copy of this style where all {@code null} fields are replaced with sensible defaults.
	 */
	public TextStyle withDefaults() {
		TextStyle defaultStyle = BrtsFileConfig.getInstance().fillPojo(new TextStyle());
		defaultStyle = coalesce(defaultStyle, hardCodedDefaults());
		log.debug("Applying defaults to TextStyle: base defaults={}, style with overrides={}", defaultStyle, this);
		return coalesce(this, defaultStyle);
	}

	private static TextStyle hardCodedDefaults() {
		TextStyle d = new TextStyle();
		d.fontName = "SansSerif";
		d.fallbackFontName = "Dialog";
		d.fontSize = 28;
		d.fontStyle = Font.BOLD;
		d.normalColor = "#FFFFFFFF";
		d.selectedColor = "#FFFFD700";
		d.activatedColor = "#FFFF6600";
		d.shadow = false;
		d.shadowColor = "#80000000";
		d.outline = false;
		d.outlineColor = "#FF000000";
		d.outlineWidth = 2.0f;
		d.backgroundShape = BackgroundShape.NONE;
		d.backgroundColor = "#80000000";
		d.backgroundAlpha = 128;
		d.cornerRadius = 12;
		d.paddingX = 16;
		d.paddingY = 8;
		return d;
	}

	/**
	 * Merges the non-null fields of the overlay style over the base style, returning a new TextStyle. The overlay takes
	 * precedence over the base style.
	 *
	 * @param overlay the style whose non-null fields will override the base style
	 * @param base    the base style to be overridden
	 * @return a new TextStyle with the merged values
	 */
	private TextStyle coalesce(TextStyle overlay, TextStyle base) {
		TextStyle result = new TextStyle();
		result.fontName = coalesce(overlay.fontName, base.fontName);
		result.fallbackFontName = coalesce(overlay.fallbackFontName, base.fallbackFontName);
		result.fontSize = coalesce(overlay.fontSize, base.fontSize);
		result.fontStyle = coalesce(overlay.fontStyle, base.fontStyle);
		result.normalColor = coalesce(overlay.normalColor, base.normalColor);
		result.selectedColor = coalesce(overlay.selectedColor, base.selectedColor);
		result.activatedColor = coalesce(overlay.activatedColor, base.activatedColor);
		result.shadow = coalesce(overlay.shadow, base.shadow);
		result.shadowColor = coalesce(overlay.shadowColor, base.shadowColor);
		result.outline = coalesce(overlay.outline, base.outline);
		result.outlineColor = coalesce(overlay.outlineColor, base.outlineColor);
		result.outlineWidth = coalesce(overlay.outlineWidth, base.outlineWidth);
		result.backgroundShape = coalesce(overlay.backgroundShape, base.backgroundShape);
		result.backgroundColor = coalesce(overlay.backgroundColor, base.backgroundColor);
		result.backgroundAlpha = coalesce(overlay.backgroundAlpha, base.backgroundAlpha);
		result.cornerRadius = coalesce(overlay.cornerRadius, base.cornerRadius);
		result.paddingX = coalesce(overlay.paddingX, base.paddingX);
		result.paddingY = coalesce(overlay.paddingY, base.paddingY);
		return result;
	}

	private static <T> T coalesce(T a, T b) {
		return a != null ? a : b;
	}

	// ── Colour parsing ──────────────────────────────────────────────────────

	/** Parses an "#AARRGGBB" or "#RRGGBB" colour string to an ARGB int. */
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
