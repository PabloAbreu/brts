package org.brts.lowlevel.titlemenu.descriptor;

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