package org.brts.lowlevel.subtitle.parser;

import org.brts.lowlevel.subtitle.model.SubtitleCue;
import org.brts.lowlevel.subtitle.model.SubtitlePosition;
import org.brts.lowlevel.subtitle.model.SubtitleTrack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Parser for SubRip ({@code .srt}) subtitle files.
 * <p>
 * Handles the standard SRT format:
 *
 * <pre>
 *   1
 *   00:00:01,000 --> 00:00:04,000
 *   First subtitle line
 *   Second line (optional)
 *
 *   2
 *   00:00:05,500 --> 00:00:08,200
 *   Next subtitle
 * </pre>
 *
 * Basic HTML tags ({@code <b>}, {@code <i>}, {@code <u>}) are preserved in the cue text for the renderer to interpret.
 * <p>
 * The SSA-style {@code {\anN}} position tag (sometimes found in SRT files) is recognised and converted to a
 * {@link SubtitlePosition}.
 */
@Slf4j
public class SrtParser implements SubtitleParser {

	/**
	 * SRT timestamp format: {@code HH:MM:SS,mmm}
	 */
	private static final Pattern TIMESTAMP_LINE = Pattern.compile(
			"(\\d{1,2}):(\\d{2}):(\\d{2})[,.]([0-9]{1,3})\\s*-->\\s*" + "(\\d{1,2}):(\\d{2}):(\\d{2})[,.]([0-9]{1,3})");

	/**
	 * SSA-style position override tag occasionally embedded in SRT files.
	 */
	private static final Pattern AN_TAG = Pattern.compile("\\{\\\\an(\\d)}");

	@Override
	public SubtitleTrack parse(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return parse(in, "SRT");
		}
	}

	@Override
	public SubtitleTrack parse(InputStream input, String format) throws IOException {
		SubtitleTrack track = new SubtitleTrack();
		track.setFormat("SRT");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {

			String line;
			int cueNumber = 0;

			while ((line = reader.readLine()) != null) {
				line = stripBom(line).trim();

				// Skip blank lines between cues
				if (line.isEmpty())
					continue;

				// Try to read a cue number (integer line)
				if (isInteger(line)) {
					cueNumber = Integer.parseInt(line);
					// Next line should be the timestamp
					line = reader.readLine();
					if (line == null)
						break;
					line = line.trim();
				}

				// Parse timestamp line
				Matcher m = TIMESTAMP_LINE.matcher(line);
				if (!m.find()) {
					// Not a timestamp line — skip
					continue;
				}

				long startMs = parseTimestamp(m, 1);
				long endMs = parseTimestamp(m, 5);

				// Read text lines until blank line or EOF
				StringBuilder text = new StringBuilder();
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty())
						break;
					if (!text.isEmpty())
						text.append('\n');
					text.append(line);
				}

				SubtitleCue cue = new SubtitleCue();
				cue.setNumber(cueNumber > 0 ? cueNumber : track.getCues().size() + 1);
				cue.setStartTimeMs(startMs);
				cue.setEndTimeMs(endMs);

				// Extract optional \an positioning tag
				String rawText = text.toString();
				Matcher anMatcher = AN_TAG.matcher(rawText);
				if (anMatcher.find()) {
					int alignment = Integer.parseInt(anMatcher.group(1));
					cue.setPosition(SubtitlePosition.aligned(alignment));
					rawText = AN_TAG.matcher(rawText).replaceAll("").trim();
				}

				cue.setText(rawText);
				track.getCues().add(cue);
			}
		}

		log.info("Parsed SRT: {} cues", track.getCues().size());
		return track;
	}

	// ── Helpers ─────────────────────────────────────────────────────────────

	/**
	 * Parses HH:MM:SS,mmm groups into milliseconds.
	 *
	 * @param m          the matcher positioned at the timestamp
	 * @param groupStart the group index of the first component (hours)
	 * @return time in milliseconds
	 */
	private static long parseTimestamp(Matcher m, int groupStart) {
		int h = Integer.parseInt(m.group(groupStart));
		int min = Integer.parseInt(m.group(groupStart + 1));
		int sec = Integer.parseInt(m.group(groupStart + 2));
		String msStr = m.group(groupStart + 3);
		// Pad to 3 digits if needed (e.g. "5" → "500", "50" → "500")
		while (msStr.length() < 3)
			msStr += "0";
		int ms = Integer.parseInt(msStr);
		return ((long) h * 3600 + min * 60 + sec) * 1000 + ms;
	}

	private static boolean isInteger(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Strips UTF-8 BOM if present.
	 */
	private static String stripBom(String s) {
		if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
			return s.substring(1);
		}
		return s;
	}

}
