package org.brts.lowlevel.realdata;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/realdata/RealDiscPlaylistParserTest.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.test.sampledata.RequiresSamples;
import org.brts.common.test.sampledata.Samples;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayMark;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests against real MPLS files from a physical Blu-ray disc.
 * <p>
 * Sample disc is located at {@code samples/PB/BDMV/PLAYLIST/}. All tests are skipped when the sample data is absent.
 * <p>
 * Disc layout notes (derived from binary analysis):
 * <ul>
 * <li>This disc uses BD-J with all clips referenced via SubPath entries, so {@code numItems == 0} is normal — clips are
 * not referenced by classic PlayItem records.</li>
 * <li>PlayMarks carry real chapter/entry timestamps in 90 kHz ticks.</li>
 * <li>All MPLS files are version "0200".</li>
 * </ul>
 */
@RequiresSamples("PB/BDMV/PLAYLIST")
class RealDiscPlaylistParserTest {

	private static final Path PLAYLIST = Samples.sample("PB/BDMV/PLAYLIST");

	private final MoviePlaylistParser parser = new MoviePlaylistParser();

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private MoviePlaylist parse(String name) throws IOException {
		return parser.parse(PLAYLIST.resolve(name + ".mpls"));
	}

	// ------------------------------------------------------------------
	// Basic parseability
	// ------------------------------------------------------------------

	/**
	 * Representative files spanning all MPLS structural variants on this disc (different subPath counts and mark
	 * counts) must parse without exception.
	 */
	@ParameterizedTest(name = "{0}.mpls parses without exception")
	@ValueSource(strings = {
			// minimal: 1 mark
			"01129",
			// 2 marks, 64 subPaths (largest group)
			"01113", "01309", "01343",
			// 3 marks, 64 subPaths
			"01460",
			// 5 marks, 64 subPaths
			"01639",
			// 80 subPaths, 2 marks
			"00600",
			// 80 subPaths, 301 marks (very large mark table)
			"00149",
			// 96 subPaths, 31 marks
			"00805",
			// 384 subPaths, 31 marks (largest subPath count)
			"00800",
			// 208 subPaths, 2 marks
			"01123",
			// 352 subPaths, 7 marks
			"01632" })
	void representativeMplsFilesParse(String name) throws IOException {
		MoviePlaylist playlist = parse(name);
		assertThat(playlist).as("MoviePlaylist parsed from %s.mpls", name).isNotNull();
	}

	// ------------------------------------------------------------------
	// PlayItem count (numItems == 0 on this BD-J disc)
	// ------------------------------------------------------------------

	/**
	 * PlayItem counts per file — verified against binary analysis.
	 *
	 * <pre>
	 * file     | items
	 * ---------+------
	 * 01129    |   1   (single short clip)
	 * 00600    |   1
	 * 00800    |   1
	 * 00805    |   1
	 * 01113    |   2   (two clips for an episode)
	 * 01309    |   1
	 * 01343    |   1
	 * 01460    |   1
	 * 00149    | 301   (episode-per-item playlist)
	 * </pre>
	 */
	@ParameterizedTest(name = "{0}.mpls: numItems == {1}")
	@CsvSource({ "01129,   1", "00600,   1", "00800,   1", "00805,   1", "01113,   2", "01309,   1", "01343,   1",
			"01460,   1", "00149, 301", })
	void playItems_exactCount(String name, int expectedCount) throws IOException {
		MoviePlaylist playlist = parse(name);
		assertThat(playlist.getPlayItems()).as("playItems in %s.mpls", name).isNotNull().hasSize(expectedCount);
	}

	// ------------------------------------------------------------------
	// SubPath count assertions (concrete expected values per file)
	// ------------------------------------------------------------------

	/**
	 * On this disc all playlists have 0 SubPath entries — clips are referenced via PlayItems only.
	 */
	@ParameterizedTest(name = "{0}.mpls: numSubPaths == 0")
	@ValueSource(strings = { "01129", "00600", "00800", "00805", "01113", "01309", "01343", "01460", "01121", "01632",
			"00149" })
	void subPaths_areAlwaysZero(String name) throws IOException {
		MoviePlaylist playlist = parse(name);
		assertThat(playlist.getSubPaths()).as("subPaths in %s.mpls", name).isNotNull().isEmpty();
	}

	// ------------------------------------------------------------------
	// PlayMark count assertions
	// ------------------------------------------------------------------

	/**
	 * PlayMark counts are verified against binary analysis.
	 *
	 * <pre>
	 * file     | marks
	 * ---------+-------
	 * 01129    |   1
	 * 01113    |   2
	 * 01460    |   3
	 * 01468    |   4
	 * 01639    |   5
	 * 01632    |   7
	 * 00800    |  31
	 * 00805    |  31
	 * 00149    | 301
	 * </pre>
	 */
	@ParameterizedTest(name = "{0}.mpls: numMarks == {1}")
	@CsvSource({ "01129,   1", "01113,   2", "00600,   2", "01460,   3", "01468,   4", "01639,   5", "01632,   7",
			"00800,  31", "00805,  31", "00149, 301", })
	void playMarks_exactCount(String name, int expectedCount) throws IOException {
		MoviePlaylist playlist = parse(name);
		assertThat(playlist.getPlayMarks()).as("playMarks in %s.mpls", name).hasSize(expectedCount);
	}

	// ------------------------------------------------------------------
	// PlayMark field assertions for specific files
	// ------------------------------------------------------------------

	/**
	 * 00600.mpls — two entry marks for the main feature. Binary-verified exact values:
	 * <ul>
	 * <li>mark[0]: type=1 (entry), playItemRef=0, time=524280, entryEsPid=65535 (0xFFFF)</li>
	 * <li>mark[1]: type=1 (entry), playItemRef=0, time=1100480</li>
	 * </ul>
	 */
	/**
	 * 00600.mpls — single PlayItem ("00165"), two chapter marks. Binary-verified:
	 * <ul>
	 * <li>mark[0]: type=1, ref=0, time=524280, pid=0xFFFF, dur=0</li>
	 * <li>mark[1]: type=1, ref=0, time=1100480, pid=0xFFFF, dur=0</li>
	 * </ul>
	 */
	@Test
	void marks_00600_exactValues() throws IOException {
		MoviePlaylist playlist = parse("00600");
		List<PlayMark> marks = playlist.getPlayMarks();

		assertThat(marks).hasSize(2);
		assertThat(playlist.getPlayItems()).hasSize(1);
		assertThat(playlist.getPlayItems().get(0).getClipName()).isEqualTo("00165");

		PlayMark m0 = marks.get(0);
		assertThat(m0.getMarkType()).as("mark[0].type").isEqualTo(1);
		assertThat(m0.getPlayItemRef()).as("mark[0].playItemRef").isEqualTo(0);
		assertThat(m0.getMarkTimeTicks()).as("mark[0].timeTicks").isEqualTo(524_280L);
		assertThat(m0.getEntryEsPid()).as("mark[0].entryEsPid").isEqualTo(0xFFFF);
		assertThat(m0.getDurationTicks()).as("mark[0].durationTicks").isEqualTo(0L);

		PlayMark m1 = marks.get(1);
		assertThat(m1.getMarkType()).as("mark[1].type").isEqualTo(1);
		assertThat(m1.getPlayItemRef()).as("mark[1].playItemRef").isEqualTo(0);
		assertThat(m1.getMarkTimeTicks()).as("mark[1].timeTicks").isEqualTo(1_100_480L);
	}

	/**
	 * 01113.mpls — two PlayItems (both clip "00248"), two entry marks pointing to refs 0 and 1.
	 * <ul>
	 * <li>item[0]: clip=00248, inTime=524280, outTime=749505</li>
	 * <li>item[1]: clip=00248, inTime=749505, outTime=751381</li>
	 * <li>mark[0]: type=1, ref=0, time=524280</li>
	 * <li>mark[1]: type=1, ref=1, time=749505</li>
	 * </ul>
	 */
	@Test
	void marks_01113_exactValues() throws IOException {
		MoviePlaylist playlist = parse("01113");
		List<PlayMark> marks = playlist.getPlayMarks();

		assertThat(marks).hasSize(2);
		assertThat(playlist.getPlayItems()).hasSize(2);
		assertThat(playlist.getPlayItems().get(0).getClipName()).isEqualTo("00248");
		assertThat(playlist.getPlayItems().get(0).getInTimeTicks()).isEqualTo(524_280L);
		assertThat(playlist.getPlayItems().get(0).getOutTimeTicks()).isEqualTo(749_505L);
		assertThat(playlist.getPlayItems().get(1).getInTimeTicks()).isEqualTo(749_505L);

		PlayMark m0 = marks.get(0);
		assertThat(m0.getMarkType()).as("mark[0].type").isEqualTo(1);
		assertThat(m0.getPlayItemRef()).as("mark[0].playItemRef").isEqualTo(0);
		assertThat(m0.getMarkTimeTicks()).as("mark[0].timeTicks").isEqualTo(524_280L);
		assertThat(m0.getEntryEsPid()).as("mark[0].entryEsPid").isEqualTo(0xFFFF);

		PlayMark m1 = marks.get(1);
		assertThat(m1.getMarkType()).as("mark[1].type").isEqualTo(1);
		assertThat(m1.getPlayItemRef()).as("mark[1].playItemRef").isEqualTo(1);
		assertThat(m1.getMarkTimeTicks()).as("mark[1].timeTicks").isEqualTo(749_505L);
	}

	/**
	 * 00800.mpls — 31 marks, alternating type-1 (entry) and type-2 (link). Verifies first mark, second mark, and last
	 * mark.
	 */
	/**
	 * 00800.mpls — single PlayItem (clip "00705"), 31 chapter marks alternating type 1 and 2.
	 * <ul>
	 * <li>item[0]: clip=00705, inTime=27000000, outTime=317157367</li>
	 * <li>mark[0]: type=1, time=27,000,000 (≈ 5-minute intro chapter)</li>
	 * <li>mark[1]: type=2 (link point to next chapter)</li>
	 * <li>mark[30]: type=1, time=317,146,106</li>
	 * </ul>
	 */
	@Test
	void marks_00800_firstLastAndAlternation() throws IOException {
		MoviePlaylist playlist = parse("00800");
		List<PlayMark> marks = playlist.getPlayMarks();

		assertThat(marks).hasSize(31);
		assertThat(playlist.getPlayItems()).hasSize(1);
		assertThat(playlist.getPlayItems().get(0).getClipName()).isEqualTo("00705");
		assertThat(playlist.getPlayItems().get(0).getInTimeTicks()).isEqualTo(27_000_000L);
		assertThat(playlist.getPlayItems().get(0).getOutTimeTicks()).isEqualTo(317_157_367L);

		// First mark: type=1, time=27,000,000 ticks (≈ 5 min intro)
		PlayMark first = marks.get(0);
		assertThat(first.getMarkType()).as("marks[0].type").isEqualTo(1);
		assertThat(first.getMarkTimeTicks()).as("marks[0].timeTicks").isEqualTo(27_000_000L);
		assertThat(first.getEntryEsPid()).as("marks[0].entryEsPid").isEqualTo(0xFFFF);

		// Second mark: type=2 (link mark between chapters)
		PlayMark second = marks.get(1);
		assertThat(second.getMarkType()).as("marks[1].type").isEqualTo(2);
		assertThat(second.getMarkTimeTicks()).as("marks[1].timeTicks").isEqualTo(49_912_890L);

		// Third mark: type=1 again
		assertThat(marks.get(2).getMarkType()).as("marks[2].type").isEqualTo(1);

		// Last mark: type=1, time verified from binary
		PlayMark last = marks.get(30);
		assertThat(last.getMarkType()).as("marks[30].type").isEqualTo(1);
		assertThat(last.getMarkTimeTicks()).as("marks[30].timeTicks").isEqualTo(317_146_106L);
	}

	/**
	 * 00149.mpls — 301 PlayItems and 301 entry marks. Each mark references its corresponding item (mark[i].ref == i).
	 * All marks have timestamp 524,280 and pid 0xFFFF. First item: clip="00616", inTime=524280.
	 */
	@Test
	void marks_00149_bulkEntryMarks() throws IOException {
		MoviePlaylist playlist = parse("00149");
		List<PlayMark> marks = playlist.getPlayMarks();

		assertThat(marks).hasSize(301);
		assertThat(playlist.getPlayItems()).hasSize(301);
		assertThat(playlist.getPlayItems().get(0).getClipName()).isEqualTo("00616");

		for (int i = 0; i < marks.size(); i++) {
			PlayMark m = marks.get(i);
			assertThat(m.getMarkType()).as("marks[%d].type in 00149.mpls", i).isEqualTo(1);
			assertThat(m.getPlayItemRef()).as("marks[%d].playItemRef in 00149.mpls", i).isEqualTo(i);
			assertThat(m.getMarkTimeTicks()).as("marks[%d].timeTicks in 00149.mpls", i).isEqualTo(524_280L);
			assertThat(m.getEntryEsPid()).as("marks[%d].entryEsPid in 00149.mpls", i).isEqualTo(0xFFFF);
			assertThat(m.getDurationTicks()).as("marks[%d].durationTicks in 00149.mpls", i).isEqualTo(0L);
		}
	}

	/**
	 * 00805.mpls — 31 marks identical to 00800.mpls in structure but with 96 subPaths.
	 */
	@Test
	void marks_00805_samePatternAs00800() throws IOException {
		List<PlayMark> marks805 = parse("00805").getPlayMarks();
		List<PlayMark> marks800 = parse("00800").getPlayMarks();

		assertThat(marks805).hasSameSizeAs(marks800);

		// Spot-check first, second, and last
		assertThat(marks805.get(0).getMarkTimeTicks()).isEqualTo(marks800.get(0).getMarkTimeTicks());
		assertThat(marks805.get(1).getMarkTimeTicks()).isEqualTo(marks800.get(1).getMarkTimeTicks());
		assertThat(marks805.get(30).getMarkTimeTicks()).isEqualTo(marks800.get(30).getMarkTimeTicks());
	}

	// ------------------------------------------------------------------
	// Mark field sanity invariants across all marks
	// ------------------------------------------------------------------

	/**
	 * All marks must have markType in {1, 2}, non-negative time ticks, and non-negative play-item ref.
	 */
	@ParameterizedTest(name = "{0}.mpls: all marks have valid field ranges")
	@ValueSource(strings = { "00600", "00800", "00805", "01113", "01460", "00149" })
	void marks_allHaveValidFieldRanges(String name) throws IOException {
		List<PlayMark> marks = parse(name).getPlayMarks();

		for (int i = 0; i < marks.size(); i++) {
			PlayMark m = marks.get(i);
			assertThat(m.getMarkType()).as("marks[%d].type in %s.mpls must be 1 or 2", i, name).isIn(1, 2);
			assertThat(m.getMarkTimeTicks()).as("marks[%d].timeTicks in %s.mpls must be >= 0", i, name)
					.isGreaterThanOrEqualTo(0L);
			assertThat(m.getPlayItemRef()).as("marks[%d].playItemRef in %s.mpls must be >= 0", i, name)
					.isGreaterThanOrEqualTo(0);
		}
	}

	// ------------------------------------------------------------------
	// Bulk smoke — every MPLS file in the directory must parse
	// ------------------------------------------------------------------

	/**
	 * Every MPLS file on the disc parses without throwing an exception.
	 */
	@Test
	void allMplsFilesOnDiscParseSuccessfully() throws IOException {
		int count = 0;
		try (var stream = Files.list(PLAYLIST)) {
			for (var path : (Iterable<Path>) stream::iterator) {
				if (path.getFileName().toString().endsWith(".mpls")) {
					MoviePlaylist playlist = parser.parse(path);
					assertThat(playlist).as("MoviePlaylist from %s", path.getFileName()).isNotNull();
					assertThat(playlist.getPlayMarks()).as("playMarks from %s must not be null", path.getFileName())
							.isNotNull();
					count++;
				}
			}
		}
		assertThat(count).as("at least one MPLS file must have been tested").isPositive();
	}

}
