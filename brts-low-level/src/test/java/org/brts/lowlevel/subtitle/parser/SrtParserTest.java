package org.brts.lowlevel.subtitle.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/subtitle/parser/SrtParserTest.java' is part of BRTS.
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

import org.brts.lowlevel.subtitle.model.SubtitleCue;
import org.brts.lowlevel.subtitle.model.SubtitleTrack;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SrtParserTest {

	private final SrtParser parser = new SrtParser();

	private SubtitleTrack parse(String srtContent) throws Exception {
		InputStream in = new ByteArrayInputStream(srtContent.getBytes(StandardCharsets.UTF_8));
		return parser.parse(in, "SRT");
	}

	@Test
	void parseBasicSrt() throws Exception {
		String srt = """
				1
				00:00:01,000 --> 00:00:04,000
				Hello, world!

				2
				00:00:05,500 --> 00:00:08,200
				Second subtitle
				with two lines
				""";

		SubtitleTrack track = parse(srt);

		assertEquals("SRT", track.getFormat());
		assertEquals(2, track.getCues().size());

		SubtitleCue cue1 = track.getCues().get(0);
		assertEquals(1, cue1.getNumber());
		assertEquals(1000, cue1.getStartTimeMs());
		assertEquals(4000, cue1.getEndTimeMs());
		assertEquals("Hello, world!", cue1.getText());
		assertNull(cue1.getPosition());

		SubtitleCue cue2 = track.getCues().get(1);
		assertEquals(2, cue2.getNumber());
		assertEquals(5500, cue2.getStartTimeMs());
		assertEquals(8200, cue2.getEndTimeMs());
		assertEquals("Second subtitle\nwith two lines", cue2.getText());
	}

	@Test
	void parsePreservesHtmlTags() throws Exception {
		String srt = """
				1
				00:00:01,000 --> 00:00:03,000
				This is <b>bold</b> and <i>italic</i>
				""";

		SubtitleTrack track = parse(srt);
		assertEquals(1, track.getCues().size());
		assertEquals("This is <b>bold</b> and <i>italic</i>", track.getCues().get(0).getText());
	}

	@Test
	void parseAnTag() throws Exception {
		String srt = """
				1
				00:00:01,000 --> 00:00:03,000
				{\\an8}Top subtitle
				""";

		SubtitleTrack track = parse(srt);
		SubtitleCue cue = track.getCues().get(0);
		assertNotNull(cue.getPosition());
		assertEquals(8, cue.getPosition().getAlignment());
		assertFalse(cue.getPosition().isAbsolutePosition());
		assertEquals("Top subtitle", cue.getText());
	}

	@Test
	void parsePeriodSeparator() throws Exception {
		// Some SRT files use period instead of comma
		String srt = """
				1
				00:01:02.345 --> 00:01:05.678
				Period separator
				""";

		SubtitleTrack track = parse(srt);
		assertEquals(1, track.getCues().size());
		SubtitleCue cue = track.getCues().get(0);
		assertEquals(62345, cue.getStartTimeMs());
		assertEquals(65678, cue.getEndTimeMs());
	}

	@Test
	void parseTimestampHours() throws Exception {
		String srt = """
				1
				01:30:00,000 --> 01:30:05,000
				One hour thirty
				""";

		SubtitleTrack track = parse(srt);
		assertEquals(5400000, track.getCues().get(0).getStartTimeMs());
		assertEquals(5405000, track.getCues().get(0).getEndTimeMs());
	}

	@Test
	void parseEmptyInput() throws Exception {
		SubtitleTrack track = parse("");
		assertEquals(0, track.getCues().size());
	}

	@Test
	void parseBom() throws Exception {
		String srt = "\uFEFF1\n00:00:01,000 --> 00:00:02,000\nBOM test\n";
		SubtitleTrack track = parse(srt);
		assertEquals(1, track.getCues().size());
		assertEquals("BOM test", track.getCues().get(0).getText());
	}

}
