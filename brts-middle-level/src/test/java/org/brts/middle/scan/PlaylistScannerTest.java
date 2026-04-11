package org.brts.middle.scan;

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
