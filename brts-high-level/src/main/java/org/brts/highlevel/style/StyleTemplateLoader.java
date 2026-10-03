package org.brts.highlevel.style;



import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.ImagesComposition;
import org.brts.common.utils.paths.BrtsAssetPaths;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.BackgroundLayer;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads a style template from a reference: {@code brts:<name>} designates an installed template (see
 * {@link BrtsAssetPaths}), anything else is a filesystem path to the template folder.
 */
@Slf4j
public class StyleTemplateLoader {

	public static final String INSTALLED_PREFIX = "brts:";

	public static final String MANIFEST_FILE = "style-template.json";

	private final Supplier<List<Path>> assetRoots;

	public StyleTemplateLoader() {
		this(BrtsAssetPaths::assetRoots);
	}

	public StyleTemplateLoader(Supplier<List<Path>> assetRoots) {
		this.assetRoots = assetRoots;
	}

	public StyleTemplate load(String ref) throws IOException {
		Path directory = resolveDirectory(ref);
		Path manifestPath = directory.resolve(MANIFEST_FILE);
		if (!Files.isRegularFile(manifestPath)) {
			throw new IllegalArgumentException(
					"Style template '" + ref + "' has no " + MANIFEST_FILE + " (expected " + manifestPath + ")");
		}
		StyleTemplateManifest manifest = JsonMapperFactory.get().readValue(manifestPath.toFile(),
				StyleTemplateManifest.class);
		absolutize(manifest, directory, manifestPath);
		log.info("Loaded style template '{}' from {}", manifest.getName() != null ? manifest.getName() : ref,
				directory);
		return new StyleTemplate(directory, manifest);
	}

	Path resolveDirectory(String ref) {
		if (ref == null || ref.isBlank()) {
			throw new IllegalArgumentException("Style template reference must not be blank");
		}
		if (ref.startsWith(INSTALLED_PREFIX)) {
			return BrtsAssetPaths.resolveStyleTemplateDir(ref.substring(INSTALLED_PREFIX.length()), MANIFEST_FILE,
					assetRoots.get());
		}
		Path directory = Path.of(ref).toAbsolutePath().normalize();
		if (!Files.isDirectory(directory)) {
			throw new IllegalArgumentException("Style template folder not found: " + directory);
		}
		return directory;
	}

	private static void absolutize(StyleTemplateManifest manifest, Path directory, Path manifestPath) {
		PathFixer fixer = new PathFixer(directory, manifestPath);
		if (manifest.getTitleMenu() != null && manifest.getTitleMenu().getBackgroundSource() != null) {
			BackgroundSource bg = manifest.getTitleMenu().getBackgroundSource();
			fixer.fix(bg.getVideoPath(), bg::setVideoPath);
			fixer.fix(bg.getImagePath(), bg::setImagePath);
			fixer.fix(bg.getAudioPath(), bg::setAudioPath);
			ImagesComposition composition = bg.getComposition();
			if (composition != null) {
				fixer.fixAll(composition.getImages());
				fixer.fixAll(composition.getTransparencyMasks());
			}
		}
		PopupMenuConfig popup = manifest.getPopupMenu();
		if (popup != null && popup.getBackgrounds() != null) {
			PopupMenuConfig.PageBackgrounds backgrounds = popup.getBackgrounds();
			for (List<BackgroundLayer> layers : List.of(backgrounds.getShared(), backgrounds.getRoot(),
					backgrounds.getAudio(), backgrounds.getSubtitles())) {
				if (layers != null) {
					fixer.fixAll(layers.stream().map(BackgroundLayer::getSource).toList());
				}
			}
		}
	}

	private record PathFixer(Path directory, Path manifestPath) {

		void fix(String path, Consumer<String> setter) {
			if (path == null || path.isBlank()) {
				return;
			}
			Path resolved = directory.resolve(path).normalize();
			if (!Files.exists(resolved)) {
				throw new IllegalArgumentException(
						"Asset '" + path + "' referenced by " + manifestPath + " not found: " + resolved);
			}
			setter.accept(resolved.toString());
		}

		void fixAll(List<ImageReference> refs) {
			if (refs == null) {
				return;
			}
			for (ImageReference ref : refs) {
				if (ref == null) {
					continue;
				}
				fix(ref.getSourcePath(), ref::setSourcePath);
				fix(ref.getVideoPath(), ref::setVideoPath);
				if (ref.getSyntheticImage() != null) {
					fix(ref.getSyntheticImage().getSrcPath(), ref.getSyntheticImage()::setSrcPath);
				}
			}
		}

	}

}
