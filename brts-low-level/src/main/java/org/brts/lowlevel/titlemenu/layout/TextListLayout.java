package org.brts.lowlevel.titlemenu.layout;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/titlemenu/layout/TextListLayout.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brts.common.menu.TextRenderer;
import org.brts.common.menu.TextRenderer.ButtonImages;
import org.brts.common.menu.TextStyle;
import org.brts.common.utils.BrtsI18NLabels;
import org.brts.lowlevel.bdmv.NavigationCommandUtils;
import org.brts.lowlevel.titlemenu.descriptor.BoundingBox;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import lombok.extern.slf4j.Slf4j;

/**
 * Simple text-list layout: renders each title as a selectable text button and arranges them vertically (or in columns).
 * <p>
 * Buttons are centred horizontally within each column. Vertical spacing is automatic based on button height and
 * configured spacing.
 */
@Slf4j
public class TextListLayout implements TitleMenuLayout {
	private static final int SETTINGS_ENTRY_PAGE_ID = 2;
	private static final int SETTINGS_BUTTON_MAX_WIDTH = 800;
	private static final int SETTINGS_BUTTON_HEIGHT = 40;
	private static final int SETTINGS_BUTTON_SPACING_Y = 8;
	private static final int SETTINGS_MARGIN_BOTTOM = 80;

	@Override
	public LayoutResult layout(TitleMenuDescriptor descriptor, Path baseDir) throws IOException {
		LayoutConfig config = descriptor.getLayout();
		int screenW = descriptor.getScreenWidth();
		int screenH = descriptor.getScreenHeight();
		int columns = config.effectiveColumns();

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
			int marginBottom = config.effectiveMarginBottom();
			int marginLeft = config.effectiveMarginLeft();
			int marginRight = config.effectiveMarginRight();
			originX = marginLeft;
			originY = marginTop;
			availableWidth = screenW - marginLeft - marginRight;
			availableHeight = screenH - marginTop - marginBottom;
		}
		int spacingX = config.effectiveSpacingX();
		int spacingY = config.effectiveSpacingY();

		TextStyle globalStyle = resolveGlobalStyle(config.getTitleStyle());
		int maxButtonWidth = config.effectiveMaxButtonWidth(availableWidth);

		List<TitleEntry> titles = descriptor.getTitles();
		List<LayoutResult.PositionedButton> positioned = new ArrayList<>();

		// Calculate available width per column
		int totalSpacingX = (columns - 1) * spacingX;
		int columnWidth = (availableWidth - totalSpacingX) / columns;

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
		rowY[0] = originY;
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

			int colX = originX + col * (columnWidth + spacingX);
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
		layoutSettingsMenus(descriptor, result);

		log.info("TextListLayout: {} titles arranged in {} column(s), button size {}×{} (uniform height: {})",
				titles.size(), columns, uniformWidth, globalMaxHeight, uniformHeight);
		return result;
	}

	static void layoutSettingsMenus(TitleMenuDescriptor descriptor, LayoutResult result) {
		boolean hasAudioItems = descriptor.getAudioItems() != null && !descriptor.getAudioItems().isEmpty();
		boolean hasSubtitleItems = descriptor.getSubtitleItems() != null && !descriptor.getSubtitleItems().isEmpty();
		if (!hasAudioItems && !hasSubtitleItems) {
			return;
		}

		TextStyle style = resolveGlobalStyle(descriptor.getLayout().getTitleStyle());
		LayoutResult.PositionedButton settingsButton = renderSettingsButton(
				BrtsI18NLabels.getLabel(BrtsI18NLabels.MENU_SETTINGS), style, descriptor.getScreenWidth() - 40,
				descriptor.getScreenHeight() - SETTINGS_MARGIN_BOTTOM,
				NavigationCommandUtils.setButtonPage(SETTINGS_ENTRY_PAGE_ID, 1));
		settingsButton.setX(settingsButton.getX() - settingsButton.getWidth());
		settingsButton.setY(settingsButton.getY() - settingsButton.getHeight());
		result.setSettingsButton(settingsButton);

		List<List<LayoutResult.PositionedButton>> pages = new ArrayList<>();
		if (hasAudioItems && hasSubtitleItems) {
			pages.add(List.of(
					renderSettingsButton(BrtsI18NLabels.getLabel(BrtsI18NLabels.MENU_TO_AUDIO), style, 0, 0,
							NavigationCommandUtils.setButtonPage(3, 1)),
					renderSettingsButton(BrtsI18NLabels.getLabel(BrtsI18NLabels.MENU_TO_SUBTITLES), style, 0, 0,
							NavigationCommandUtils.setButtonPage(4, 1)),
					renderSettingsButton(BrtsI18NLabels.getLabel(BrtsI18NLabels.MENU_BACK), style, 0, 0,
							NavigationCommandUtils.setButtonPage(1, result.getButtons().size() + 1))));
			pages.add(audioButtons(descriptor, style, NavigationCommandUtils.setButtonPage(2, 1)));
			pages.add(subtitleButtons(descriptor, style, NavigationCommandUtils.setButtonPage(2, 1)));
		} else if (hasAudioItems) {
			pages.add(audioButtons(descriptor, style,
					NavigationCommandUtils.setButtonPage(1, result.getButtons().size() + 1)));
		} else {
			pages.add(subtitleButtons(descriptor, style,
					NavigationCommandUtils.setButtonPage(1, result.getButtons().size() + 1)));
		}

		for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
			List<LayoutResult.PositionedButton> buttons = pages.get(pageIndex);
			positionSettingsButtons(buttons, descriptor.getScreenWidth(), descriptor.getScreenHeight());
			LayoutResult.SettingsPage page = new LayoutResult.SettingsPage();
			page.setPageId(SETTINGS_ENTRY_PAGE_ID + pageIndex);
			page.setButtons(buttons);
			result.getSettingsPages().add(page);
		}
	}

	private static List<LayoutResult.PositionedButton> audioButtons(TitleMenuDescriptor descriptor, TextStyle style,
			List<org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand> backCommands) {
		List<LayoutResult.PositionedButton> buttons = new ArrayList<>();
		descriptor.getAudioItems().forEach(item -> {
			TextStyle itemStyle = resolveItemStyle(item.getStyle(), style);
			buttons.add(renderSettingsButton(item.getDescription(), itemStyle, 0, 0,
					NavigationCommandUtils.setAudioChoice(item.getStreamNumber())));
		});
		buttons.add(renderSettingsButton(BrtsI18NLabels.getLabel(BrtsI18NLabels.MENU_BACK), style, 0, 0, backCommands));
		return buttons;
	}

	private static List<LayoutResult.PositionedButton> subtitleButtons(TitleMenuDescriptor descriptor, TextStyle style,
			List<org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand> backCommands) {
		List<LayoutResult.PositionedButton> buttons = new ArrayList<>();
		descriptor.getSubtitleItems().forEach(item -> {
			TextStyle itemStyle = resolveItemStyle(item.getStyle(), style);
			buttons.add(renderSettingsButton(item.getDescription(), itemStyle, 0, 0,
					NavigationCommandUtils.setSubtitleChoice(item.getStreamNumber())));
		});
		buttons.add(renderSettingsButton(BrtsI18NLabels.getLabel(BrtsI18NLabels.MENU_BACK), style, 0, 0, backCommands));
		return buttons;
	}

	private static LayoutResult.PositionedButton renderSettingsButton(String text, TextStyle style, int x, int y,
			List<org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand> commands) {
		ButtonImages images = TextRenderer.renderTextButton(text, style, SETTINGS_BUTTON_MAX_WIDTH);
		LayoutResult.PositionedButton button = new LayoutResult.PositionedButton();
		button.setX(x);
		button.setY(y);
		button.setNormalImage(images.normal());
		button.setSelectedImage(images.selected());
		button.setActivatedImage(images.activated());
		button.setWidth(images.width());
		button.setHeight(images.height());
		button.setNavigationCommands(commands);
		return button;
	}

	private static void positionSettingsButtons(List<LayoutResult.PositionedButton> buttons, int screenWidth,
			int screenHeight) {
		int totalHeight = buttons.size() * SETTINGS_BUTTON_HEIGHT + (buttons.size() - 1) * SETTINGS_BUTTON_SPACING_Y;
		int y = screenHeight - SETTINGS_MARGIN_BOTTOM - totalHeight;
		int x = Math.max(40,
				Math.min((screenWidth - SETTINGS_BUTTON_MAX_WIDTH) / 2, screenWidth - SETTINGS_BUTTON_MAX_WIDTH - 40));
		for (LayoutResult.PositionedButton button : buttons) {
			button.setX(x);
			button.setY(y);
			y += SETTINGS_BUTTON_HEIGHT + SETTINGS_BUTTON_SPACING_Y;
		}
	}

	private static TextStyle resolveGlobalStyle(TextStyle descriptorStyle) {
		TextStyle base = new TextStyle();
		if (descriptorStyle != null) {
			base = descriptorStyle.mergeOver(base);
		}
		return base.withDefaults();
	}

	private static TextStyle resolveItemStyle(TextStyle itemStyle, TextStyle resolvedGlobal) {
		if (itemStyle == null)
			return resolvedGlobal;
		return itemStyle.mergeOver(resolvedGlobal).withDefaults();
	}

}
