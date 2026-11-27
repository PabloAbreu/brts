/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/test/java/org/brts/highlevel/template/StyledDiscTemplateTest.java' is part of BRTS.
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

package org.brts.highlevel.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.brts.highlevel.descriptor.MovieDiscDescriptor;
import org.brts.highlevel.descriptor.MovieDiscDescriptor.MovieFeature;
import org.brts.highlevel.descriptor.TvSeriesDiscDescriptor;
import org.brts.highlevel.style.StyleTemplate;
import org.brts.highlevel.style.StyleTemplateLoader;
import org.brts.middle.descriptor.DiscDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StyledDiscTemplateTest {

	private static StyleTemplate sciFi;

	@BeforeAll
	static void loadTemplate() throws IOException {
		sciFi = new StyleTemplateLoader(() -> List.of(Path.of("../assets").toAbsolutePath().normalize()))
				.load("brts:sci-fi");
	}

	@Test
	void tvSeriesUsesTemplateBackgroundWhenNoVideoGiven() throws IOException {
		DiscDescriptor disc = new TvSeriesDiscTemplate().expand(tvSeries(null), sciFi);

		assertThat(disc.isHasTopMenu()).isTrue();
		assertThat(disc.getOutputFolder()).isEqualTo("/tmp/out");
		assertThat(disc.getStyle()).isSameAs(sciFi.manifest().getStyle());
		assertThat(disc.getPopupMenu()).isSameAs(sciFi.manifest().getPopupMenu());
		assertThat(disc.getTitleMenuConfig().getBackgroundSource())
				.isSameAs(sciFi.manifest().getTitleMenu().getBackgroundSource());
		assertThat(disc.getTitleMenuConfig().getBaseDir()).isEqualTo(sciFi.directory().toString());
		assertThat(disc.getTitleMenuConfig().getStyle().getFontSize()).isEqualTo(34);
		assertThat(disc.getTitleMenuConfig().getBoundingBox()).isNotNull();
	}

	@Test
	void tvSeriesVideoBackgroundWinsOverTemplate() throws IOException {
		DiscDescriptor disc = new TvSeriesDiscTemplate().expand(tvSeries("/videos/bg.mkv"), sciFi);

		assertThat(disc.getTitleMenuConfig().getBackgroundSource().getVideoPath()).isEqualTo("/videos/bg.mkv");
		assertThat(disc.getTitleMenuConfig().getBaseDir()).isNull();
		assertThat(disc.getTitleMenuConfig().getStyle()).isNotNull();
	}

	@Test
	void tvSeriesWithoutBackgroundHasNoTopMenu() throws IOException {
		DiscDescriptor disc = new TvSeriesDiscTemplate().expand(tvSeries(null), null);

		assertThat(disc.isHasTopMenu()).isFalse();
		assertThat(disc.getTitleMenuConfig()).isNull();
		assertThat(disc.isTopMenuConfigConsistent()).isTrue();
	}

	@Test
	void movieWithBonusGetsStyledTitleMenu() {
		MovieDiscDescriptor movie = movie(feature("/m/main.mkv", "Feature"), feature("/m/bonus.mkv", "Making Of"));

		DiscDescriptor disc = new MovieDiscTemplate().expand(movie, sciFi);

		assertThat(disc.isHasTopMenu()).isTrue();
		assertThat(disc.getTitleMenuConfig().getBackgroundSource()).isNotNull();
		assertThat(disc.getTitles()).extracting("displayName").containsExactly("Feature", "Making Of");
	}

	@Test
	void singleTitleMovieHasNoTitleMenuButKeepsPopupStyle() {
		DiscDescriptor disc = new MovieDiscTemplate().expand(movie(feature("/m/main.mkv", null)), sciFi);

		assertThat(disc.isHasTopMenu()).isFalse();
		assertThat(disc.getTitleMenuConfig()).isNull();
		assertThat(disc.getPopupMenu()).isNotNull();
	}

	@Test
	void unstyledMovieHasNoTitleMenu() {
		MovieDiscDescriptor movie = movie(feature("/m/main.mkv", null), feature("/m/bonus.mkv", null));

		DiscDescriptor disc = new MovieDiscTemplate().expand(movie, null);

		assertThat(disc.isHasTopMenu()).isFalse();
		assertThat(disc.getStyle()).isNull();
	}

	private static TvSeriesDiscDescriptor tvSeries(String backgroundVideo) {
		TvSeriesDiscDescriptor d = new TvSeriesDiscDescriptor();
		d.setDiscTitle("Series S01");
		d.setOutputDirectory("/tmp/out");
		d.setEpisodeFiles(List.of("/e/01.mkv", "/e/02.mkv"));
		d.setMenuBackgroundVideoPath(backgroundVideo);
		return d;
	}

	private static MovieDiscDescriptor movie(MovieFeature main, MovieFeature... bonus) {
		MovieDiscDescriptor d = new MovieDiscDescriptor();
		d.setDiscTitle("Movie");
		d.setMainFeature(main);
		d.setBonusTracks(List.of(bonus));
		return d;
	}

	private static MovieFeature feature(String mkv, String label) {
		MovieFeature f = new MovieFeature();
		f.setSourceMkv(mkv);
		f.setLabel(label);
		return f;
	}

}
