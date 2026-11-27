/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/menu/ButtonOverlayRenderer.java' is part of BRTS.
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
package org.brts.common.menu;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import javax.imageio.ImageIO;

import org.brts.common.utils.composition.ImageFrame;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGenerator;
import org.brts.common.utils.composition.sources.synth.SyntheticImageGeneratorFactory;

/** Renders optional button-sized overlays, including FreeMarker SVGs with actual label bounds. */
public final class ButtonOverlayRenderer {
	private ButtonOverlayRenderer() {
	}

	/** Label position in button coordinates; {@code y} is the baseline, not the top edge. */
	public record LabelBounds(int x, int y, int width, int height) {
	}

	/** Composites an overlay on top of the supplied state image. A null reference is a no-op. */
	public static void apply(BufferedImage button, ImageReference reference, LabelBounds label) {
		if (reference == null) {
			return;
		}
		int width = button.getWidth();
		int height = button.getHeight();
		BufferedImage overlay;
		try {
			if (reference.isSynthetic()) {
				try (SyntheticImageGenerator generator = SyntheticImageGeneratorFactory
						.create(reference.getSyntheticImage())) {
					// Generated frames can be cached/borrowed by their generator; copy pixels before closing it.
					try (ImageFrame frame = generator.generate(0, Map.of("width", width, "height", height, "textX",
							label.x(), "textY", label.y(), "textWidth", label.width(), "textHeight", label.height()))) {
						overlay = frame.toBufferedImage();
					}
				}
			} else if (reference.isStatic()) {
				overlay = ImageIO.read(Path.of(reference.getSourcePath()).toFile());
			} else {
				throw new IllegalArgumentException("Button overlay must be a static image or synthetic image");
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Cannot load button overlay " + reference.getSourcePath(), e);
		}
		if (overlay == null || overlay.getWidth() != width || overlay.getHeight() != height) {
			throw new IllegalArgumentException("Button overlay must be " + width + "x" + height + " pixels"
					+ (overlay == null ? " (no image decoded)"
							: ", got " + overlay.getWidth() + "x" + overlay.getHeight()));
		}
		Graphics2D g = button.createGraphics();
		try {
			g.drawImage(overlay, 0, 0, null);
		} finally {
			g.dispose();
		}
	}
}
