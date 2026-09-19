package org.brts.lowlevel.titlemenu.descriptor;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/titlemenu/descriptor/LayoutConfigTest.java' is part of BRTS.
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

import org.junit.jupiter.api.Test;

class LayoutConfigTest {

	@Test
	void omittedValues_useConfiguredFallbacks() {
		LayoutConfig config = new LayoutConfig();

		assertThat(config.effectiveType()).isEqualTo(LayoutType.TEXT_LIST);
		assertThat(config.effectiveColumns()).isEqualTo(1);
		assertThat(config.effectiveMarginTop()).isEqualTo(100);
		assertThat(config.effectiveMarginBottom()).isEqualTo(100);
		assertThat(config.effectiveMarginLeft()).isEqualTo(80);
		assertThat(config.effectiveMarginRight()).isEqualTo(80);
		assertThat(config.effectiveSpacingX()).isEqualTo(40);
		assertThat(config.effectiveSpacingY()).isEqualTo(60);
		assertThat(config.effectiveThumbnailWidth()).isEqualTo(320);
		assertThat(config.effectiveThumbnailHeight()).isEqualTo(180);
		assertThat(config.effectiveThumbnailDurationSec()).isEqualTo(5.0);
		assertThat(config.effectiveShowLabels()).isTrue();
		assertThat(config.effectiveMaxButtonWidth(1920)).isEqualTo(1440);
	}

	@Test
	void explicitValues_overrideDefaults_includingZeroAndFalse() {
		LayoutConfig config = new LayoutConfig();
		config.setType(LayoutType.THUMBNAIL_GRID);
		config.setColumns(0);
		config.setMarginTop(0);
		config.setShowLabels(false);
		config.setMaxButtonWidth(0);

		assertThat(config.effectiveType()).isEqualTo(LayoutType.THUMBNAIL_GRID);
		assertThat(config.effectiveColumns()).isZero();
		assertThat(config.effectiveMarginTop()).isZero();
		assertThat(config.effectiveShowLabels()).isFalse();
		assertThat(config.effectiveMaxButtonWidth(1920)).isZero();
	}

}
