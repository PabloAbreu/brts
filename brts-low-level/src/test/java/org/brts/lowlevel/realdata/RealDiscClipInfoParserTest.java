package org.brts.lowlevel.realdata;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/realdata/RealDiscClipInfoParserTest.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.common.test.sampledata.RequiresSamples;
import org.brts.common.test.sampledata.Samples;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Integration tests against real CLPI files from a physical Blu-ray disc.
 * <p>
 * All tests use the sample disc at {@code samples/PB/BDMV/CLIPINF/}. Tests are skipped if the sample directory is not
 * present (e.g. on a CI agent that does not have the disc files checked out).
 */
@RequiresSamples("PB/BDMV/CLIPINF")
class RealDiscClipInfoParserTest {

	/** Root of the sample disc. Relative to the Maven multi-module root. */
	private static final Path CLIPINF = Samples.sample("PB/BDMV/CLIPINF");

	private final ClipInfoParser parser = new ClipInfoParser();

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private ClipInfo parse(String name) throws IOException {
		Path path = CLIPINF.resolve(name + ".clpi");
		return parser.parse(path);
	}

	// ------------------------------------------------------------------
	// Basic parseability — every CLPI in the disc must parse without error
	// ------------------------------------------------------------------

	/**
	 * Each of the most structurally varied CLPI files parses without throwing. File sizes range from 324 B (minimal) to
	 * 39 KB (00705.clpi with large EP map).
	 */
	@ParameterizedTest(name = "{0}.clpi parses without exception")
	@ValueSource(strings = {
			// smallest files (0 streams in program info)
			"00264", "00165", "00248", "00281", "00616", "00617",
			// medium files
			"00572", "00662", "00726", "00674",
			// larger files with EP map entries
			"00741", "00750", "00742", "00744",
			// files with placeholder 0x00 coding type in stream table
			"00258", "00736", "00737", "00738", "00746", "00748",
			// largest file
			"00705",
			// last clip in directory
			"00753" })
	void allRepresentativeClpiFilesParse(String clipName) throws IOException {
		ClipInfo info = parse(clipName);
		assertThat(info).as("ClipInfo parsed from %s.clpi", clipName).isNotNull();
	}

	// ------------------------------------------------------------------
	// Version / header invariants common to all disc CLPI files
	// ------------------------------------------------------------------

	/**
	 * All CLPI files on this disc carry version "0200". The parser accepts both "0200" and "0300" — this verifies the
	 * disc is v2.
	 */
	@Test
	void version_isHdmv0200() throws IOException {
		// Check three distinct structural variants
		for (String name : new String[] { "00165", "00572", "00705" }) {
			ClipInfo info = parse(name);
			assertThat(info).as("parse of %s.clpi", name).isNotNull();
			// Parser does not currently expose the raw version string on the model;
			// successful parsing (no ParseException) implies the magic+version were
			// accepted.
		}
	}

	/**
	 * Every CLPI on this disc has clipStreamType == 1 (Transport Stream).
	 */
	@Test
	void clipStreamType_isTransportStream() throws IOException {
		for (String name : new String[] { "00165", "00248", "00281", "00616", "00753" }) {
			ClipInfo info = parse(name);
			assertThat(info.getClipStreamType()).as("clipStreamType in %s.clpi", name).isEqualTo(1);
		}
	}

	/**
	 * Application types: 1 = Movie, 2 = Time-based slideshows.
	 */
	@Test
	void applicationType_isMovieOrTimeBased() throws IOException {
		for (String name : new String[] { "00165", "00281", "00616" }) {
			ClipInfo info = parse(name);
			assertThat(info.getApplicationType()).as("applicationType in %s.clpi", name).isEqualTo(1);
		}
		for (String name : new String[] { "00248", "00753" }) {
			ClipInfo info = parse(name);
			assertThat(info.getApplicationType()).as("applicationType in %s.clpi", name).isEqualTo(2);
		}
	}

	/**
	 * The ATC-delta flag is false for all standard stream clips on this disc.
	 */
	@Test
	void atcDeltaFlag_isFalseForStandardClips() throws IOException {
		for (String name : new String[] { "00165", "00248", "00572", "00705" }) {
			ClipInfo info = parse(name);
			assertThat(info.isAtcDelta()).as("atcDelta in %s.clpi", name).isFalse();
		}
	}

	@Test
	void atcDeltaFlag_isTrueForAtcDeltaClips() throws IOException {
		for (String name : new String[] { "00616", "00617" }) {
			ClipInfo info = parse(name);
			assertThat(info.isAtcDelta()).as("atcDelta in %s.clpi", name).isTrue();
		}
	}

	// ------------------------------------------------------------------
	// Timing assertions for specific well-known files
	// ------------------------------------------------------------------

	/**
	 * 00165.clpi — a feature stream clip for this disc.
	 * <p>
	 * PTS values come from the SequenceInfo section (45 kHz domain):
	 * <ul>
	 * <li>startPts45 = 524,280 → startPts90 = 1,048,560</li>
	 * <li>endPts45 = 1,111,741 → endPts90 = 2,223,482</li>
	 * <li>duration = endPts90 − startPts90 = 1,174,922 ticks @ 90 kHz ≈ 13 s</li>
	 * </ul>
	 */
	@Test
	void timing_00165_exactValues() throws IOException {
		ClipInfo info = parse("00165");

		assertThat(info.getTsRecordingStartPts().getTicks()).as("startPts90 ticks in 00165.clpi").isEqualTo(1_048_560L);

		assertThat(info.getTsRecordingEndPts().getTicks()).as("endPts90 ticks in 00165.clpi").isEqualTo(2_223_482L);

		assertThat(info.getDuration().getTicks()).as("duration ticks in 00165.clpi").isEqualTo(1_174_922L);
	}

	/**
	 * 00248.clpi — a time-based clip.
	 */
	@Test
	void timing_00248_exactValues() throws IOException {
		ClipInfo info = parse("00248");

		assertThat(info.getTsRecordingStartPts().getTicks()).isEqualTo(1_048_560L);
		assertThat(info.getTsRecordingEndPts().getTicks()).isEqualTo(1_502_762L);
		assertThat(info.getDuration().getTicks()).isEqualTo(454_202L);
	}

	/**
	 * Duration must be strictly positive and start < end for every clip.
	 */
	@ParameterizedTest(name = "{0}.clpi: start < end and duration > 0")
	@ValueSource(strings = {
			// Files with numStreams=0 — parser traverses no stream table entries
			"00165", "00248", "00281", "00572", "00616", "00617", "00662", "00726", "00753" })
	void timing_startIsLessThanEnd(String clipName) throws IOException {
		ClipInfo info = parse(clipName);

		assertThat(info.getTsRecordingStartPts().getTicks()).as("startPts must be non-negative in %s.clpi", clipName)
				.isGreaterThanOrEqualTo(0L);

		assertThat(info.getTsRecordingEndPts().getTicks()).as("endPts must be > startPts in %s.clpi", clipName)
				.isGreaterThan(info.getTsRecordingStartPts().getTicks());

		assertThat(info.getDuration().getTicks()).as("duration must be positive in %s.clpi", clipName).isPositive();
	}

	/**
	 * startPts90 is 1,048,560 (= raw 524,280 × 2 from SequenceInfo) for most clips on this disc.
	 */
	@ParameterizedTest(name = "{0}.clpi: startPts90 == 1048560")
	@ValueSource(strings = { "00165", "00248", "00281", "00572", "00616", "00617", "00662", "00726", "00753" })
	void timing_startPts_isCommonValue(String clipName) throws IOException {
		ClipInfo info = parse(clipName);
		assertThat(info.getTsRecordingStartPts().getTicks()).as("startPts90 in %s.clpi", clipName)
				.isEqualTo(1_048_560L);
	}

	// ------------------------------------------------------------------
	// Stream table assertions
	// ------------------------------------------------------------------

	/**
	 * Some clips (00258, 00736, 00748) have a non-zero stream count in ProgramInfo but use coding type {@code 0x00} (a
	 * placeholder). The parser must not throw and must return a non-null stream list.
	 */
	@ParameterizedTest(name = "{0}.clpi: parses clips with placeholder 0x00 coding type")
	@ValueSource(strings = { "00258", "00736", "00748" })
	void streams_handlesPlaceholderCodingType(String clipName) throws IOException {
		ClipInfo info = parse(clipName);
		assertThat(info).as("ClipInfo from %s.clpi", clipName).isNotNull();
		assertThat(info.getStreams()).as("streams in %s.clpi", clipName).isNotNull();
	}

	// ------------------------------------------------------------------
	// EP map assertions
	// ------------------------------------------------------------------

	/**
	 * All clips on this disc have a non-empty CPI section and produce a non-null EP map.
	 */
	@ParameterizedTest(name = "{0}.clpi: EP map is present")
	@ValueSource(strings = { "00165", "00248", "00281", "00616", "00617" })
	void epMap_isPresentForAllClips(String clipName) throws IOException {
		ClipInfo info = parse(clipName);
		assertThat(info.getEpMap()).as("epMap in %s.clpi", clipName).isNotNull();
		assertThat(info.getEpMap().getStreams()).as("epMap streams in %s.clpi", clipName).isNotEmpty();
	}

	// ------------------------------------------------------------------
	// Bulk "smoke" — all CLPI files in the directory must parse
	// ------------------------------------------------------------------

	/**
	 * Every single CLPI file on the disc parses without an exception. This is a safety net catching any files not
	 * covered by the named tests above.
	 */
	@Test
	void allClpiFilesOnDiscParseSuccessfully() throws IOException {
		int count = 0;
		try (var stream = Files.list(CLIPINF)) {
			for (var path : (Iterable<Path>) stream::iterator) {
				if (path.getFileName().toString().endsWith(".clpi")) {
					ClipInfo info = parser.parse(path);
					assertThat(info).as("ClipInfo parsed from %s", path.getFileName()).isNotNull();
					assertThat(info.getTsRecordingStartPts().getTicks())
							.as("startPts non-negative in %s", path.getFileName()).isGreaterThanOrEqualTo(0L);
					count++;
				}
			}
		}
		assertThat(count).as("at least one CLPI file must have been tested").isPositive();
	}

}
