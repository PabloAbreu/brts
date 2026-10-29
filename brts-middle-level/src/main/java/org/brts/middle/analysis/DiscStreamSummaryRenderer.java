/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/main/java/org/brts/middle/analysis/DiscStreamSummaryRenderer.java' is part of BRTS.
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

package org.brts.middle.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.brts.common.m2ts.AudioChannelLayoutConverter;
import org.brts.common.m2ts.IStreamInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.AudioUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Static report-only stream visualization; never changes parser data or infers player state. */
final class DiscStreamSummaryRenderer {

	private record Track(String pid, String codec, String category, String tone, String language, String role,
			List<String> attributes, String identity) {
	}

	private record Companion(String clip, ObjectNode stream, Set<String> sources) {
	}

	private DiscStreamSummaryRenderer() {
	}

	static void append(StringBuilder html, String kind, JsonNode data, Map<String, JsonNode> companions) {
		if (!Set.of("playlist", "clipinfo", "m2ts").contains(kind))
			return;
		Map<Track, Set<String>> tracks = new LinkedHashMap<>();
		if ("playlist".equals(kind)) {
			List<Companion> metadata = companions(companions);
			Set<Companion> selected = new LinkedHashSet<>();
			int index = 0;
			for (JsonNode item : data.path("playItems")) {
				String source = "PlayItem " + (++index) + " · clip " + item.path("clipName").asText("unknown");
				for (JsonNode stream : item.path("streams")) {
					if (!stream.isObject())
						continue;
					ObjectNode enriched = canonical(stream);
					Set<String> sources = new LinkedHashSet<>();
					sources.add(source);
					String clip = streamClip(data, item, stream);
					List<Companion> matches = metadata.stream()
							.filter(c -> c.clip().equals(clip) && compatible(enriched, c.stream())).toList();
					// A sparse selection must not hide contradictory companion definitions.
					if (matches.size() == 1) {
						Companion match = matches.getFirst();
						mergeMissing(enriched, match.stream());
						sources.addAll(match.sources());
						selected.add(match);
					}
					tracks.computeIfAbsent(normalize(enriched, "Playlist-selected", true), key -> new LinkedHashSet<>())
							.addAll(sources);
				}
			}
			for (Companion companion : metadata) {
				if (!selected.contains(companion))
					tracks.computeIfAbsent(normalize(companion.stream(), "SubPath companion metadata", false),
							key -> new LinkedHashSet<>()).addAll(companion.sources());
			}
		} else
			collect(tracks, data.path("streams"), "Parsed " + kind, "", false);
		html.append(
				"<section class=\"stream-overview\"><h2>Stream overview</h2><ul class=\"stream-legend\" aria-label=\"Stream categories\">");
		for (String category : List.of("video", "audio", "subtitle", "menu")) {
			html.append("<li class=\"legend-").append(category).append("\">").append(label(category)).append("</li>");
		}
		html.append("</ul>");
		if ("playlist".equals(kind))
			html.append(
					"<p class=\"muted\">Deduplicated playlist-selected streams enriched with compatible companion metadata; sources are listed on each tile. Unselected or conflicting SubPath companion tracks are identified separately.</p>");
		if (tracks.isEmpty())
			html.append("<p class=\"muted\">No stream information available.</p>");
		for (String category : List.of("video", "audio", "subtitle", "menu", "unknown")) {
			long count = tracks.keySet().stream().filter(t -> category.equals(t.category())).count();
			if (count == 0)
				continue;
			html.append("<div class=\"stream-group\"><h3>").append(label(category)).append(" <span class=\"count\">")
					.append(count).append("</span></h3><div class=\"stream-grid\">");
			tracks.forEach((track, sources) -> {
				if (!category.equals(track.category()))
					return;
				html.append("<article class=\"stream-tile stream-").append(category).append(" tone-")
						.append(track.tone()).append("\"><p class=\"stream-category\">").append(label(category))
						.append("</p><h4>").append(esc(track.codec())).append("</h4><p class=\"stream-pid\">")
						.append(esc(track.pid())).append("</p>");
				if (!track.language().isEmpty())
					html.append("<p>Language: ").append(esc(track.language())).append("</p>");
				for (String attribute : track.attributes())
					html.append("<p>").append(esc(attribute)).append("</p>");
				if (!track.role().isEmpty())
					html.append("<p>").append(esc(track.role())).append("</p>");
				html.append("<details><summary>Sources (").append(sources.size()).append(")</summary><ul>");
				for (String source : sources)
					html.append("<li>").append(esc(source)).append("</li>");
				html.append("</ul></details></article>");
			});
			html.append("</div></div>");
		}
		if ("playlist".equals(kind) && !data.path("subPaths").isEmpty()) {
			html.append(
					"<p class=\"muted\">SubPath enrichment is limited to successfully parsed companion metadata; missing stream metadata is not inferred.</p>");
		}
		html.append("</section>");
	}

	private static List<Companion> companions(Map<String, JsonNode> companions) {
		List<Companion> result = new ArrayList<>();
		companions.forEach((source, data) -> {
			String clip = source.replaceFirst(" \\((clipinfo|m2ts)\\)$", "");
			for (JsonNode stream : data.path("streams")) {
				if (!stream.isObject())
					continue;
				ObjectNode normalized = canonical(stream);
				List<Companion> matches = result.stream()
						.filter(c -> c.clip().equals(clip) && compatible(c.stream(), normalized)).toList();
				if (matches.size() == 1) {
					mergeMissing(matches.getFirst().stream(), normalized);
					matches.getFirst().sources().add("SubPath clip " + source);
				} else {
					result.add(new Companion(clip, normalized, new LinkedHashSet<>(List.of("SubPath clip " + source))));
				}
			}
		});
		return result;
	}

	private static String streamClip(JsonNode data, JsonNode item, JsonNode stream) {
		if (stream.path("streamType").asInt(1) == 1)
			return item.path("clipName").asText("");
		JsonNode subPath = data.path("subPaths").path(stream.path("subpathId").asInt(-1));
		return subPath.path("subPlayItems").path(stream.path("subclipId").asInt(0)).path("clipName").asText("");
	}

	/** Normalize equivalent representations without inventing absent metadata. */
	private static ObjectNode canonical(JsonNode stream) {
		ObjectNode result = stream.deepCopy();
		if (!result.hasNonNull("codingType") && result.path("streamTypeByte").isIntegralNumber()) {
			StreamCodingType type = StreamCodingType.fromByte(result.path("streamTypeByte").asInt(), false);
			if (type != null)
				result.put("codingType", type.name());
		}
		String language = result.path("language").asText("").trim().toLowerCase(Locale.ROOT);
		if (language.isEmpty())
			result.remove("language");
		else
			result.put("language", language);
		if (!result.hasNonNull("sampleRate") && result.path("sampleRateHz").isIntegralNumber()) {
			int hz = result.path("sampleRateHz").asInt();
			if (hz == 48000 || hz == 96000 || hz == 192000)
				result.put("sampleRate", AudioUtils.sampleRateHzToBlurayAudioSampleRateCode(hz));
		}
		if (!result.hasNonNull("frameRateFps") && result.path("frameRate").isIntegralNumber()) {
			Double fps = IStreamInfo.fpsFromFrameRate(result.path("frameRate").asInt());
			if (fps != null)
				result.put("frameRateFps", fps);
		}
		if (result.path("frameRateFps").isNumber())
			result.put("frameRateFps", result.path("frameRateFps").asDouble());
		return result;
	}

	private static boolean compatible(ObjectNode left, ObjectNode right) {
		if (!left.path("pid").isIntegralNumber() || !left.path("pid").equals(right.path("pid"))
				|| !left.hasNonNull("codingType") || !left.path("codingType").equals(right.path("codingType")))
			return false;
		if (!compatibleChannelLayout(left, right) || !compatibleChannelLayout(right, left))
			return false;
		for (String field : List.of("language", "videoFormat", "frameRate", "frameRateFps", "aspectRatio",
				"dynamicRangeType", "colorSpace", "hdrPlusFlag", "ocFlag", "crFlag", "characterCode", "channels",
				"audioChannelLayout", "sampleRate", "sampleRateHz", "streamTypeByte", "registration")) {
			if (left.hasNonNull(field) && right.hasNonNull(field) && !left.get(field).equals(right.get(field)))
				return false;
		}
		return true;
	}

	private static boolean compatibleChannelLayout(ObjectNode layout, ObjectNode channels) {
		if (!layout.path("audioChannelLayout").isIntegralNumber() || !channels.path("channels").isIntegralNumber())
			return true;
		int count = channels.path("channels").asInt();
		// Blu-ray layouts are categories, not exact counts. In particular, the ATSC
		// AC-3 descriptor counts full-bandwidth channels only and does not signal LFE.
		return count > 0
				&& layout.path("audioChannelLayout").asInt() == AudioChannelLayoutConverter.channelsToLayout(count);
	}

	private static void mergeMissing(ObjectNode target, ObjectNode source) {
		source.fields().forEachRemaining(field -> {
			if (!target.hasNonNull(field.getKey()) && !field.getValue().isNull())
				target.set(field.getKey(), field.getValue());
		});
	}

	private static void collect(Map<Track, Set<String>> tracks, JsonNode streams, String source, String role,
			boolean playlist) {
		for (JsonNode stream : streams) {
			if (!stream.isObject())
				continue;
			Track track = normalize(stream, role, playlist);
			tracks.computeIfAbsent(track, key -> new LinkedHashSet<>()).add(source);
		}
	}

	private static Track normalize(JsonNode stream, String role, boolean playlist) {
		StreamCodingType type = null;
		String raw = stream.path("codingType").asText("");
		try {
			type = StreamCodingType.valueOf(raw);
		} catch (IllegalArgumentException ignored) {
			if (stream.path("streamTypeByte").isIntegralNumber())
				type = StreamCodingType.fromByte(stream.path("streamTypeByte").asInt(), false);
		}
		String category = type == null ? "unknown"
				: type.isVideo() ? "video"
						: type.isAudio() ? "audio"
								: type.isSubtitle() ? "subtitle" : type.isMenu() ? "menu" : "unknown";
		String codec = type == null ? (raw.isEmpty() ? "Unknown codec" : raw) : codecLabel(type);
		String tone = type == null ? "unknown" : type.name().toLowerCase(Locale.ROOT).replace('_', '-');
		String pid = stream.path("pid").isIntegralNumber()
				? String.format(Locale.ROOT, "PID %d · 0x%04X", stream.path("pid").asInt(), stream.path("pid").asInt())
				: "PID unknown";
		String language = stream.path("language").asText("").trim().toLowerCase(Locale.ROOT);
		List<String> attributes = new ArrayList<>();
		M2tsStreamInfo video = new M2tsStreamInfo();
		if (stream.path("videoFormat").isIntegralNumber())
			video.setVideoFormat(stream.path("videoFormat").asInt());
		if (stream.path("frameRate").isIntegralNumber())
			video.setFrameRate(stream.path("frameRate").asInt());
		if (stream.path("frameRateFps").isNumber())
			video.setFrameRateFps(stream.path("frameRateFps").asDouble());
		if (video.getVideoFormatInfo() != null)
			attributes.add(video.getVideoFormatInfo());
		if (video.getFrameRateFps() != null)
			attributes.add(video.getFrameRateFps() + " fps");
		if (stream.path("audioChannelLayout").isIntegralNumber())
			attributes.add(AudioChannelLayoutConverter.layoutDisplayName(stream.path("audioChannelLayout").asInt()));
		else if (stream.path("channels").isIntegralNumber())
			attributes.add(AudioChannelLayoutConverter.channelsDisplayName(stream.path("channels").asInt()));
		if (stream.path("sampleRateHz").isIntegralNumber())
			attributes.add(stream.path("sampleRateHz").asInt() + " Hz");
		else if (stream.path("sampleRate").isIntegralNumber())
			attributes.add("Sample rate code " + stream.path("sampleRate").asInt());
		if (stream.path("bitrateKbps").isNumber())
			attributes.add(stream.path("bitrateKbps").asText() + " kb/s");
		if (playlist) {
			int routing = stream.path("streamType").asInt(1);
			role += routing == 1 ? " · in-mux"
					: " · stream type " + routing + " · SubPath " + stream.path("subpathId").asText("?") + " / subclip "
							+ stream.path("subclipId").asText("?");
		}
		// Preserve significant attributes not shown as primary labels; never merge conflicting format/HDR metadata.
		StringBuilder identity = new StringBuilder();
		for (String field : List.of("aspectRatio", "dynamicRangeType", "colorSpace", "hdrPlusFlag", "ocFlag", "crFlag",
				"characterCode", "streamTypeByte", "registration", "videoFormat", "frameRate", "sampleRate")) {
			if (stream.hasNonNull(field))
				identity.append(field).append('=').append(stream.get(field)).append(';');
		}
		return new Track(pid, codec, category, tone, language, role, List.copyOf(attributes), identity.toString());
	}

	private static String codecLabel(StreamCodingType type) {
		return switch (type) {
		case MPEG2_VIDEO -> "MPEG-2";
		case H264_AVC -> "H.264 / AVC";
		case H265_HEVC -> "H.265 / HEVC";
		case VC1 -> "VC-1";
		case LPCM -> "LPCM";
		case DOLBY_AC3 -> "Dolby AC-3";
		case DOLBY_AC3_PLUS -> "Dolby Digital Plus / E-AC-3";
		case DOLBY_TRUEHD -> "Dolby TrueHD";
		case DTS -> "DTS";
		case DTS_HD -> "DTS-HD";
		case DTS_HD_MASTER_AUDIO -> "DTS-HD Master Audio";
		case DTS_EXPRESS -> "DTS Express";
		case PRESENTATION_GRAPHICS -> "PGS";
		case TEXT_SUBTITLE -> "Text subtitles";
		case INTERACTIVE_GRAPHICS -> "Interactive graphics / IG";
		};
	}

	private static String label(String category) {
		return switch (category) {
		case "video" -> "Video";
		case "audio" -> "Audio";
		case "subtitle" -> "Subtitles";
		case "menu" -> "Menus";
		default -> "Unknown";
		};
	}

	private static String esc(String value) {
		return DiscAnalyzer.escape(value);
	}
}
