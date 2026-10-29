/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-middle-level/src/test/java/org/brts/middle/analysis/DiscAnalyzerTest.java' is part of BRTS.
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.brts.lowlevel.bdmv.NavigationCommandMnemonic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscAnalyzerTest {

	@TempDir
	Path tempDir;

	private final DiscAnalyzer analyzer = new DiscAnalyzer();

	@Test
	void prepareCreatesOrderedBatchAndMirroredOutputFiles() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("report");

		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		BatchRunDescriptor descriptor = JsonMapperFactory.get().readValue(plan.descriptor().toFile(),
				BatchRunDescriptor.class);

		assertThat(descriptor.getSteps()).extracting(step -> step.getCommand()).containsExactly("index-parse",
				"mobj-parse", "playlist-parse", "clip-parse", "m2ts-info");
		assertThat(descriptor.getSteps().get(0).toCommandTokens()).contains("--input",
				disc.resolve("BDMV/index.bdmv").toAbsolutePath().toString(), "--output",
				output.resolve("BDMV/index.json").toAbsolutePath().toString());
		assertThat(plan.files()).extracting(file -> output.relativize(file.json()).toString().replace('\\', '/'))
				.containsExactly("BDMV/index.json", "BDMV/MovieObject.json", "BDMV/PLAYLIST/00001.json",
						"BDMV/CLIPINF/00002.json", "BDMV/STREAM/00002.json");
		assertThat(output.resolve(DiscAnalyzer.STYLESHEET_NAME)).exists();
		assertThat(plan.descriptor()).exists();
	}

	@Test
	void prepareRejectsNonEmptyOutput() throws Exception {
		Path disc = createDisc();
		Path output = Files.createDirectory(tempDir.resolve("existing"));
		Files.writeString(output.resolve("keep.txt"), "keep");

		assertThatThrownBy(() -> analyzer.prepare(disc, output)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must be empty");
		assertThat(output.resolve("keep.txt")).hasContent("keep");
	}

	@Test
	void htmlPagesContainCrossLinksAndEscapeParsedValues() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("report");
		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		Files.writeString(output.resolve("BDMV/index.json"), """
				{"titles":[{"objectType":1,"hdmvObjectId":0},{"objectType":2,"bdjObjectName":"APP01"}]}
				""");
		Files.writeString(output.resolve("BDMV/MovieObject.json"), """
				{"movieObjects":[{"navigationCommands":[{"mnemonic":"PLAY_PL","operand1":1}]}]}
				""");
		Files.writeString(output.resolve("BDMV/PLAYLIST/00001.json"), """
				{"playItems":[{"clipName":"00002"}]}
				""");
		Files.writeString(output.resolve("BDMV/CLIPINF/00002.json"), "{\"clipName\":\"00002\"}");
		Files.writeString(output.resolve("BDMV/STREAM/00002.json"), "{\"sourcePath\":\"<unsafe>\"}");

		analyzer.render(plan, List.of(), false);

		assertThat(Files.readString(output.resolve("index.html"))).contains("BDMV/index.html");
		assertThat(Files.readString(output.resolve("BDMV/index.html"))).contains("MovieObject.html#object-0",
				"BD-J object (not parsed)");
		assertThat(Files.readString(output.resolve("BDMV/MovieObject.html"))).contains("PLAYLIST/00001.html");
		assertThat(Files.readString(output.resolve("BDMV/PLAYLIST/00001.html"))).contains("../CLIPINF/00002.html",
				"../STREAM/00002.html");
		assertThat(Files.readString(output.resolve("BDMV/STREAM/00002.html"))).contains("&lt;unsafe&gt;");
		assertThat(output.resolve(DiscAnalyzer.STYLESHEET_NAME)).exists();
	}

	@Test
	void categorizedReportIncludesBacklinksSubpathsAndOnlyValidLinks() throws Exception {
		Path disc = createDisc();
		Files.write(disc.resolve("BDMV/PLAYLIST/00003.mpls"), new byte[0]);
		Files.write(disc.resolve("BDMV/CLIPINF/00004.clpi"), new byte[0]);
		Files.write(disc.resolve("BDMV/STREAM/00004.m2ts"), new byte[0]);
		Path output = tempDir.resolve("report");
		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		Files.writeString(output.resolve("BDMV/index.json"), "{}");
		Files.writeString(output.resolve("BDMV/MovieObject.json"), "{\"movieObjects\":[]}");
		Files.writeString(output.resolve("BDMV/PLAYLIST/00001.json"), """
				{"playItems":[{"clipName":"00002","streams":[{"pid":4113,"codingType":"H265_HEVC"}]},
				{"clipName":"00002","streams":[{"pid":4113,"codingType":"H265_HEVC"}]}],
				"subPaths":[{"subPathType":3,"subPlayItems":[{"clipName":"00004"}]}]}
				""");
		Files.writeString(output.resolve("BDMV/PLAYLIST/00003.json"), """
				{"playItems":[{"clipName":"00002"}],"subPaths":[{"subPlayItems":[{"clipName":"00004"}]}]}
				""");
		Files.writeString(output.resolve("BDMV/CLIPINF/00002.json"), "{\"clipName\":\"00002\",\"streams\":[]}");
		Files.writeString(output.resolve("BDMV/STREAM/00002.json"), "{}");
		Files.writeString(output.resolve("BDMV/CLIPINF/00004.json"), """
				{"streams":[{"pid":5120,"codingType":"INTERACTIVE_GRAPHICS"}]}
				""");
		// STREAM/00004 exists on disc but parsing did not produce JSON.
		analyzer.render(plan, List.of(), false);

		String root = Files.readString(output.resolve("index.html"));
		assertThat(root).contains("BDMV/PLAYLIST/index.html", "BDMV/CLIPINF/index.html", "BDMV/STREAM/index.html")
				.doesNotContain("BDMV/PLAYLIST/00001.html");
		String playlists = Files.readString(output.resolve("BDMV/PLAYLIST/index.html"));
		assertThat(playlists).contains("2 / 2 parsed", "00001.html", "00003.html", "../../disc-analyse.css",
				"../../index.html");
		assertThat(playlists.indexOf("00001.html")).isLessThan(playlists.indexOf("00003.html"));
		String playlist = Files.readString(output.resolve("BDMV/PLAYLIST/00001.html"));
		assertThat(playlist).contains("href=\"index.html\"", "../CLIPINF/00004.html", "stream-menu", "tone-h265-hevc",
				"Sources (2)", "SubPath clip 00004 (clipinfo)").doesNotContain("href=\"../STREAM/00004.html\"");
		assertThat(playlist.indexOf("Stream overview")).isLessThan(playlist.indexOf("Related objects"));
		assertThat(playlist.indexOf("Stream overview")).isLessThan(playlist.indexOf("Parsed data"));
		for (String path : List.of("CLIPINF/00002", "STREAM/00002", "CLIPINF/00004")) {
			String page = Files.readString(output.resolve("BDMV/" + path + ".html"));
			assertThat(page).contains("Referenced by playlists", "2 / 2 parsed", "../PLAYLIST/00001.html",
					"../PLAYLIST/00003.html");
			assertThat(page.split("href=\"../PLAYLIST/00001.html\"", -1)).hasSize(2);
		}
		assertAllLocalLinksExist(output);
	}

	@Test
	void subpathStreamSummaryFallsBackToM2tsWhenClipMetadataIsEmpty() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("fallback");
		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		Files.writeString(output.resolve("BDMV/PLAYLIST/00001.json"), """
				{"subPaths":[{"subPlayItems":[{"clipName":"00002"}]}]}
				""");
		Files.writeString(output.resolve("BDMV/CLIPINF/00002.json"), "{\"streams\":[]}");
		Files.writeString(output.resolve("BDMV/STREAM/00002.json"), """
				{"streams":[{"pid":5120,"codingType":"INTERACTIVE_GRAPHICS"}]}
				""");
		analyzer.render(plan, List.of(), false);
		assertThat(Files.readString(output.resolve("BDMV/PLAYLIST/00001.html"))).contains("stream-menu",
				"SubPath clip 00002 (m2ts)");
		assertAllLocalLinksExist(output);
	}

	@Test
	void playlistSummaryMergesSelectedStreamsWithBothCompanionFormats() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("merged");
		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		Files.writeString(output.resolve("BDMV/PLAYLIST/00001.json"), """
				{"playItems":[{"clipName":"00002","streams":[
				{"pid":4113,"codingType":"H265_HEVC","videoFormat":8,"frameRate":1}]}],
				"subPaths":[{"subPlayItems":[{"clipName":"00002"}]}]}
				""");
		Files.writeString(output.resolve("BDMV/CLIPINF/00002.json"), """
				{"streams":[{"pid":4113,"codingType":"H265_HEVC","videoFormat":8,"frameRate":1,
				"aspectRatio":3,"dynamicRangeType":1,"colorSpace":2}]}
				""");
		Files.writeString(output.resolve("BDMV/STREAM/00002.json"), """
				{"streams":[{"pid":4113,"codingType":"H265_HEVC","streamTypeByte":36,
				"videoFormat":8,"frameRateFps":23.976}]}
				""");
		analyzer.render(plan, List.of(), false);
		String html = Files.readString(output.resolve("BDMV/PLAYLIST/00001.html"));
		assertThat(html.split("<article class=\"stream-tile", -1)).hasSize(2);
		assertThat(html).contains("Sources (3)", "SubPath clip 00002 (clipinfo)", "SubPath clip 00002 (m2ts)");
		assertAllLocalLinksExist(output);
	}

	@Test
	void partialAndDeferredReportsKeepIndexesWithoutBrokenLinks() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("partial");
		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		Files.writeString(output.resolve("BDMV/PLAYLIST/00001.json"), "invalid json");
		Files.writeString(output.resolve("BDMV/CLIPINF/00002.json"), "{}");
		analyzer.render(plan, List.of(), false);
		assertThat(Files.readString(output.resolve("index.html"))).contains("Cannot read parsed data");
		assertThat(Files.readString(output.resolve("BDMV/CLIPINF/00002.html")))
				.contains("0 / 1 parsed", "No references found", "not parsed / unavailable")
				.doesNotContain("href=\"../STREAM/00002.html\"");
		assertAllLocalLinksExist(output);

		Path deferred = tempDir.resolve("deferred");
		DiscAnalyzer.Plan deferredPlan = analyzer.prepare(disc, deferred);
		analyzer.render(deferredPlan, List.of(), true);
		assertThat(Files.readString(deferred.resolve("index.html"))).contains("Analysis prepared, not executed");
		assertThat(Files.readString(deferred.resolve("BDMV/PLAYLIST/index.html"))).contains("Deferred", "0 / 1 parsed");
		assertAllLocalLinksExist(deferred);
	}

	@Test
	void missingSourceFoldersStillHaveEmptyCategoryIndexes() throws Exception {
		Path disc = Files.createDirectories(tempDir.resolve("empty/BDMV")).getParent();
		Path output = tempDir.resolve("empty-report");
		analyzer.render(analyzer.prepare(disc, output), List.of(), false);
		for (String folder : List.of("PLAYLIST", "CLIPINF", "STREAM")) {
			assertThat(Files.readString(output.resolve("BDMV/" + folder + "/index.html"))).contains("0 / 0 parsed",
					"No files found");
		}
		assertAllLocalLinksExist(output);
	}

	@Test
	void movieObjectLinksOnlyImmediatePlaylistOperands() throws Exception {
		Path disc = createDisc();
		Path output = tempDir.resolve("commands");
		DiscAnalyzer.Plan plan = analyzer.prepare(disc, output);
		Files.writeString(output.resolve("BDMV/PLAYLIST/00001.json"), "{}");
		var mapper = JsonMapperFactory.get();
		var commands = mapper.createArrayNode();
		for (NavigationCommandMnemonic mnemonic : List.of(NavigationCommandMnemonic.PLAY_PL,
				NavigationCommandMnemonic.PLAY_PL_PI, NavigationCommandMnemonic.PLAY_PL_PM)) {
			commands.add(mapper.valueToTree(NavigationCommand.compile(mnemonic, 1, true, 0, true)));
			commands.add(mapper.valueToTree(NavigationCommand.compile(mnemonic, 1, false, 0, true)));
			commands.add(mapper.valueToTree(NavigationCommand.compile(mnemonic, 0x80000001L, false, 0, true)));
		}
		commands.add(mapper.readTree("{\"mnemonic\":\"PLAY_PL\",\"operand1\":1}"));
		commands.add(mapper.readTree("{\"mnemonic\":\"PLAY_PL\"}"));
		commands.add(mapper.readTree("{\"mnemonic\":\"PLAY_PL\",\"operand1\":100000}"));
		commands.add(mapper.readTree("{\"mnemonic\":\"NOT_A_COMMAND\",\"operand1\":1}"));
		var authoritative = mapper
				.valueToTree(NavigationCommand.compile(NavigationCommandMnemonic.PLAY_PL, 1, false, 0, true));
		((com.fasterxml.jackson.databind.node.ObjectNode) authoritative).put("mnemonic", "JUMP_TITLE");
		commands.add(authoritative);
		commands.add(
				mapper.valueToTree(NavigationCommand.compile(NavigationCommandMnemonic.JUMP_TITLE, 1, true, 0, true)));
		var object = mapper.createObjectNode().set("navigationCommands", commands);
		var root = mapper.createObjectNode().set("movieObjects", mapper.createArrayNode().add(object));
		mapper.writeValue(output.resolve("BDMV/MovieObject.json").toFile(), root);
		Files.writeString(output.resolve("BDMV/index.json"), "{\"titles\":[{\"objectType\":1,\"hdmvObjectId\":0}]}");
		analyzer.render(plan, List.of(), false);
		String html = Files.readString(output.resolve("BDMV/MovieObject.html"));
		assertThat(html.split("href=\"PLAYLIST/00001.html\"", -1)).hasSize(5);
		assertThat(html).contains("GPR[1]", "PSR[1]", "runtime value", "Unresolved navigation command",
				"Invalid literal playlist number", "id=\"object-0\"");
		assertAllLocalLinksExist(output);
	}

	private static void assertAllLocalLinksExist(Path output) throws Exception {
		List<Path> pages;
		try (var paths = Files.walk(output)) {
			pages = paths.filter(path -> path.toString().endsWith(".html")).toList();
		}
		Pattern href = Pattern.compile("href=\"([^\"]+)\"");
		for (Path page : pages) {
			var matcher = href.matcher(Files.readString(page));
			while (matcher.find()) {
				String[] parts = matcher.group(1).split("#", 2);
				Path target = page.getParent().resolve(parts[0]).normalize();
				assertThat(target).as("Link from %s to %s", page, matcher.group(1)).exists();
				if (parts.length == 2)
					assertThat(Files.readString(target)).contains("id=\"" + parts[1] + "\"");
			}
		}
	}

	private Path createDisc() throws Exception {
		Path bdmv = Files.createDirectories(tempDir.resolve("disc/BDMV"));
		Files.createDirectories(bdmv.resolve("PLAYLIST"));
		Files.createDirectories(bdmv.resolve("CLIPINF"));
		Files.createDirectories(bdmv.resolve("STREAM"));
		Files.write(bdmv.resolve("index.bdmv"), new byte[0]);
		Files.write(bdmv.resolve("MovieObject.bdmv"), new byte[0]);
		Files.write(bdmv.resolve("PLAYLIST/00001.mpls"), new byte[0]);
		Files.write(bdmv.resolve("CLIPINF/00002.clpi"), new byte[0]);
		Files.write(bdmv.resolve("STREAM/00002.m2ts"), new byte[0]);
		return bdmv.getParent();
	}
}
