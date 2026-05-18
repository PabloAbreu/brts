package org.brts.lowlevel.titlemenu.layout;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
		int columns = config.effectiveColumns();
		int marginTop = config.effectiveMarginTop();
		int marginLeft = config.effectiveMarginLeft();
		int marginRight = config.effectiveMarginRight();
		int spacingX = config.effectiveSpacingX();
		int spacingY = config.effectiveSpacingY();

		TextStyle globalStyle = resolveGlobalStyle(config.getTitleStyle());
		int maxButtonWidth = config.effectiveMaxButtonWidth(screenW);

		List<TitleEntry> titles = descriptor.getTitles();
		List<LayoutResult.PositionedButton> positioned = new ArrayList<>();

		// Pre-render all buttons to determine uniform sizing
		List<ButtonImages> rendered = new ArrayList<>();
		for (TitleEntry title : titles) {
			TextStyle style = resolveItemStyle(title.getStyle(), globalStyle);
			ButtonImages images = TextRenderer.renderTextButton(title.getDisplayName(), style, maxButtonWidth);
			rendered.add(images);
		}

		// Calculate available width per column
		int totalMarginX = marginLeft + marginRight;
		int totalSpacingX = (columns - 1) * spacingX;
		int columnWidth = (screenW - totalMarginX - totalSpacingX) / columns;

		// Distribute titles across columns
		int titlesPerColumn = (int) Math.ceil((double) titles.size() / columns);

		for (int i = 0; i < titles.size(); i++) {
			TitleEntry title = titles.get(i);
			ButtonImages images = rendered.get(i);

			int col = i / titlesPerColumn;
			int row = i % titlesPerColumn;

			// Column X origin
			int colX = marginLeft + col * (columnWidth + spacingX);

			// Centre button within column
			int x = colX + Math.max(0, (columnWidth - images.width()) / 2);
			int y = marginTop + row * (images.height() + spacingY);

			LayoutResult.PositionedButton btn = new LayoutResult.PositionedButton();
			btn.setTitleIndex(i);
			btn.setTitleNumber(title.getTitleNumber());
			btn.setX(x);
			btn.setY(y);
			btn.setNormalImage(images.normal());
			btn.setSelectedImage(images.selected());
			btn.setActivatedImage(images.activated());
			btn.setWidth(images.width());
			btn.setHeight(images.height());
			positioned.add(btn);
		}

		LayoutResult result = new LayoutResult();
		result.setButtons(positioned);
		result.setCompositeBackground(false);
		result.setBackgroundComposition(null);

		log.info("TextListLayout: {} titles arranged in {} column(s)", titles.size(), columns);
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
