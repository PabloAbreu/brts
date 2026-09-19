package org.brts.middle.scan;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/test/java/org/brts/middle/scan/PlaylistScannerTest.java' is part of BRTS.
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

import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link PlaylistScanner} helper methods and heuristics.
 */
class PlaylistScannerTest {

	// ── Duration computation ────────────────────────────────────────────────

	@Test
	void computeDurationSeconds_singleItem() {
		MoviePlaylist pl = playlist("00001", item("00001", 0, 45_000 * 120)); // 120
																				// seconds
																				// at 45
																				// kHz
		double dur = PlaylistScanner.computeDurationSeconds(pl);
		assertThat(dur).isCloseTo(120.0, within(0.01));
	}

	@Test
	void computeDurationSeconds_multipleItems() {
		// Two items: 60s + 30s (MPLS ticks are 45 kHz)
		MoviePlaylist pl = playlist("00002", item("00001", 0, 45_000 * 60), item("00002", 45_000 * 10, 45_000 * 40)); // 30s
																														// span
		double dur = PlaylistScanner.computeDurationSeconds(pl);
		assertThat(dur).isCloseTo(90.0, within(0.01));
	}

	@Test
	void computeDurationSeconds_emptyPlayItems() {
		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlayItems(List.of());
		assertThat(PlaylistScanner.computeDurationSeconds(pl)).isEqualTo(0.0);
	}

	@Test
	void computeDurationSeconds_nullPlayItems() {
		MoviePlaylist pl = new MoviePlaylist();
		assertThat(PlaylistScanner.computeDurationSeconds(pl)).isEqualTo(0.0);
	}

	// ── Duration formatting ─────────────────────────────────────────────────

	@Test
	void formatDuration_typical() {
		assertThat(PlaylistScanner.formatDuration(3661.0)).isEqualTo("01:01:01");
	}

	@Test
	void formatDuration_zero() {
		assertThat(PlaylistScanner.formatDuration(0.0)).isEqualTo("00:00:00");
	}

	@Test
	void formatDuration_longMovie() {
		// 2h30m
		assertThat(PlaylistScanner.formatDuration(9000.0)).isEqualTo("02:30:00");
	}

	// ── Helpers ─────────────────────────────────────────────────────────────

	private static MoviePlaylist playlist(String name, PlayItem... items) {
		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlaylistName(name);
		pl.setPlayItems(List.of(items));
		return pl;
	}

	private static PlayItem item(String clipName, long inTicks, long outTicks) {
		PlayItem pi = new PlayItem();
		pi.setClipName(clipName);
		pi.setInTimeTicks(inTicks);
		pi.setOutTimeTicks(outTicks);
		return pi;
	}

}
