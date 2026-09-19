package org.brts.lowlevel.igs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/igs/IgsMenuAssemblerTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.List;

import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.junit.jupiter.api.Test;

class IgsMenuAssemblerTest {

	@Test
	void buildObjectsFromImages_fragmentsLargeRleWithoutChangingLogicalObjectId() {
		BufferedImage image = new BufferedImage(512, 256, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				image.setRGB(x, y, (x & 1) == 0 ? 0xFFFFFFFF : 0xFF000000);
			}
		}
		IgsPalette palette = PaletteBuilder.buildFromImages(0, image);

		List<IgsObject> fragments = IgsMenuAssembler.buildObjectsFromImages(List.of(image), palette);

		assertThat(fragments).hasSizeGreaterThan(1).allSatisfy(fragment -> {
			assertThat(fragment.getId()).isZero();
			assertThat(IgsPgsCodec.encodeObject(fragment).length).isLessThanOrEqualTo(IgsPgsCodec.MAX_ODS_DATA_LENGTH);
		});
		assertThat(fragments.get(0).getSequenceDescriptor().isFirstInSequence()).isTrue();
		assertThat(fragments.get(0).getSequenceDescriptor().isLastInSequence()).isFalse();
		assertThat(fragments.get(fragments.size() - 1).getSequenceDescriptor().isFirstInSequence()).isFalse();
		assertThat(fragments.get(fragments.size() - 1).getSequenceDescriptor().isLastInSequence()).isTrue();
	}

}
