package org.brts.lowlevel.titlemenu.layout;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.menu.GlyphFallbackText;
import org.brts.common.menu.TextStyle;
import org.brts.common.utils.composition.ImageComposition;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.ImagesComposition;
import org.brts.common.utils.expressions.ObjectExpression;
import org.brts.lowlevel.titlemenu.descriptor.BoundingBox;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import lombok.extern.slf4j.Slf4j;

/**
 * Grid layout with animated thumbnails extracted from source media, composited into the background video.
 * <p>
 * For each title, a thumbnail segment is extracted from the source media at the configured time offset (defaulting to
 * the middle of the video). These thumbnails are then composited into the background via
 * {@link org.brts.common.utils.composition.CompositedVideoGenerator}.
 * <p>
 * The IGS buttons are transparent overlays that match the thumbnail positions (with a highlight border for
 * selected/activated states), giving the illusion of selectable animated thumbnails integrated into the video.
 */
@Slf4j
public class ThumbnailGridLayout implements TitleMenuLayout {

	/** Border thickness for selected/activated thumbnail highlight. */
	private static final int HIGHLIGHT_BORDER = 4;

	/** Label height below each thumbnail. */
	private static final int LABEL_HEIGHT = 30;

	/** Gap between thumbnail and label. */
	private static final int LABEL_GAP = 4;

	@Override
	public LayoutResult layout(TitleMenuDescriptor descriptor, Path baseDir) throws IOException {
		LayoutConfig config = descriptor.getLayout();
		int screenW = descriptor.getScreenWidth();
		int screenH = descriptor.getScreenHeight();
		int columns = config.effectiveColumns();
		int spacingX = config.effectiveSpacingX();
		int spacingY = config.effectiveSpacingY();
		int thumbW = config.effectiveThumbnailWidth();
		int thumbH = config.effectiveThumbnailHeight();
		boolean showLabels = config.effectiveShowLabels();

		// When a valid bounding box is set, it replaces margins entirely
		BoundingBox box = config.getBoundingBox();
		int originX;
		int originY;
		int availableWidth;
		int availableHeight;
		if (box != null && box.isValid()) {
			originX = box.getX();
			originY = box.getY();
			availableWidth = box.getWidth();
			availableHeight = box.getHeight();
		} else {
			int marginTop = config.effectiveMarginTop();
			int marginLeft = config.effectiveMarginLeft();
			int marginRight = config.effectiveMarginRight();
			int marginBottom = config.effectiveMarginBottom();
			originX = marginLeft;
			originY = marginTop;
			availableWidth = screenW - marginLeft - marginRight;
			availableHeight = screenH - marginTop - marginBottom;
		}

		TextStyle globalStyle = resolveGlobalStyle(config.getTitleStyle());

		List<TitleEntry> titles = descriptor.getTitles();
		List<LayoutResult.PositionedButton> positioned = new ArrayList<>();

		// Cell size includes thumbnail + optional label + highlight border
		int cellW = thumbW + 2 * HIGHLIGHT_BORDER;
		int cellH = thumbH + 2 * HIGHLIGHT_BORDER + (showLabels ? LABEL_GAP + LABEL_HEIGHT : 0);

		// Compute grid positions
		int totalGridW = columns * cellW + (columns - 1) * spacingX;
		int gridStartX = originX + Math.max(0, (availableWidth - totalGridW) / 2);

		int rows = (int) Math.ceil((double) titles.size() / columns);
		int totalGridH = rows * cellH + (rows - 1) * spacingY;
		int gridStartY = originY + Math.max(0, (availableHeight - totalGridH) / 2);

		// Build composition for background video generation
		ImagesComposition composition = new ImagesComposition();
		List<ImageReference> images = new ArrayList<>();
		List<ImageComposition> compositions = new ArrayList<>();

		// Base image: background source video
		if (descriptor.getBackgroundMedia() != null && descriptor.getBackgroundMedia().isVideo()) {
			ImageReference bgRef = new ImageReference();
			bgRef.setImageId("background");
			bgRef.setVideoPath(descriptor.getBackgroundMedia().getVideoPath());
			images.add(bgRef);
			composition.setBaseImageId("background");
		}

		// Add thumbnail video overlays for each title
		for (int i = 0; i < titles.size(); i++) {
			TitleEntry title = titles.get(i);
			int col = i % columns;
			int row = i / columns;

			int cellX = gridStartX + col * (cellW + spacingX);
			int cellY = gridStartY + row * (cellH + spacingY);

			// Thumbnail position (inside the highlight border area)
			int thumbX = cellX + HIGHLIGHT_BORDER;
			int thumbY = cellY + HIGHLIGHT_BORDER;

			// Add video source for thumbnail (if available)
			if (title.getSourceMediaPath() != null && !title.getSourceMediaPath().isBlank()) {
				String videoPath = title.getSourceMediaPath();
				String imageId = "thumb_" + i;

				ImageReference thumbRef = new ImageReference();
				thumbRef.setImageId(imageId);
				thumbRef.setVideoPath(videoPath);
				images.add(thumbRef);

				// Overlay composition positioned at thumbnail location
				ImageComposition overlay = new ImageComposition();
				overlay.setImageId(imageId);

				ImageComposition.Point topLeft = new ImageComposition.Point();
				topLeft.setX(ObjectExpression.of(thumbX));
				topLeft.setY(ObjectExpression.of(thumbY));
				overlay.setTopLeft(topLeft);

				ImageComposition.Size resize = new ImageComposition.Size();
				resize.setWidth(ObjectExpression.of(thumbW));
				resize.setHeight(ObjectExpression.of(thumbH));
				overlay.setResize(resize);

				compositions.add(overlay);
			}

			// Generate IGS button images (transparent with highlight borders for selected/activated)
			BufferedImage normalImg = renderThumbnailButton(cellW, cellH, title.getDisplayName(), showLabels,
					globalStyle, TextStyle.parseColor(globalStyle.getNormalColor()), false);
			BufferedImage selectedImg = renderThumbnailButton(cellW, cellH, title.getDisplayName(), showLabels,
					globalStyle, TextStyle.parseColor(globalStyle.getSelectedColor()), true);
			BufferedImage activatedImg = renderThumbnailButton(cellW, cellH, title.getDisplayName(), showLabels,
					globalStyle, TextStyle.parseColor(globalStyle.getActivatedColor()), true);

			LayoutResult.PositionedButton btn = new LayoutResult.PositionedButton();
			btn.setTitleIndex(i);
			btn.setTitleNumber(title.getTitleNumber());
			btn.setX(cellX);
			btn.setY(cellY);
			btn.setNormalImage(normalImg);
			btn.setSelectedImage(selectedImg);
			btn.setActivatedImage(activatedImg);
			btn.setWidth(cellW);
			btn.setHeight(cellH);
			positioned.add(btn);
		}

		composition.setImages(images);
		composition.setCompositions(compositions);

		LayoutResult result = new LayoutResult();
		result.setButtons(positioned);
		result.setCompositeBackground(true);
		result.setBackgroundComposition(composition);

		log.info("ThumbnailGridLayout: {} titles in {}×{} grid ({}×{} thumbnails)", titles.size(), columns, rows,
				thumbW, thumbH);
		return result;
	}

	/**
	 * Renders a thumbnail button overlay image. For the normal state this is transparent (thumbnails are composited
	 * into the background). For selected/activated states a highlight border is drawn.
	 */
	private BufferedImage renderThumbnailButton(int width, int height, String label, boolean showLabel, TextStyle style,
			int colorArgb, boolean drawBorder) {
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		if (drawBorder) {
			// Draw highlight border around thumbnail area
			Color borderColor = new Color(colorArgb, true);
			g.setColor(borderColor);
			g.fillRect(0, 0, width, HIGHLIGHT_BORDER); // top
			g.fillRect(0, height - HIGHLIGHT_BORDER - (showLabel ? LABEL_GAP + LABEL_HEIGHT : 0), width,
					HIGHLIGHT_BORDER); // bottom
			g.fillRect(0, 0, HIGHLIGHT_BORDER, height - (showLabel ? LABEL_GAP + LABEL_HEIGHT : 0)); // left
			g.fillRect(width - HIGHLIGHT_BORDER, 0, HIGHLIGHT_BORDER,
					height - (showLabel ? LABEL_GAP + LABEL_HEIGHT : 0)); // right
		}

		// Draw label below thumbnail
		if (showLabel && label != null && !label.isEmpty()) {
			Font font = new Font(style.getFontName(), style.getFontStyle(),
					style.getFontSize() != null ? style.getFontSize() - 4 : 24);
			Font fallbackFont = GlyphFallbackText.fallbackFont(style, font);
			g.setFont(font);
			FontMetrics fm = g.getFontMetrics();

			int labelY = height - LABEL_HEIGHT + fm.getAscent();
			int labelX = Math.max(0, (width - GlyphFallbackText.width(g, label, font, fallbackFont)) / 2);

			g.setColor(new Color(colorArgb, true));
			GlyphFallbackText.drawString(g, GlyphFallbackText.split(label, font, fallbackFont), labelX, labelY);
		}

		g.dispose();
		return img;
	}

	private TextStyle resolveGlobalStyle(TextStyle descriptorStyle) {
		TextStyle base = new TextStyle();
		if (descriptorStyle != null) {
			base = descriptorStyle.mergeOver(base);
		}
		return base.withDefaults();
	}

}
