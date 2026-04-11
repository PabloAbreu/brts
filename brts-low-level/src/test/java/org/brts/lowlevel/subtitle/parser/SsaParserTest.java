package org.brts.lowlevel.subtitle.parser;

import org.brts.lowlevel.subtitle.model.SubtitleCue;
import org.brts.lowlevel.subtitle.model.SubtitleTrack;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SsaParserTest {

	private final SsaParser parser = new SsaParser();

	private SubtitleTrack parse(String content) throws Exception {
		InputStream in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
		return parser.parse(in, "ASS");
	}

	@Test
	void parseBasicAss() throws Exception {
		String ass = """
				[Script Info]
				Title: Test

				[Events]
				Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
				Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello world
				Dialogue: 0,0:00:05.50,0:00:08.20,Default,,0,0,0,,Second line
				""";

		SubtitleTrack track = parse(ass);
		assertEquals("ASS", track.getFormat());
		assertEquals(2, track.getCues().size());

		SubtitleCue cue1 = track.getCues().get(0);
		assertEquals(1000, cue1.getStartTimeMs());
		assertEquals(4000, cue1.getEndTimeMs());
		assertEquals("Hello world", cue1.getText());

		SubtitleCue cue2 = track.getCues().get(1);
		assertEquals(5500, cue2.getStartTimeMs());
		assertEquals(8200, cue2.getEndTimeMs());
		assertEquals("Second line", cue2.getText());
	}

	@Test
	void parseLineBreaks() throws Exception {
		String ass = """
				[Events]
				Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
				Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,First\\NSecond
				""";

		SubtitleTrack track = parse(ass);
		assertEquals("First\nSecond", track.getCues().get(0).getText());
	}

	@Test
	void parsePositionTag() throws Exception {
		String ass = """
				[Events]
				Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
				Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\\pos(320,240)}Positioned text
				""";

		SubtitleTrack track = parse(ass);
		SubtitleCue cue = track.getCues().get(0);
		assertNotNull(cue.getPosition());
		assertTrue(cue.getPosition().isAbsolutePosition());
		assertEquals(320, cue.getPosition().getX());
		assertEquals(240, cue.getPosition().getY());
		assertEquals("Positioned text", cue.getText());
	}

	@Test
	void parseAlignmentTag() throws Exception {
		String ass = """
				[Events]
				Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
				Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\\an8}Top centered
				""";

		SubtitleTrack track = parse(ass);
		SubtitleCue cue = track.getCues().get(0);
		assertNotNull(cue.getPosition());
		assertFalse(cue.getPosition().isAbsolutePosition());
		assertEquals(8, cue.getPosition().getAlignment());
		assertEquals("Top centered", cue.getText());
	}

	@Test
	void parseBoldItalicConversion() throws Exception {
		String ass = """
				[Events]
				Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
				Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Normal {\\b1}bold{\\b0} normal
				""";

		SubtitleTrack track = parse(ass);
		assertEquals("Normal <b>bold</b> normal", track.getCues().get(0).getText());
	}

	@Test
	void parseTimestamp() {
		assertEquals(1000, SsaParser.parseSsaTimestamp("0:00:01.00"));
		assertEquals(62340, SsaParser.parseSsaTimestamp("0:01:02.34"));
		assertEquals(3661230, SsaParser.parseSsaTimestamp("1:01:01.23"));
	}

	@Test
	void convertSsaToHtml() {
		assertEquals("Normal <b>bold</b> end", SsaParser.convertSsaToHtml("Normal {\\b1}bold{\\b0} end"));

		assertEquals("before <i>italic</i> after", SsaParser.convertSsaToHtml("before {\\i1}italic{\\i0} after"));

		assertEquals("line1\nline2", SsaParser.convertSsaToHtml("line1\\Nline2"));
	}

	@Test
	void stripsUnknownOverrides() {
		assertEquals("Clean text", SsaParser.convertSsaToHtml("{\\fad(500,200)}Clean text"));
	}

	@Test
	void parseTextWithCommas() throws Exception {
		// Text field can contain commas — should not be split
		String ass = """
				[Events]
				Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
				Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,Hello, world, test
				""";

		SubtitleTrack track = parse(ass);
		assertEquals("Hello, world, test", track.getCues().get(0).getText());
	}

	@Test
	void parseEmptyInput() throws Exception {
		SubtitleTrack track = parse("");
		assertEquals(0, track.getCues().size());
	}

}
