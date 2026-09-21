package org.brts.lowlevel.popupmenu;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/popupmenu/TrackDisplayNameResolverTest.java' is part of BRTS.
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

import org.brts.common.mkv.SourceMediaInfo.SourceTrack;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;

class TrackDisplayNameResolverTest {

	@Test
	void preservesMeaningfulSourceName() {
		SourceTrack track = audioTrack("Director's Commentary", "fra", 6);

		assertThat(TrackDisplayNameResolver.resolve(track)).isEqualTo("Director's Commentary");
	}

	@Test
	void synthesizesCaseInsensitiveUnnamedSourceName() {
		SourceTrack track = audioTrack(" UnNaMeD ", "fra", 6);

		assertThat(TrackDisplayNameResolver.resolve(track)).isEqualTo("Fra - AC3 5.1");
	}

	@Test
	void omitsChannelsForSubtitle() {
		SourceTrack track = new SourceTrack();
		track.setTrackNumber(2);
		track.setTrackName("unnamed");
		track.setLanguage("fra");
		track.setCodingType(StreamCodingType.PRESENTATION_GRAPHICS);

		assertThat(TrackDisplayNameResolver.resolve(track)).isEqualTo("Fra - PGS");
	}

	@Test
	void usesTrackNumberForUndefinedLanguage() {
		SourceTrack track = audioTrack("unnamed", "und", 2);

		assertThat(TrackDisplayNameResolver.resolve(track)).isEqualTo("Track 1 - AC3 2.0");
	}

	@Test
	void fallsBackToDisplayFormForUnmappedLanguage() {
		SourceTrack track = audioTrack("unnamed", "qaa", 2);

		assertThat(TrackDisplayNameResolver.resolve(track)).isEqualTo("Qaa - AC3 2.0");
	}

	@Test
	void rendersCustomTemplate() {
		SourceTrack track = audioTrack("unnamed", "fra", 6);

		String result = TrackDisplayNameResolver.synthesize(track,
				"${trackNumber}: ${codec} / ${language}<#if channels?has_content> (${channels})</#if>");

		assertThat(result).isEqualTo("1: AC3 / Fra (5.1)");
	}

	private static SourceTrack audioTrack(String name, String language, int channels) {
		SourceTrack track = new SourceTrack();
		track.setTrackNumber(1);
		track.setTrackName(name);
		track.setLanguage(language);
		track.setCodingType(StreamCodingType.DOLBY_AC3);
		track.setChannels(channels);
		return track;
	}
}