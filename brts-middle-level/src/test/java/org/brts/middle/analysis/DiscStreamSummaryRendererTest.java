/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/test/java/org/brts/middle/analysis/DiscStreamSummaryRendererTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.brts.common.json.JsonMapperFactory;
import org.junit.jupiter.api.Test;

class DiscStreamSummaryRendererTest {

	@Test
	void streamTilesClassifyCodecsAndDisplayAvailableMetadata() throws Exception {
		String html = render("m2ts", """
				{"streams":[
				{"pid":4113,"codingType":"H265_HEVC","videoFormat":8,"frameRate":1},
				{"pid":4114,"codingType":"H264_AVC"},
				{"pid":4352,"codingType":"DTS","language":"ENG","channels":6,"sampleRateHz":48000},
				{"pid":4353,"codingType":"DOLBY_AC3","audioChannelLayout":3,"sampleRate":1},
				{"pid":4608,"codingType":"PRESENTATION_GRAPHICS","language":"fra"},
				{"pid":5120,"codingType":"INTERACTIVE_GRAPHICS"},
				{"pid":5121,"codingType":"TEXT_SUBTITLE"},
				{"pid":123,"codingType":"<new-codec>","language":"<script>"},
				{"pid":124,"streamTypeByte":27}]}
				""");
		assertThat(html).contains("stream-video tone-h265-hevc", "stream-video tone-h264-avc", "stream-audio tone-dts",
				"stream-audio tone-dolby-ac3", "stream-subtitle", "stream-menu", "stream-unknown", "2160p",
				"23.976 fps", "PID 4113 · 0x1011", "Language: eng", "5.1", "48000 Hz", "Sample rate code 1",
				"&lt;new-codec&gt;", "&lt;script&gt;").doesNotContain("<script>", "null");
	}

	@Test
	void playlistDeduplicationRetainsDifferentLanguagesCodecsFormatsAndRouting() throws Exception {
		String html = render("playlist", """
				{"playItems":[
				{"clipName":"00001","streams":[
				{"pid":4352,"codingType":"DTS","language":"ENG","streamType":1},
				{"pid":4352,"codingType":"DTS","language":"fra","streamType":1},
				{"pid":4352,"codingType":"DOLBY_AC3","language":"eng","streamType":1},
				{"pid":4352,"codingType":"DTS","language":"eng","streamType":2,"subpathId":0,"subclipId":0},
				{"pid":4113,"codingType":"H264_AVC","videoFormat":6},
				{"pid":4113,"codingType":"H264_AVC","videoFormat":8}]},
				{"clipName":"00002","streams":[{"pid":4352,"codingType":"DTS","language":"eng","streamType":1}]}]}
				""");
		assertThat(html.split("<article class=\"stream-tile", -1)).hasSize(7);
		assertThat(html).contains("Sources (2)", "PlayItem 1 · clip 00001", "PlayItem 2 · clip 00002", "1080p", "2160p",
				"SubPath 0 / subclip 0");
	}

	@Test
	void selectedVideoMergesClipAndM2tsMetadataWithoutChangingParsedData() throws Exception {
		var mapper = JsonMapperFactory.get();
		var playlist = mapper.readTree("""
				{"playItems":[{"clipName":"00005","streams":[
				{"pid":4113,"codingType":"H265_HEVC","videoFormat":8,"frameRate":1},
				{"pid":4117,"codingType":"H265_HEVC","videoFormat":6,"frameRate":1,
				 "streamType":4,"subpathId":0,"subclipId":0}]}],
				"subPaths":[{"subPlayItems":[{"clipName":"00005"}]}]}
				""");
		var clip = mapper.readTree("""
				{"streams":[
				{"pid":4113,"codingType":"H265_HEVC","videoFormat":8,"frameRate":1,
				 "aspectRatio":3,"dynamicRangeType":1,"colorSpace":2,"hdrPlusFlag":false},
				{"pid":4117,"codingType":"H265_HEVC","videoFormat":6,"frameRate":1}]}
				""");
		var m2ts = mapper.readTree("""
				{"streams":[
				{"pid":4113,"codingType":"H265_HEVC","streamTypeByte":36,"registration":"HDMV",
				 "videoFormat":8,"frameRateFps":23.976},
				{"pid":4117,"codingType":"H265_HEVC","streamTypeByte":36,"videoFormat":6,"frameRate":1}]}
				""");
		var originalPlaylist = playlist.deepCopy();
		var originalClip = clip.deepCopy();
		var originalM2ts = m2ts.deepCopy();
		for (boolean reverse : new boolean[] { false, true }) {
			Map<String, com.fasterxml.jackson.databind.JsonNode> companions = new LinkedHashMap<>();
			if (reverse) {
				companions.put("00005 (m2ts)", m2ts);
				companions.put("00005 (clipinfo)", clip);
			} else {
				companions.put("00005 (clipinfo)", clip);
				companions.put("00005 (m2ts)", m2ts);
			}
			StringBuilder html = new StringBuilder();
			DiscStreamSummaryRenderer.append(html, "playlist", playlist, companions);
			assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(3);
			assertThat(html.toString()).contains("Sources (3)", "SubPath clip 00005 (clipinfo)",
					"SubPath clip 00005 (m2ts)", "2160p", "1080p", "SubPath 0 / subclip 0")
					.doesNotContain("<p>SubPath companion metadata</p>");
		}
		assertThat(playlist).isEqualTo(originalPlaylist);
		assertThat(clip).isEqualTo(originalClip);
		assertThat(m2ts).isEqualTo(originalM2ts);
	}

	@Test
	void audioRepresentationsMergeAndUnselectedTracksRemainVisible() throws Exception {
		var mapper = JsonMapperFactory.get();
		StringBuilder html = new StringBuilder();
		DiscStreamSummaryRenderer.append(html, "playlist", mapper.readTree("""
				{"playItems":[{"clipName":"00001","streams":[
				{"pid":4352,"codingType":"DTS","language":"ENG","audioChannelLayout":6,"sampleRate":1}]}]}
				"""), Map.of("00001 (m2ts)", mapper.readTree("""
				{"streams":[{"pid":4352,"codingType":"DTS","language":null,"channels":6,
				"sampleRateHz":48000,"bitrateKbps":1509},{"pid":5120,"codingType":"INTERACTIVE_GRAPHICS"}]}
				""")));
		assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(3);
		assertThat(html.toString()).contains("Sources (2)", "Language: eng", "48000 Hz", "1509 kb/s", "5.1",
				"SubPath companion metadata");
	}

	@Test
	void ac3FullBandwidthChannelsMergeWithMultichannelLayoutsInEitherSourceOrder() throws Exception {
		var mapper = JsonMapperFactory.get();
		var playlist = mapper.readTree("""
				{"playItems":[{"clipName":"00005","streams":[
				{"pid":4352,"codingType":"DOLBY_TRUEHD","language":"eng","audioChannelLayout":6,"sampleRate":1},
				{"pid":4354,"codingType":"DOLBY_AC3","language":"spa","audioChannelLayout":6,"sampleRate":1}]}]}
				""");
		var clip = mapper.readTree("""
				{"streams":[
				{"pid":4352,"codingType":"DOLBY_TRUEHD","language":"eng","audioChannelLayout":6,"sampleRate":1},
				{"pid":4354,"codingType":"DOLBY_AC3","language":"spa","audioChannelLayout":6,"sampleRate":1}]}
				""");
		var m2ts = mapper.readTree("""
				{"streams":[
				{"pid":4352,"codingType":"DOLBY_TRUEHD","streamTypeByte":131,"registration":"AC-3",
				"channels":5,"sampleRateHz":48000,"bitrateKbps":640},
				{"pid":4354,"codingType":"DOLBY_AC3","streamTypeByte":129,"registration":"AC-3",
				"channels":5,"sampleRateHz":48000,"bitrateKbps":640}]}
				""");
		var originalPlaylist = playlist.deepCopy();
		var originalClip = clip.deepCopy();
		var originalM2ts = m2ts.deepCopy();
		for (boolean reverse : new boolean[] { false, true }) {
			Map<String, com.fasterxml.jackson.databind.JsonNode> companions = new LinkedHashMap<>();
			companions.put(reverse ? "00005 (m2ts)" : "00005 (clipinfo)", reverse ? m2ts : clip);
			companions.put(reverse ? "00005 (clipinfo)" : "00005 (m2ts)", reverse ? clip : m2ts);
			StringBuilder html = new StringBuilder();
			DiscStreamSummaryRenderer.append(html, "playlist", playlist, companions);
			assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(3);
			assertThat(html.toString().split("Sources \\(3\\)", -1)).hasSize(3);
			assertThat(html.toString()).contains("Language: eng", "Language: spa", "5.1", "48000 Hz", "640 kb/s")
					.doesNotContain("5ch", "<p>SubPath companion metadata</p>");
		}
		assertThat(playlist).isEqualTo(originalPlaylist);
		assertThat(clip).isEqualTo(originalClip);
		assertThat(m2ts).isEqualTo(originalM2ts);
	}

	@Test
	void incompatibleAudioLayoutsCountsAndSampleRatesRemainSeparate() throws Exception {
		var mapper = JsonMapperFactory.get();
		for (String mismatch : new String[] { "\"channels\":2,\"sampleRateHz\":48000",
				"\"channels\":8,\"sampleRateHz\":48000", "\"channels\":5,\"sampleRateHz\":96000",
				"\"audioChannelLayout\":3,\"sampleRate\":1" }) {
			StringBuilder html = new StringBuilder();
			DiscStreamSummaryRenderer.append(html, "playlist", mapper.readTree("""
					{"playItems":[{"clipName":"00005","streams":[
					{"pid":4354,"codingType":"DOLBY_AC3","audioChannelLayout":6,"sampleRate":1}]}]}
					"""), Map.of("00005 (m2ts)",
					mapper.readTree("{\"streams\":[{\"pid\":4354,\"codingType\":\"DOLBY_AC3\"," + mismatch + "}]}")));
			assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(3);
			assertThat(html.toString()).doesNotContain("Sources (2)");
		}
		StringBuilder html = new StringBuilder();
		DiscStreamSummaryRenderer.append(html, "playlist", mapper.readTree("""
				{"playItems":[{"clipName":"00005","streams":[
				{"pid":4354,"codingType":"DOLBY_AC3","channels":6}]}]}
				"""), Map.of("00005 (m2ts)", mapper.readTree("""
				{"streams":[{"pid":4354,"codingType":"DOLBY_AC3","channels":5}]}
				""")));
		assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(3);
		assertThat(html.toString()).doesNotContain("Sources (2)");
	}

	@Test
	void conflictingMetadataAndUnrelatedClipsAreNotMerged() throws Exception {
		var mapper = JsonMapperFactory.get();
		for (String conflicting : new String[] { "\"videoFormat\":6", "\"dynamicRangeType\":2" }) {
			StringBuilder html = new StringBuilder();
			DiscStreamSummaryRenderer.append(html, "playlist", mapper.readTree("""
					{"playItems":[{"clipName":"00001","streams":[
					{"pid":4113,"codingType":"H265_HEVC","videoFormat":8,"dynamicRangeType":1}]}]}
					"""),
					Map.of("00001 (clipinfo)",
							mapper.readTree(
									"{\"streams\":[{\"pid\":4113,\"codingType\":\"H265_HEVC\"," + conflicting + "}]}"),
							"00002 (m2ts)", mapper.readTree("""
									{"streams":[{"pid":4113,"codingType":"H265_HEVC","videoFormat":8}]}
									""")));
			assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(4);
			assertThat(html.toString()).doesNotContain("Sources (2)");
		}
	}

	@Test
	void subpathRoutingUsesTheCompanionClipRatherThanTheMainClip() throws Exception {
		var mapper = JsonMapperFactory.get();
		StringBuilder html = new StringBuilder();
		DiscStreamSummaryRenderer.append(html, "playlist", mapper.readTree("""
				{"playItems":[{"clipName":"00001","streams":[
				{"pid":4113,"codingType":"H264_AVC","streamType":2,"subpathId":0,"subclipId":0}]}],
				"subPaths":[{"subPlayItems":[{"clipName":"00002"}]}]}
				"""), Map.of("00001 (clipinfo)", mapper.readTree("""
				{"streams":[{"pid":4113,"codingType":"H264_AVC","videoFormat":8}]}
				"""), "00002 (m2ts)", mapper.readTree("""
				{"streams":[{"pid":4113,"codingType":"H264_AVC","videoFormat":6}]}
				""")));
		String selected = html.substring(html.indexOf("<article"), html.indexOf("</article>"));
		assertThat(selected).contains("1080p", "Sources (2)", "SubPath clip 00002 (m2ts)").doesNotContain("2160p",
				"SubPath clip 00001 (clipinfo)");
		assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(3);
	}

	@Test
	void sparseSelectionsDoNotHideConflictingCompanionDefinitions() throws Exception {
		var mapper = JsonMapperFactory.get();
		StringBuilder html = new StringBuilder();
		DiscStreamSummaryRenderer.append(html, "playlist", mapper.readTree("""
				{"playItems":[{"clipName":"00001","streams":[{"pid":4113,"codingType":"H265_HEVC"}]}]}
				"""), Map.of("00001 (clipinfo)", mapper.readTree("""
				{"streams":[{"pid":4113,"codingType":"H265_HEVC","videoFormat":8}]}
				"""), "00001 (m2ts)", mapper.readTree("""
				{"streams":[{"pid":4113,"codingType":"H265_HEVC","videoFormat":6}]}
				""")));
		assertThat(html.toString().split("<article class=\"stream-tile", -1)).hasSize(4);
		assertThat(html.toString()).contains("2160p", "1080p").doesNotContain("Sources (2)");
	}

	@Test
	void subpathCompanionsAreExplicitlyIdentifiedAndEscaped() throws Exception {
		var mapper = JsonMapperFactory.get();
		StringBuilder html = new StringBuilder();
		DiscStreamSummaryRenderer.append(html, "playlist", mapper.readTree("{\"subPaths\":[{}]}"), Map.of("<unsafe>",
				mapper.readTree("{\"streams\":[{\"codingType\":\"INTERACTIVE_GRAPHICS\",\"pid\":5120}]}")));
		assertThat(html.toString()).contains("stream-menu", "SubPath companion metadata", "SubPath clip &lt;unsafe&gt;",
				"missing stream metadata is not inferred");
	}

	@Test
	void emptyAndUnknownStreamsAreSafe() throws Exception {
		assertThat(render("clipinfo", "{}")).contains("No stream information available");
		assertThat(render("clipinfo", "{\"streams\":[{}]}")).contains("Unknown codec", "PID unknown")
				.doesNotContain("null");
		assertThat(render("index", "{}")).isEmpty();
	}

	private static String render(String kind, String json) throws Exception {
		StringBuilder html = new StringBuilder();
		DiscStreamSummaryRenderer.append(html, kind, JsonMapperFactory.get().readTree(json), Map.of());
		return html.toString();
	}
}
