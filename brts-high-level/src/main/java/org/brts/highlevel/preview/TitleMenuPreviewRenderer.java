package org.brts.highlevel.preview;



import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.brts.common.utils.composition.CompositionBuffer;
import org.brts.common.utils.composition.CompositionContextImpl;
import org.brts.common.utils.composition.ImageFrame;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.ImagesComposition;
import org.brts.common.utils.composition.MediaRepositoryImpl;
import org.brts.common.utils.composition.sources.video.VideoFrames;
import org.brts.common.utils.composition.sources.video.VideoFramesFactory;
import org.brts.highlevel.descriptor.HighLevelDiscDescriptor;
import org.brts.highlevel.descriptor.MovieDiscDescriptor;
import org.brts.highlevel.descriptor.TvSeriesDiscDescriptor;
import org.brts.highlevel.style.StyleTemplate;
import org.brts.highlevel.style.StyleTemplateLoader;
import org.brts.highlevel.template.MovieDiscTemplate;
import org.brts.highlevel.template.TvSeriesDiscTemplate;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.lowlevel.titlemenu.descriptor.TitleEntry;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem;
import org.brts.lowlevel.titlemenu.layout.LayoutResult;
import org.brts.lowlevel.titlemenu.layout.TextListLayout;
import org.brts.lowlevel.titlemenu.layout.ThumbnailGridLayout;
import org.brts.lowlevel.titlemenu.layout.TitleMenuLayout;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.TitleDescriptor;
import org.brts.middle.descriptor.TitleMenuConfig;

/** Renders one visual preview frame of the title menu that a high-level descriptor and style template describe. */
public class TitleMenuPreviewRenderer {

	public enum ButtonState {
		NORMAL, SELECTED, ACTIVATED;

		public static ButtonState parse(String value) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (RuntimeException e) {
				throw new IllegalArgumentException(
						"Unknown button state '" + value + "' (expected normal, selected, or activated)", e);
			}
		}
	}

	private final StyleTemplateLoader styleTemplateLoader;

	public TitleMenuPreviewRenderer() {
		this(new StyleTemplateLoader());
	}

	public TitleMenuPreviewRenderer(StyleTemplateLoader styleTemplateLoader) {
		this.styleTemplateLoader = styleTemplateLoader;
	}

	/**
	 * Renders a preview frame from the high-level descriptor. Relative media paths are resolved against
	 * {@code baseDir}. The title number receiving the requested state is a Blu-ray title number (normally starting at
	 * 1).
	 */
	public BufferedImage render(HighLevelDiscDescriptor descriptor, Path baseDir, double timeSeconds,
			int selectedTitleNumber, ButtonState selectedState) throws IOException {
		if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
			throw new IllegalArgumentException("Preview time must be a finite non-negative number of seconds");
		}
		if (selectedTitleNumber < 1) {
			throw new IllegalArgumentException("Selected title number must be greater than zero");
		}
		if (selectedState == null) {
			throw new IllegalArgumentException("Button state must not be null");
		}
		if (descriptor == null) {
			throw new IllegalArgumentException("High-level disc descriptor must not be null");
		}
		Path resolvedBaseDir = (baseDir == null ? Path.of(".") : baseDir).toAbsolutePath().normalize();
		StyleTemplate style = descriptor.getStyleTemplate() == null ? null
				: styleTemplateLoader.load(descriptor.getStyleTemplate());
		DiscDescriptor disc = expand(descriptor, style);
		TitleMenuConfig menuConfig = resolveMenuConfig(descriptor, disc, style);
		if (menuConfig == null || menuConfig.getBackgroundSource() == null) {
			throw new IllegalArgumentException(
					"No title-menu background is available. Configure a title-menu background "
							+ "in the style template or provide a TV-series menuBackgroundVideoPath.");
		}
		if (disc.getTitles() == null || disc.getTitles().isEmpty()) {
			throw new IllegalArgumentException("The high-level descriptor must produce at least one title to preview");
		}

		TitleMenuDescriptor menu = toTitleMenuDescriptor(disc, menuConfig);
		boolean selectedTitleExists = menu.getTitles().stream()
				.anyMatch(title -> title.getTitleNumber() == selectedTitleNumber);
		if (!selectedTitleExists) {
			throw new IllegalArgumentException(
					"Selected title number " + selectedTitleNumber + " is not present in the high-level descriptor");
		}

		ImagesComposition background = normalizeBackground(menu.getBackgroundMedia(), menu, resolvedBaseDir);

		TitleMenuLayout layout = menu.getLayout().effectiveType() == LayoutType.THUMBNAIL_GRID
				? new ThumbnailGridLayout()
				: new TextListLayout();
		LayoutResult result = layout.layout(menu, resolvedBaseDir);
		if (result.isCompositeBackground() && result.getBackgroundComposition() != null) {
			background = result.getBackgroundComposition();
		}
		if (background == null) {
			background = normalizeBackground(menu.getBackgroundMedia(), menu, resolvedBaseDir);
		}
		background.setCanvasWidth(menu.getScreenWidth());
		background.setCanvasHeight(menu.getScreenHeight());

		int frameNumber = frameNumber(background, resolvedBaseDir, timeSeconds);
		try (MediaRepositoryImpl repository = new MediaRepositoryImpl();
				ImageFrame composed = new CompositionBuffer(background, repository,
						new CompositionContextImpl(frameNumber, background, resolvedBaseDir)).compose()) {
			BufferedImage preview = new BufferedImage(menu.getScreenWidth(), menu.getScreenHeight(),
					BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics = preview.createGraphics();
			try {
				graphics.drawImage(composed.toBufferedImage(), 0, 0, null);
				for (LayoutResult.PositionedButton button : result.getButtons()) {
					BufferedImage buttonImage = button.getNormalImage();
					if (button.getTitleNumber() == selectedTitleNumber) {
						buttonImage = switch (selectedState) {
						case NORMAL -> button.getNormalImage();
						case SELECTED -> button.getSelectedImage();
						case ACTIVATED -> button.getActivatedImage();
						};
					}
					graphics.drawImage(buttonImage, button.getX(), button.getY(), null);
				}
				if (result.getSettingsButton() != null) {
					LayoutResult.PositionedButton button = result.getSettingsButton();
					graphics.drawImage(button.getNormalImage(), button.getX(), button.getY(), null);
				}
			} finally {
				graphics.dispose();
			}
			return preview;
		}
	}

	private DiscDescriptor expand(HighLevelDiscDescriptor descriptor, StyleTemplate style) throws IOException {
		if (descriptor instanceof MovieDiscDescriptor movie) {
			return new MovieDiscTemplate().expand(movie, style);
		}
		if (descriptor instanceof TvSeriesDiscDescriptor tvSeries) {
			return new TvSeriesDiscTemplate().expand(tvSeries, style);
		}
		throw new IllegalArgumentException("Unsupported high-level template type: " + descriptor.getTemplateType());
	}

	private TitleMenuConfig resolveMenuConfig(HighLevelDiscDescriptor descriptor, DiscDescriptor disc,
			StyleTemplate style) {
		TitleMenuConfig menu = disc.getTitleMenuConfig();
		if (menu == null) {
			menu = new TitleMenuConfig();
			if (style != null) {
				style.applyTo(menu);
			}
		}
		if (descriptor instanceof TvSeriesDiscDescriptor tv && tv.getMenuBackgroundVideoPath() != null
				&& !tv.getMenuBackgroundVideoPath().isBlank()) {
			BackgroundSource source = new BackgroundSource();
			source.setVideoPath(tv.getMenuBackgroundVideoPath());
			menu.setBackgroundSource(source);
		}
		if (menu.getStyle() != null && disc.getStyle() != null) {
			menu.setStyle(menu.getStyle().mergeOver(disc.getStyle()));
		} else if (menu.getStyle() == null) {
			menu.setStyle(disc.getStyle());
		}
		return menu;
	}

	private TitleMenuDescriptor toTitleMenuDescriptor(DiscDescriptor disc, TitleMenuConfig menuConfig) {
		TitleMenuDescriptor menu = new TitleMenuDescriptor();
		menu.setBackgroundMedia(menuConfig.getBackgroundSource());
		LayoutConfig layout = new LayoutConfig();
		layout.setType(menuConfig.getLayoutType());
		layout.setBoundingBox(menuConfig.getBoundingBox());
		layout.setTitleStyle(menuConfig.getStyle());
		menu.setLayout(layout);
		List<TitleEntry> entries = new ArrayList<>();
		for (TitleDescriptor title : disc.getTitles()) {
			TitleEntry entry = new TitleEntry();
			entry.setTitleNumber(title.getTitleId());
			entry.setDisplayName(resolveDisplayName(title));
			entry.setSourceMediaPath(title.getSourceMkv());
			entries.add(entry);
		}
		menu.setTitles(entries);
		menu.setAudioItems(toStreamMenuItems(menuConfig.getAudioItems()));
		menu.setSubtitleItems(toStreamMenuItems(menuConfig.getSubtitleItems()));
		return menu;
	}

	private static List<StreamMenuItem> toStreamMenuItems(
			List<? extends org.brts.middle.menu.descriptor.StreamMenuItem> source) {
		if (source == null) {
			return List.of();
		}
		return source.stream().map(item -> {
			StreamMenuItem result = new StreamMenuItem();
			result.setDescription(item.getDescription());
			result.setStreamNumber(item.getStreamNumber());
			result.setStyle(item.getStyle());
			return result;
		}).toList();
	}

	private static String resolveDisplayName(TitleDescriptor title) {
		if (title.getDisplayName() != null && !title.getDisplayName().isBlank()) {
			return title.getDisplayName();
		}
		String source = title.getSourceMkv();
		if (source == null || source.isBlank()) {
			return "Title " + title.getTitleId();
		}
		int lastSeparator = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
		String filename = source.substring(lastSeparator + 1);
		int dot = filename.lastIndexOf('.');
		return dot > 0 ? filename.substring(0, dot) : filename;
	}

	private static ImagesComposition normalizeBackground(BackgroundSource source, TitleMenuDescriptor menu,
			Path baseDir) {
		if (source == null) {
			throw new IllegalArgumentException("Title-menu background is missing");
		}
		if (source.isComposition()) {
			return source.getComposition();
		}
		String path = source.isVideo() ? source.getVideoPath() : source.getImagePath();
		if (path == null || path.isBlank()) {
			throw new IllegalArgumentException(
					"Title-menu background must specify videoPath, imagePath, or composition");
		}
		Path resolved = Path.of(path).isAbsolute() ? Path.of(path) : baseDir.resolve(path);
		ImageReference reference = new ImageReference();
		reference.setImageId("background");
		if (source.isVideo()) {
			reference.setVideoPath(resolved.normalize().toString());
		} else {
			reference.setSourcePath(resolved.normalize().toString());
		}
		ImagesComposition composition = new ImagesComposition();
		composition.setBaseImageId("background");
		composition.setCanvasWidth(menu.getScreenWidth());
		composition.setCanvasHeight(menu.getScreenHeight());
		composition.setImages(List.of(reference));
		composition.setCompositions(List.of());
		return composition;
	}

	private static int frameNumber(ImagesComposition composition, Path baseDir, double timeSeconds) throws IOException {
		if (timeSeconds == 0) {
			return 0;
		}
		ImageReference base = findBaseImage(composition);
		if (base != null && base.isVideo()) {
			Path videoPath = Path.of(base.getVideoPath());
			if (!videoPath.isAbsolute()) {
				videoPath = baseDir.resolve(videoPath);
			}
			try (VideoFrames frames = VideoFramesFactory.create(videoPath.normalize())) {
				return toFrameNumber(timeSeconds, frames.getFps());
			}
		}
		if (base != null && base.isSynthetic() && base.getSyntheticImage().getFrameRate() != null
				&& base.getSyntheticImage().getFrameRate() > 0) {
			return toFrameNumber(timeSeconds, base.getSyntheticImage().getFrameRate());
		}
		return toFrameNumber(timeSeconds, 24.0);
	}

	private static int toFrameNumber(double timeSeconds, double fps) {
		double frame = Math.floor(timeSeconds * (fps > 0 ? fps : 24.0));
		if (frame > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Preview time is too large to represent as a frame index");
		}
		return (int) frame;
	}

	private static ImageReference findBaseImage(ImagesComposition composition) {
		if (composition.getImages() == null || composition.getImages().isEmpty()) {
			return null;
		}
		String baseId = composition.getBaseImageId();
		return composition.getImages().stream().filter(image -> baseId == null || baseId.equals(image.getImageId()))
				.findFirst().orElse(composition.getImages().get(0));
	}
}
