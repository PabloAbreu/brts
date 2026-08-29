package org.brts.common.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import org.junit.jupiter.api.Test;

class GlyphFallbackTextTest {

	// Unicode noncharacter (U+FFFF): permanently reserved, guaranteed to never have a mapped glyph in any font,
	// unlike Private Use Area code points which some platform font-fallback chains may still resolve.
	private static final String UNDISPLAYABLE = "\uFFFF";

	private final Font primary = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
	private final Font fallback = new Font(Font.SERIF, Font.PLAIN, 20);

	@Test
	void split_allDisplayableByPrimary_returnsSingleRun() {
		List<GlyphFallbackText.Run> runs = GlyphFallbackText.split("Audio", primary, fallback);

		assertThat(runs).hasSize(1);
		assertThat(runs.get(0).font()).isEqualTo(primary);
		assertThat(runs.get(0).text()).isEqualTo("Audio");
	}

	@Test
	void split_mixedText_splitsUndisplayableGlyphIntoFallbackRun() {
		String text = "Audio " + UNDISPLAYABLE;

		List<GlyphFallbackText.Run> runs = GlyphFallbackText.split(text, primary, fallback);

		assertThat(runs).hasSize(2);
		assertThat(runs.get(0).font()).isEqualTo(primary);
		assertThat(runs.get(0).text()).isEqualTo("Audio ");
		assertThat(runs.get(1).font()).isEqualTo(fallback);
		assertThat(runs.get(1).text()).isEqualTo(UNDISPLAYABLE);
	}

	@Test
	void split_samePrimaryAndFallbackFont_returnsSingleRunWithoutSplitting() {
		List<GlyphFallbackText.Run> runs = GlyphFallbackText.split("Audio " + UNDISPLAYABLE, primary, primary);

		assertThat(runs).hasSize(1);
		assertThat(runs.get(0).font()).isEqualTo(primary);
	}

	@Test
	void split_emptyText_returnsNoRuns() {
		assertThat(GlyphFallbackText.split("", primary, fallback)).isEmpty();
		assertThat(GlyphFallbackText.split(null, primary, fallback)).isEmpty();
	}

	@Test
	void width_sumsPerRunWidths() {
		BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scratch.createGraphics();

		String text = "Audio " + UNDISPLAYABLE;
		List<GlyphFallbackText.Run> runs = GlyphFallbackText.split(text, primary, fallback);
		int expected = g.getFontMetrics(primary).stringWidth("Audio ")
				+ g.getFontMetrics(fallback).stringWidth(UNDISPLAYABLE);

		assertThat(GlyphFallbackText.width(g, text, primary, fallback)).isEqualTo(expected);
		g.dispose();
	}

	@Test
	void fallbackFont_blankConfiguredName_reusesPrimaryFont() {
		TextStyle style = new TextStyle();
		style.setFallbackFontName(null);
		style.setFontStyle(Font.PLAIN);
		style.setFontSize(20);

		assertThat(GlyphFallbackText.fallbackFont(style, primary)).isEqualTo(primary);
	}

	@Test
	void fallbackFont_configuredName_buildsFontWithSameStyleAndSize() {
		TextStyle style = new TextStyle();
		style.setFallbackFontName(Font.SERIF);
		style.setFontStyle(Font.BOLD);
		style.setFontSize(30);

		Font built = GlyphFallbackText.fallbackFont(style, primary);

		assertThat(built.getFamily()).isEqualTo(new Font(Font.SERIF, Font.BOLD, 30).getFamily());
		assertThat(built.getStyle()).isEqualTo(Font.BOLD);
		assertThat(built.getSize()).isEqualTo(30);
	}

}
