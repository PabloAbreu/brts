package org.brts.lowlevel.popupmenu;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.brts.common.utils.composition.CompositionEngine;
import org.brts.common.utils.composition.CompositionEngineFactory;
import org.brts.common.utils.composition.ImageFrame;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGenerator;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGeneratorFactory;
import org.brts.lowlevel.igs.IgsMenuAssembler;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.BackgroundLayer;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.BackgroundLayout;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.PageBackgrounds;

final class PopupMenuBackgroundRenderer {

	enum PageRole {
		ROOT, AUDIO, SUBTITLES
	}

	List<IgsMenuAssembler.PositionedDecoration> render(PageBackgrounds backgrounds, PageRole role, boolean includesRoot,
			List<IgsMenuAssembler.PositionedButton> buttons, int screenWidth, int screenHeight) {
		if (backgrounds == null) {
			return List.of();
		}

		List<BackgroundLayer> layers = new ArrayList<>();
		addAll(layers, backgrounds.getShared());
		if (includesRoot) {
			addAll(layers, backgrounds.getRoot());
		}
		switch (role) {
		case AUDIO -> addAll(layers, backgrounds.getAudio());
		case SUBTITLES -> addAll(layers, backgrounds.getSubtitles());
		case ROOT -> {
			// Root-specific layers were included above.
		}
		}

		if (layers.size() + buttons.size() > 256) {
			throw new IllegalArgumentException("Popup page " + role + " exceeds the IGS limit of 256 BOGs");
		}

		Rectangle selectableBounds = selectableBounds(buttons);
		List<IgsMenuAssembler.PositionedDecoration> decorations = new ArrayList<>();
		for (int index = 0; index < layers.size(); index++) {
			BackgroundLayer layer = layers.get(index);
			Rectangle destination = resolveDestination(layer, selectableBounds, screenWidth, screenHeight, role, index);
			decorations.add(new IgsMenuAssembler.PositionedDecoration(
					renderImage(layer.getSource(), destination.width, destination.height, role, index), destination.x,
					destination.y));
		}
		return decorations;
	}

	private static void addAll(List<BackgroundLayer> target, List<BackgroundLayer> source) {
		if (source != null) {
			target.addAll(source);
		}
	}

	private static Rectangle selectableBounds(List<IgsMenuAssembler.PositionedButton> buttons) {
		if (buttons.isEmpty()) {
			throw new IllegalArgumentException("Cannot fit a popup background around an empty selectable page");
		}
		int left = buttons.stream().mapToInt(IgsMenuAssembler.PositionedButton::x).min().orElseThrow();
		int top = buttons.stream().mapToInt(IgsMenuAssembler.PositionedButton::y).min().orElseThrow();
		int right = buttons.stream().mapToInt(button -> button.x() + button.images().normal().getWidth()).max()
				.orElseThrow();
		int bottom = buttons.stream().mapToInt(button -> button.y() + button.images().normal().getHeight()).max()
				.orElseThrow();
		return new Rectangle(left, top, right - left, bottom - top);
	}

	private static Rectangle resolveDestination(BackgroundLayer layer, Rectangle selectableBounds, int screenWidth,
			int screenHeight, PageRole role, int index) {
		if (layer == null || layer.getLayout() == null) {
			throw invalid(role, index, "layout is required");
		}
		BackgroundLayout layout = layer.getLayout();
		if (layout.getMode() == null) {
			throw invalid(role, index, "layout mode is required");
		}
		Rectangle result;
		switch (layout.getMode()) {
		case ABSOLUTE -> result = new Rectangle(layout.getX(), layout.getY(),
				requiredPositive(layout.getWidth(), role, index, "width"),
				requiredPositive(layout.getHeight(), role, index, "height"));
		case SELECTABLE_BOUNDS -> {
			validateNonNegative(layout.getMarginTop(), role, index, "marginTop");
			validateNonNegative(layout.getMarginRight(), role, index, "marginRight");
			validateNonNegative(layout.getMarginBottom(), role, index, "marginBottom");
			validateNonNegative(layout.getMarginLeft(), role, index, "marginLeft");
			result = new Rectangle(selectableBounds.x - layout.getMarginLeft(),
					selectableBounds.y - layout.getMarginTop(),
					selectableBounds.width + layout.getMarginLeft() + layout.getMarginRight(),
					selectableBounds.height + layout.getMarginTop() + layout.getMarginBottom());
		}
		case FULL_WIDTH_BOTTOM -> {
			validateNonNegative(layout.getEdgeOffset(), role, index, "edgeOffset");
			validateNonNegative(layout.getMarginTop(), role, index, "marginTop");
			validateNonNegative(layout.getMarginRight(), role, index, "marginRight");
			validateNonNegative(layout.getMarginBottom(), role, index, "marginBottom");
			validateNonNegative(layout.getMarginLeft(), role, index, "marginLeft");
			int height = requiredPositive(layout.getHeight(), role, index, "height");
			int outerHeight = height + layout.getMarginTop() + layout.getMarginBottom();
			result = new Rectangle(layout.getMarginLeft(), screenHeight - layout.getEdgeOffset() - outerHeight,
					screenWidth - layout.getMarginLeft() - layout.getMarginRight(), outerHeight);
		}
		case FULL_HEIGHT_LEFT -> {
			validateNonNegative(layout.getEdgeOffset(), role, index, "edgeOffset");
			validateNonNegative(layout.getMarginTop(), role, index, "marginTop");
			validateNonNegative(layout.getMarginRight(), role, index, "marginRight");
			validateNonNegative(layout.getMarginBottom(), role, index, "marginBottom");
			validateNonNegative(layout.getMarginLeft(), role, index, "marginLeft");
			int width = requiredPositive(layout.getWidth(), role, index, "width");
			int outerWidth = width + layout.getMarginLeft() + layout.getMarginRight();
			result = new Rectangle(layout.getEdgeOffset(), layout.getMarginTop(), outerWidth,
					screenHeight - layout.getMarginTop() - layout.getMarginBottom());
		}
		default -> throw invalid(role, index, "unsupported layout mode " + layout.getMode());
		}
		if (result.x < 0 || result.y < 0 || result.width <= 0 || result.height <= 0
				|| (long) result.x + result.width > screenWidth || (long) result.y + result.height > screenHeight) {
			throw invalid(role, index, "destination " + result.x + "," + result.y + " " + result.width + "x"
					+ result.height + " is outside the " + screenWidth + "x" + screenHeight + " screen");
		}
		return result;
	}

	private static int requiredPositive(Integer value, PageRole role, int index, String field) {
		if (value == null || value <= 0) {
			throw invalid(role, index, field + " must be greater than zero");
		}
		return value;
	}

	private static void validateNonNegative(int value, PageRole role, int index, String field) {
		if (value < 0) {
			throw invalid(role, index, field + " must not be negative");
		}
	}

	private static BufferedImage renderImage(ImageReference source, int width, int height, PageRole role, int index) {
		if (source == null) {
			throw invalid(role, index, "source is required");
		}
		boolean raster = source.isStatic();
		boolean synthetic = source.isSynthetic();
		if (source.isVideo()) {
			throw invalid(role, index, "video sources are not supported");
		}
		if (raster == synthetic) {
			throw invalid(role, index, "source must define exactly one of sourcePath or syntheticImage");
		}

		CompositionEngine engine = CompositionEngineFactory.get();
		if (raster) {
			try (ImageFrame sourceFrame = engine.load(Path.of(source.getSourcePath()));
					ImageFrame resized = engine.resize(sourceFrame, width, height)) {
				return resized.toBufferedImage();
			} catch (RuntimeException e) {
				throw new IllegalArgumentException(
						"Failed to render popup background " + role + "[" + index + "] from " + source.getSourcePath(),
						e);
			}
		}

		ImageReference.SyntheticImageSource syntheticSource = source.getSyntheticImage();
		if (syntheticSource.getFrameRate() != null && syntheticSource.getFrameRate() > 0) {
			throw invalid(role, index, "animated synthetic sources are not supported");
		}
		try (SyntheticImageGenerator generator = SyntheticImageGeneratorFactory.create(syntheticSource)) {
			ImageFrame generated = generator.generate(0, Map.of("width", width, "height", height));
			try (ImageFrame resized = engine.resize(generated, width, height)) {
				return resized.toBufferedImage();
			}
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("Failed to render popup background " + role + "[" + index + "]", e);
		}
	}

	private static IllegalArgumentException invalid(PageRole role, int index, String message) {
		return new IllegalArgumentException("Invalid popup background " + role + "[" + index + "]: " + message);
	}
}