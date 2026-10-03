package org.brts.highlevel.style;



import java.nio.file.Path;

import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.TitleMenuConfig;

/**
 * A loaded style template: its folder and its manifest, with every asset path already made absolute.
 */
public record StyleTemplate(Path directory, StyleTemplateManifest manifest) {

	public boolean hasTitleMenuBackground() {
		return manifest.getTitleMenu() != null && manifest.getTitleMenu().getBackgroundSource() != null;
	}

	/** Applies the disc-wide style and popup menu presentation. */
	public void applyTo(DiscDescriptor disc) {
		disc.setStyle(manifest.getStyle());
		disc.setPopupMenu(manifest.getPopupMenu());
	}

	/** Applies title menu style settings; the template background is used only when none is set yet. */
	public void applyTo(TitleMenuConfig menuConfig) {
		StyleTemplateManifest.TitleMenuStyle titleMenu = manifest.getTitleMenu();
		if (titleMenu == null) {
			return;
		}
		menuConfig.setStyle(titleMenu.getStyle());
		if (titleMenu.getLayoutType() != null) {
			menuConfig.setLayoutType(titleMenu.getLayoutType());
		}
		if (titleMenu.getBoundingBox() != null) {
			menuConfig.setBoundingBox(titleMenu.getBoundingBox());
		}
		if (menuConfig.getBackgroundSource() == null && titleMenu.getBackgroundSource() != null) {
			menuConfig.setBackgroundSource(titleMenu.getBackgroundSource());
			menuConfig.setBaseDir(directory.toString());
		}
	}

}
