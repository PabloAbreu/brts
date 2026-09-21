package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/test/java/org/brts/cli/low/TrackDisplayNameResolverI18NTest.java' is part of BRTS.
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
import org.brts.common.utils.BrtsI18NLabels;
import org.brts.lowlevel.popupmenu.TrackDisplayNameResolver;
import org.junit.jupiter.api.Test;

class TrackDisplayNameResolverI18NTest {

	@Test
	void resolvesPackagedEnglishLanguageName() {
		assertThat(BrtsI18NLabels.getLanguageName("fra", "fallback")).isEqualTo("French");
	}

	@Test
	void synthesizesUnnamedTrackUsingPackagedResources() {
		SourceTrack track = new SourceTrack();
		track.setTrackNumber(1);
		track.setTrackName("unnamed");
		track.setLanguage("fra");
		track.setCodingType(StreamCodingType.DOLBY_AC3);
		track.setChannels(6);

		assertThat(TrackDisplayNameResolver.resolve(track)).isEqualTo("French - AC3 5.1");
	}
}