package org.brts.lowlevel.subtitle.parser;

import org.brts.lowlevel.subtitle.model.SubtitleCue;
import org.brts.lowlevel.subtitle.model.SubtitlePosition;
import org.brts.lowlevel.subtitle.model.SubtitleTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for SubStation Alpha ({@code .ssa}) and Advanced SubStation Alpha ({@code .ass}) subtitle files.
 * <p>
 * The parser extracts dialogue events from the {@code [Events]} section and maps SSA style overrides to the common
 * {@link SubtitleCue} model:
 * <ul>
 * <li>{@code \pos(x,y)} &#8594; absolute pixel position</li>
 * <li>{@code \an1} to {@code \an9} &#8594; numpad alignment override</li>
 * <li>{@code \b1} / {@code \b0} &#8594; bold</li>
 * <li>{@code \i1} / {@code \i0} &#8594; italic</li>
 * <li>SSA underline tags &#8594; underline</li>
 * <li>{@code \N} &#8594; line break</li>
 * <li>{@code \n} &#8594; soft line break (treated as line break)</li>
 * </ul>
 * Other override tags are stripped.
 */
public class SsaParser implements SubtitleParser {

	private static final Logger log = LoggerFactory.getLogger(SsaParser.class);

	private static final Pattern POS_TAG = Pattern.compile("\\\\pos\\((\\d+)[,.]\\s*(\\d+)\\)");

	private static final Pattern AN_TAG = Pattern.compile("\\\\an(\\d)");

	/** Matches SSA override blocks: { ... } */
	private static final Pattern OVERRIDE_BLOCK = Pattern.compile("\\{[^}]*}");

	/** Bold/italic/underline toggle tags. */
	private static final Pattern BOLD_ON = Pattern.compile("\\\\b1");

	private static final Pattern BOLD_OFF = Pattern.compile("\\\\b0");

	private static final Pattern ITALIC_ON = Pattern.compile("\\\\i1");

	private static final Pattern ITALIC_OFF = Pattern.compile("\\\\i0");

	private static final Pattern UNDERLINE_ON = Pattern.compile("\\\\u1");

	private static final Pattern UNDERLINE_OFF = Pattern.compile("\\\\u0");

	@Override
	public SubtitleTrack parse(Path file) throws IOException {
		String name = file.getFileName().toString().toLowerCase();
		String format = name.endsWith(".ass") ? "ASS" : "SSA";
		try (InputStream in = Files.newInputStream(file)) {
			return parse(in, format);
		}
	}

	@Override
	public SubtitleTrack parse(InputStream input, String format) throws IOException {
		SubtitleTrack track = new SubtitleTrack();
		track.setFormat(format != null ? format.toUpperCase() : "SSA");

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {

			String line;
			boolean inEvents = false;
			String[] formatFields = null;
			int textIndex = -1;
			int startIndex = -1;
			int endIndex = -1;
			int cueNumber = 0;

			while ((line = reader.readLine()) != null) {
				line = stripBom(line).trim();

				// Section headers
				if (line.startsWith("[")) {
					inEvents = line.equalsIgnoreCase("[Events]");
					continue;
				}

				if (!inEvents)
					continue;

				// Format line defines column order
				if (line.toLowerCase().startsWith("format:")) {
					String cols = line.substring("format:".length()).trim();
					formatFields = cols.split("\\s*,\\s*");
					for (int i = 0; i < formatFields.length; i++) {
						switch (formatFields[i].trim().toLowerCase()) {
						case "text" -> textIndex = i;
						case "start" -> startIndex = i;
						case "end" -> endIndex = i;
						}
					}
					continue;
				}

				// Dialogue lines
				if (!line.toLowerCase().startsWith("dialogue:"))
					continue;
				if (formatFields == null || textIndex < 0 || startIndex < 0 || endIndex < 0) {
					log.warn("Dialogue line before Format definition, skipping");
					continue;
				}

				String payload = line.substring("dialogue:".length()).trim();
				// Split only up to textIndex — the text field may contain commas
				String[] parts = payload.split(",", formatFields.length);
				if (parts.length <= textIndex) {
					log.warn("Malformed dialogue line: {}", line);
					continue;
				}

				long startMs = parseSsaTimestamp(parts[startIndex].trim());
				long endMs = parseSsaTimestamp(parts[endIndex].trim());
				String rawText = parts[textIndex].trim();

				// Process SSA text
				SubtitlePosition position = extractPosition(rawText);
				String processedText = convertSsaToHtml(rawText);

				cueNumber++;
				SubtitleCue cue = new SubtitleCue();
				cue.setNumber(cueNumber);
				cue.setStartTimeMs(startMs);
				cue.setEndTimeMs(endMs);
				cue.setText(processedText);
				cue.setPosition(position);

				track.getCues().add(cue);
			}
		}

		// Sort by start time (SSA files are not always ordered)
		track.getCues().sort(Comparator.comparingLong(SubtitleCue::getStartTimeMs));

		log.info("Parsed {}: {} cues", track.getFormat(), track.getCues().size());
		return track;
	}

	// ── SSA text processing ─────────────────────────────────────────────────

	/**
	 * Extracts an optional position from SSA override tags. Looks for {@code \pos(x,y)} first, then {@code \anN}.
	 */
	private SubtitlePosition extractPosition(String text) {
		Matcher posM = POS_TAG.matcher(text);
		if (posM.find()) {
			int x = Integer.parseInt(posM.group(1));
			int y = Integer.parseInt(posM.group(2));
			return SubtitlePosition.absolute(x, y);
		}

		Matcher anM = AN_TAG.matcher(text);
		if (anM.find()) {
			int an = Integer.parseInt(anM.group(1));
			if (an >= 1 && an <= 9) {
				return SubtitlePosition.aligned(an);
			}
		}

		return null;
	}

	/**
	 * Converts SSA-formatted text into HTML-tagged plain text:
	 * <ul>
	 * <li>{@code \b1} &#8594; bold on, {@code \b0} &#8594; bold off</li>
	 * <li>{@code \i1} &#8594; italic on, {@code \i0} &#8594; italic off</li>
	 * <li>SSA underline on/off &#8594; HTML underline</li>
	 * <li>{@code \N} and {@code \n} &#8594; newline</li>
	 * <li>All remaining override blocks are stripped</li>
	 * </ul>
	 */
	static String convertSsaToHtml(String text) {
		// Step 1: convert style toggles inside override blocks to HTML tags.
		// We do this before stripping all override blocks so the tags survive.
		text = replaceTagInOverrides(text, BOLD_ON, "<b>");
		text = replaceTagInOverrides(text, BOLD_OFF, "</b>");
		text = replaceTagInOverrides(text, ITALIC_ON, "<i>");
		text = replaceTagInOverrides(text, ITALIC_OFF, "</i>");
		text = replaceTagInOverrides(text, UNDERLINE_ON, "<u>");
		text = replaceTagInOverrides(text, UNDERLINE_OFF, "</u>");

		// Step 2: strip remaining override blocks { ... }
		text = OVERRIDE_BLOCK.matcher(text).replaceAll("");

		// Step 3: SSA line breaks
		text = text.replace("\\N", "\n");
		text = text.replace("\\n", "\n");

		return text.trim();
	}

	/**
	 * Within SSA override blocks, replaces a matched tag pattern with an HTML replacement (placed outside the block).
	 * This is a best-effort conversion: if a block contains multiple tags they are all converted.
	 */
	private static String replaceTagInOverrides(String text, Pattern tag, String html) {
		// For each override block, extract the tag if present, insert the HTML
		// replacement just after the block.
		StringBuilder sb = new StringBuilder();
		Matcher blockM = OVERRIDE_BLOCK.matcher(text);
		int last = 0;
		while (blockM.find()) {
			String block = blockM.group();
			sb.append(text, last, blockM.start());
			// Check if this block contains the tag
			Matcher tagM = tag.matcher(block);
			String cleaned = tagM.replaceAll("");
			// If tag was found, emit the HTML and the cleaned block
			if (cleaned.length() < block.length()) {
				// Only emit the block if it still has content beyond { }
				String inner = cleaned.substring(1, cleaned.length() - 1).trim();
				if (!inner.isEmpty()) {
					sb.append('{').append(inner).append('}');
				}
				sb.append(html);
			} else {
				sb.append(block);
			}
			last = blockM.end();
		}
		sb.append(text.substring(last));
		return sb.toString();
	}

	// ── Timestamp parsing ───────────────────────────────────────────────────

	/**
	 * Parses an SSA/ASS timestamp: {@code H:MM:SS.cc} (centiseconds).
	 *
	 * @param ts the timestamp string
	 * @return time in milliseconds
	 */
	static long parseSsaTimestamp(String ts) {
		// Format: H:MM:SS.CC (or H:MM:SS.CCC in some files)
		String[] parts = ts.split("[:.]");
		if (parts.length < 4) {
			throw new IllegalArgumentException("Invalid SSA timestamp: " + ts);
		}
		int h = Integer.parseInt(parts[0].trim());
		int m = Integer.parseInt(parts[1].trim());
		int s = Integer.parseInt(parts[2].trim());
		String csStr = parts[3].trim();
		long cs;
		if (csStr.length() <= 2) {
			cs = Long.parseLong(csStr);
			return ((long) h * 3600 + m * 60 + s) * 1000 + cs * 10;
		} else {
			// Treat as milliseconds
			cs = Long.parseLong(csStr);
			return ((long) h * 3600 + m * 60 + s) * 1000 + cs;
		}
	}

	private static String stripBom(String s) {
		if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
			return s.substring(1);
		}
		return s;
	}

}
