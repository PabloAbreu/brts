package org.brts.middle.menu.render;

import org.brts.middle.menu.descriptor.TextStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Renders button images for the three IGS button states (normal, selected, activated)
 * using Java2D {@link Graphics2D}.
 * <p>
 * Each button is rendered as a combination of:
 * <ul>
 *   <li>An optional background shape (rectangle / rounded rectangle) with configurable alpha</li>
 *   <li>Text label with font, colour, optional shadow and outline</li>
 *   <li>An optional icon (loaded from a B&amp;W PNG, colourised per state)</li>
 * </ul>
 * All three states share the same dimensions but differ in text/icon colour.
 */
public class ButtonImageRenderer {

    private static final Logger log = LoggerFactory.getLogger(ButtonImageRenderer.class);

    /** Gap between icon and text when both are present. */
    private static final int ICON_TEXT_GAP = 12;

    /** Default icon size (scaled to this height). */
    private static final int ICON_HEIGHT = 32;

    private ButtonImageRenderer() {}

    /**
     * Result of rendering a single button's three state images.
     *
     * @param normal    normal (idle) state image
     * @param selected  selected (focused) state image
     * @param activated activated (pressed) state image
     * @param width     common width of all three images
     * @param height    common height of all three images
     */
    public record ButtonImages(
            BufferedImage normal,
            BufferedImage selected,
            BufferedImage activated,
            int width,
            int height
    ) {}

    /**
     * Renders a text-only button in all three states.
     *
     * @param text  the label text
     * @param style the fully-resolved text style (call {@link TextStyle#withDefaults()} first)
     * @return the rendered button images
     */
    public static ButtonImages renderTextButton(String text, TextStyle style) {
        return renderButton(text, null, style);
    }

    /**
     * Renders a button with optional icon and text.
     *
     * @param text     the label text (may be null if icon-only)
     * @param iconPath path to a B&amp;W PNG icon (may be null if text-only)
     * @param style    the fully-resolved text style
     * @return the rendered button images
     */
    public static ButtonImages renderButton(String text, Path iconPath, TextStyle style) {
        // Load icon if specified
        BufferedImage iconBw = null;
        if (iconPath != null) {
            try {
                iconBw = ImageIO.read(iconPath.toFile());
            } catch (IOException e) {
                log.warn("Failed to load icon {}: {}", iconPath, e.getMessage());
            }
        }

        // Measure text dimensions
        Font font = new Font(style.getFontName(), style.getFontStyle(), style.getFontSize());
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();

        int textWidth = (text != null && !text.isEmpty()) ? fm.stringWidth(text) : 0;
        int textHeight = fm.getHeight();
        g.dispose();

        // Icon dimensions (scaled to ICON_HEIGHT, preserving aspect ratio)
        int iconW = 0, iconH = 0;
        if (iconBw != null) {
            double scale = (double) ICON_HEIGHT / iconBw.getHeight();
            iconW = (int) Math.round(iconBw.getWidth() * scale);
            iconH = ICON_HEIGHT;
        }

        // Total content dimensions
        int contentWidth = iconW + (iconW > 0 && textWidth > 0 ? ICON_TEXT_GAP : 0) + textWidth;
        int contentHeight = Math.max(iconH, textHeight);

        int padX = style.getPaddingX();
        int padY = style.getPaddingY();

        int imgWidth = contentWidth + 2 * padX;
        int imgHeight = contentHeight + 2 * padY;

        // Ensure minimum size
        imgWidth = Math.max(imgWidth, 4);
        imgHeight = Math.max(imgHeight, 4);

        int normalColor    = TextStyle.parseColor(style.getNormalColor());
        int selectedColor  = TextStyle.parseColor(style.getSelectedColor());
        int activatedColor = TextStyle.parseColor(style.getActivatedColor());

        BufferedImage normalImg    = renderSingleState(imgWidth, imgHeight, text, font, fm,
                normalColor, iconBw, iconW, iconH, contentWidth, contentHeight, padX, padY, style);
        BufferedImage selectedImg  = renderSingleState(imgWidth, imgHeight, text, font, fm,
                selectedColor, iconBw, iconW, iconH, contentWidth, contentHeight, padX, padY, style);
        BufferedImage activatedImg = renderSingleState(imgWidth, imgHeight, text, font, fm,
                activatedColor, iconBw, iconW, iconH, contentWidth, contentHeight, padX, padY, style);

        return new ButtonImages(normalImg, selectedImg, activatedImg, imgWidth, imgHeight);
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private static BufferedImage renderSingleState(
            int width, int height, String text, Font font, FontMetrics fm,
            int textColorArgb, BufferedImage iconBw,
            int iconW, int iconH,
            int contentWidth, int contentHeight,
            int padX, int padY, TextStyle style
    ) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Background shape
        drawBackgroundShape(g, width, height, style);

        // 2. Compute content origin (centred in the padded area)
        int cx = padX;

        // 3. Icon (colourised)
        if (iconBw != null && iconW > 0) {
            BufferedImage colourised = colouriseIcon(iconBw, iconW, iconH, textColorArgb);
            int iconY = padY + (contentHeight - iconH) / 2;
            g.drawImage(colourised, cx, iconY, null);
            cx += iconW + ICON_TEXT_GAP;
        }

        // 4. Text
        if (text != null && !text.isEmpty()) {
            g.setFont(font);
            int textY = padY + fm.getAscent() + (contentHeight - fm.getHeight()) / 2;

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
        if (style.getBackgroundShape() == null
                || style.getBackgroundShape() == TextStyle.BackgroundShape.NONE) {
            return;
        }

        int bgColor = TextStyle.parseColor(style.getBackgroundColor());
        // Override alpha if backgroundAlpha is set
        int alpha = style.getBackgroundAlpha();
        bgColor = (alpha << 24) | (bgColor & 0x00FFFFFF);

        g.setColor(new Color(bgColor, true));

        switch (style.getBackgroundShape()) {
            case RECTANGLE -> g.fillRect(0, 0, width, height);
            case ROUNDED_RECTANGLE -> {
                int r = style.getCornerRadius();
                g.fill(new RoundRectangle2D.Double(0, 0, width, height, r, r));
            }
            default -> { /* NONE — handled above */ }
        }
    }

    /**
     * Colourises a black-and-white icon: white pixels get the target colour,
     * black pixels remain transparent. Intermediate greys are alpha-blended.
     */
    private static BufferedImage colouriseIcon(BufferedImage bwIcon, int targetW, int targetH, int targetColorArgb) {
        // Scale icon
        BufferedImage scaled = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = scaled.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        sg.drawImage(bwIcon, 0, 0, targetW, targetH, null);
        sg.dispose();

        int tr = (targetColorArgb >> 16) & 0xFF;
        int tg = (targetColorArgb >> 8) & 0xFF;
        int tb = targetColorArgb & 0xFF;

        BufferedImage result = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < targetH; y++) {
            for (int x = 0; x < targetW; x++) {
                int px = scaled.getRGB(x, y);
                int a = (px >> 24) & 0xFF;
                // Use luminance of source pixel as alpha multiplier
                int lum = ((px >> 16) & 0xFF) * 299 + ((px >> 8) & 0xFF) * 587 + (px & 0xFF) * 114;
                int lumAlpha = lum / 1000; // 0..255
                int finalAlpha = (a * lumAlpha) / 255;
                result.setRGB(x, y, (finalAlpha << 24) | (tr << 16) | (tg << 8) | tb);
            }
        }
        return result;
    }
}
