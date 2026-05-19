package org.brts.lowlevel.titlemenu.layout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brts.common.menu.TextRenderer;
import org.brts.common.menu.TextRenderer.ButtonImages;
import org.brts.common.menu.TextStyle;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple text-list layout: renders each title as a selectable text button and arranges them vertically (or in columns).
 * <p>
 * Buttons are centred horizontally within each column. Vertical spacing is automatic based on button height and
 * configured spacing.
 */
public class TextListLayout implements TitleMenuLayout {

	private static final Logger log = LoggerFactory.getLogger(TextListLayout.class);

	@Override
	public LayoutResult layout(TitleMenuDescriptor descriptor, Path baseDir) throws IOException {
		LayoutConfig config = descriptor.getLayout();
		int screenW = descriptor.getScreenWidth();
		int screenH = descriptor.getScreenHeight();
		int columns = config.effectiveColumns();
		int marginTop = config.effectiveMarginTop();
		int marginBottom = config.effectiveMarginBottom();
		int marginLeft = config.effectiveMarginLeft();
		int marginRight = config.effectiveMarginRight();
		int spacingX = config.effectiveSpacingX();
		int spacingY = config.effectiveSpacingY();

		TextStyle globalStyle = resolveGlobalStyle(config.getTitleStyle());
		int maxButtonWidth = config.effectiveMaxButtonWidth(screenW);

		List<TitleEntry> titles = descriptor.getTitles();
		List<LayoutResult.PositionedButton> positioned = new ArrayList<>();

		// Calculate available width per column
		int totalMarginX = marginLeft + marginRight;
		int totalSpacingX = (columns - 1) * spacingX;
		int columnWidth = (screenW - totalMarginX - totalSpacingX) / columns;

		// Distribute titles across columns
		int titlesPerColumn = (int) Math.ceil((double) titles.size() / columns);

		// ── Pass 1: render each button at its natural size to discover dimensions ──
		List<TextStyle> resolvedStyles = new ArrayList<>();
		List<ButtonImages> rendered = new ArrayList<>();
		for (TitleEntry title : titles) {
			TextStyle style = resolveItemStyle(title.getStyle(), globalStyle);
			resolvedStyles.add(style);
			ButtonImages images = TextRenderer.renderTextButton(title.getDisplayName(), style, maxButtonWidth);
			rendered.add(images);
		}

		// ── Uniform width: max natural width, capped at column width ──
		int uniformWidth = 0;
		for (ButtonImages bi : rendered) {
			uniformWidth = Math.max(uniformWidth, bi.width());
		}
		uniformWidth = Math.min(uniformWidth, columnWidth);

		// ── Row heights: use uniform height when vertical real estate permits ──
		int globalMaxHeight = 0;
		for (ButtonImages bi : rendered) {
			globalMaxHeight = Math.max(globalMaxHeight, bi.height());
		}
		int availableHeight = screenH - marginTop - marginBottom;
		boolean uniformHeight = titlesPerColumn * globalMaxHeight + (titlesPerColumn - 1) * spacingY <= availableHeight;

		int[] rowHeights = new int[titlesPerColumn];
		if (uniformHeight) {
			Arrays.fill(rowHeights, globalMaxHeight);
		} else {
			// Fall back to per-row max (current behaviour preserving existing code output)
			for (int i = 0; i < rendered.size(); i++) {
				int row = i % titlesPerColumn;
				rowHeights[row] = Math.max(rowHeights[row], rendered.get(i).height());
			}
		}

		int[] rowY = new int[titlesPerColumn];
		rowY[0] = marginTop;
		for (int r = 1; r < titlesPerColumn; r++) {
			rowY[r] = rowY[r - 1] + rowHeights[r - 1] + spacingY;
		}

		// ── Pass 2: re-render each button at the uniform (width, rowHeight) ──
		int centreOffsetX = Math.max(0, (columnWidth - uniformWidth) / 2);
		for (int i = 0; i < titles.size(); i++) {
			TitleEntry title = titles.get(i);
			TextStyle style = resolvedStyles.get(i);

			int col = i / titlesPerColumn;
			int row = i % titlesPerColumn;
			int targetHeight = rowHeights[row];

			ButtonImages images = TextRenderer.renderTextButton(title.getDisplayName(), style, uniformWidth,
					targetHeight);

			int colX = marginLeft + col * (columnWidth + spacingX);
			int x = colX + centreOffsetX;
			int y = rowY[row];

			LayoutResult.PositionedButton btn = new LayoutResult.PositionedButton();
			btn.setTitleIndex(i);
			btn.setTitleNumber(title.getTitleNumber());
			btn.setX(x);
			btn.setY(y);
			btn.setNormalImage(images.normal());
			btn.setSelectedImage(images.selected());
			btn.setActivatedImage(images.activated());
			btn.setWidth(uniformWidth);
			btn.setHeight(targetHeight);
			positioned.add(btn);
		}

		LayoutResult result = new LayoutResult();
		result.setButtons(positioned);
		result.setCompositeBackground(false);
		result.setBackgroundComposition(null);

		log.info("TextListLayout: {} titles arranged in {} column(s), button size {}×{} (uniform height: {})",
				titles.size(), columns, uniformWidth, globalMaxHeight, uniformHeight);
		return result;
	}

	private TextStyle resolveGlobalStyle(TextStyle descriptorStyle) {
		TextStyle base = new TextStyle();
		if (descriptorStyle != null) {
			base = descriptorStyle.mergeOver(base);
		}
		return base.withDefaults();
	}

	private TextStyle resolveItemStyle(TextStyle itemStyle, TextStyle resolvedGlobal) {
		if (itemStyle == null)
			return resolvedGlobal;
		return itemStyle.mergeOver(resolvedGlobal).withDefaults();
	}

}
