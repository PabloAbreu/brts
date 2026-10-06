/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/test/java/org/brts/middle/menu/render/ButtonImageOverlayTest.java' is part of BRTS.
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

package org.brts.middle.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.brts.common.menu.ButtonStateOverlays;
import org.brts.common.menu.TextStyle;
import org.brts.common.utils.composition.ImageReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ButtonImageOverlayTest {

	@TempDir
	Path temp;

	@Test
	void selectedOverlayTracksLabelAfterIconAndSkipsEmptyLabel() {
		ImageReference ref = new ImageReference();
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("svg");
		source.setData(
				"<#if textWidth gt 0><svg xmlns='http://www.w3.org/2000/svg' width='${width}' height='${height}'><rect x='${textX}' y='${textY + 4}' width='${textWidth}' height='3' fill='#FFD400'/></svg><#else><svg xmlns='http://www.w3.org/2000/svg' width='${width}' height='${height}'/></#if>");
		ref.setSyntheticImage(source);
		ButtonStateOverlays overlays = new ButtonStateOverlays();
		overlays.setSelected(ref);
		TextStyle style = new TextStyle();
		style.setStateOverlays(overlays);
		var labeled = ButtonImageRenderer.renderTextButton("Settings", style.withDefaults());
		var empty = ButtonImageRenderer.renderTextButton("", style.withDefaults());
		assertThat(yellowPixels(labeled.selected())).isPositive();
		assertThat(yellowPixels(labeled.normal())).isZero();
		assertThat(yellowPixels(empty.selected())).isZero();
	}

	@Test
	void iconShiftsUnderlineToTextRatherThanIcon() throws Exception {
		BufferedImage icon = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 8; x++) {
				icon.setRGB(x, y, 0xFFFFFFFF);
			}
		}
		Path iconPath = temp.resolve("icon.png");
		ImageIO.write(icon, "png", iconPath.toFile());
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("svg");
		source.setData(
				"<svg xmlns='http://www.w3.org/2000/svg' width='${width}' height='${height}'><rect x='${textX}' y='${textY + 4}' width='${textWidth}' height='2' fill='#FFD400'/></svg>");
		ImageReference overlay = new ImageReference();
		overlay.setSyntheticImage(source);
		ButtonStateOverlays overlays = new ButtonStateOverlays();
		overlays.setSelected(overlay);
		TextStyle style = new TextStyle();
		style.setStateOverlays(overlays);
		var button = ButtonImageRenderer.renderButton("Play", iconPath, style.withDefaults());
		assertThat(yellowPixels(button.selected())).isPositive();
		for (int y = 0; y < button.height(); y++) {
			for (int x = 0; x < 60; x++) {
				assertThat(button.selected().getRGB(x, y) & 0xFFFFFF).isNotEqualTo(0xFFD400);
			}
		}
	}

	private static int yellowPixels(BufferedImage image) {
		int count = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) & 0xFFFFFF) == 0xFFD400 && (image.getRGB(x, y) >>> 24) != 0) {
					count++;
				}
			}
		}
		return count;
	}
}
