package org.brts.highlevel.style;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-high-level/src/main/java/org/brts/highlevel/style/StyleTemplate.java' is part of BRTS.
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
