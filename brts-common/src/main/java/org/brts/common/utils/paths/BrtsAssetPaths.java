package org.brts.common.utils.paths;



import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.utils.BrtsFileConfig;

/**
 * Locates the installed BRTS asset roots. When {@code brts.assets.dir} is set, it is the only root; otherwise
 * {@code ~/.brts/assets} then {@code /usr/share/brts/assets} are searched in order.
 */
public final class BrtsAssetPaths {

	public static final String ASSETS_DIR_KEY = "brts.assets.dir";

	public static final String STYLES_DIR = "styles";

	private static final Path USER_ASSETS_DIR = Path.of(System.getProperty("user.home"), ".brts", "assets");

	private static final Path SYSTEM_ASSETS_DIR = Path.of("/usr/share/brts/assets");

	private BrtsAssetPaths() {
	}

	public static List<Path> assetRoots() {
		String configured = BrtsFileConfig.getInstance().propertyOrDefault(ASSETS_DIR_KEY, null);
		if (configured != null) {
			return List.of(Path.of(configured.trim()).toAbsolutePath().normalize());
		}
		return List.of(USER_ASSETS_DIR, SYSTEM_ASSETS_DIR);
	}

	/**
	 * Returns the first {@code <root>/styles/<name>} directory containing {@code markerFile}.
	 *
	 * @throws IllegalArgumentException if the name is invalid or no root contains the template
	 */
	public static Path resolveStyleTemplateDir(String name, String markerFile, List<Path> roots) {
		if (name == null || name.isBlank() || !name.matches("[A-Za-z0-9._-]+") || name.startsWith(".")) {
			throw new IllegalArgumentException("Invalid installed style template name '" + name
					+ "': only letters, digits, '.', '_' and '-' are allowed");
		}
		for (Path root : roots) {
			Path candidate = root.resolve(STYLES_DIR).resolve(name);
			if (Files.isRegularFile(candidate.resolve(markerFile))) {
				return candidate.toAbsolutePath().normalize();
			}
		}
		throw new IllegalArgumentException("Installed style template '" + name + "' not found (looked for " + STYLES_DIR
				+ "/" + name + "/" + markerFile + " under " + roots + "); set " + ASSETS_DIR_KEY
				+ " in brts.conf to change the asset root");
	}

}
