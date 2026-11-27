/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/menu/ButtonOverlayRendererTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;

import org.brts.common.utils.composition.ImageReference;
import org.junit.jupiter.api.Test;

class ButtonOverlayRendererTest {

	private static ImageReference svg(String data) {
		ImageReference ref = new ImageReference();
		ImageReference.SyntheticImageSource source = new ImageReference.SyntheticImageSource();
		source.setType("svg");
		source.setData(data);
		ref.setSyntheticImage(source);
		return ref;
	}

	private static final String UNDERLINE = "<#if textWidth gt 0><svg xmlns='http://www.w3.org/2000/svg' width='${width}' height='${height}'><rect x='${textX}' y='${textY + 4}' width='${textWidth}' height='3' fill='#FFD400'/></svg><#else><svg xmlns='http://www.w3.org/2000/svg' width='${width}' height='${height}'/></#if>";

	@Test
	void selectedSvgFollowsLabelAndKeepsOtherStatesUnchanged() {
		TextStyle style = new TextStyle();
		style.setNormalColor("#FFFFFF");
		style.setSelectedColor("#FFFFFF");
		style.setActivatedColor("#FFFFFF");
		ButtonStateOverlays overlays = new ButtonStateOverlays();
		overlays.setSelected(svg(UNDERLINE));
		style.setStateOverlays(overlays);
		TextRenderer.ButtonImages images = TextRenderer.renderTextButton("Hello", style.withDefaults());

		int yellow = 0;
		for (int y = 0; y < images.height(); y++) {
			for (int x = 0; x < images.width(); x++) {
				int pixel = images.selected().getRGB(x, y);
				if ((pixel & 0xFFFFFF) == 0xFFD400 && (pixel >>> 24) != 0) {
					yellow++;
					assertThat(images.normal().getRGB(x, y)).isNotEqualTo(pixel);
					assertThat(images.activated().getRGB(x, y)).isNotEqualTo(pixel);
				}
			}
		}
		assertThat(yellow).isPositive();
		assertThat(images.normal().getRGB(0, 0)).isEqualTo(images.activated().getRGB(0, 0));
	}

	@Test
	void stateOverridesMergeIndividually() {
		ButtonStateOverlays global = new ButtonStateOverlays();
		global.setSelected(svg(UNDERLINE));
		TextStyle base = new TextStyle();
		base.setStateOverlays(global);
		ButtonStateOverlays local = new ButtonStateOverlays();
		ImageReference activated = svg(UNDERLINE);
		local.setActivated(activated);
		TextStyle item = new TextStyle();
		item.setStateOverlays(local);
		TextStyle result = item.mergeOver(base).withDefaults();
		assertThat(result.getStateOverlays().getSelected()).isSameAs(global.getSelected());
		assertThat(result.getStateOverlays().getActivated()).isSameAs(activated);
		assertThat(result.getStateOverlays().getNormal()).isNull();
	}

	@Test
	void rejectsIncorrectSize() {
		BufferedImage button = new BufferedImage(80, 40, BufferedImage.TYPE_INT_ARGB);
		assertThatThrownBy(() -> ButtonOverlayRenderer.apply(button,
				svg("<svg xmlns='http://www.w3.org/2000/svg' width='10' height='10'/>"),
				new ButtonOverlayRenderer.LabelBounds(0, 0, 0, 0))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("80x40");
	}

	@Test
	void wrappedLabelAndEmptyLabelUseFinalVisibleLine() {
		TextStyle style = new TextStyle();
		ButtonStateOverlays overlays = new ButtonStateOverlays();
		overlays.setSelected(svg(UNDERLINE));
		style.setStateOverlays(overlays);
		TextRenderer.ButtonImages wrapped = TextRenderer.renderTextButton("Alpha Beta Gamma", style.withDefaults(),
				120);
		TextRenderer.ButtonImages empty = TextRenderer.renderTextButton("", style.withDefaults());
		assertThat(countYellow(wrapped.selected())).isPositive();
		assertThat(countYellow(empty.selected())).isZero();
	}

	private static int countYellow(BufferedImage image) {
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
