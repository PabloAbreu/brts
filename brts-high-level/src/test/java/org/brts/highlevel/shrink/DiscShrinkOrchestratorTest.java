/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/test/java/org/brts/highlevel/shrink/DiscShrinkOrchestratorTest.java' is part of BRTS.
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

package org.brts.highlevel.shrink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.PlayItemStream;
import org.brts.lowlevel.model.mpls.SubPath;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.brts.lowlevel.shrink.BatchWholeM2tsConverter;
import org.brts.lowlevel.shrink.M2tsShrinkCache;
import org.brts.lowlevel.shrink.M2tsShrinkInputResolver;
import org.brts.lowlevel.shrink.M2tsShrinkInventory;
import org.brts.lowlevel.shrink.M2tsShrinkPlan;
import org.brts.lowlevel.shrink.M2tsShrinkPlanner;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.writer.MoviePlaylistWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DiscShrinkOrchestratorTest {

	@Test
	void copiesDiscTreeAndReportsWholeTreeSize(@TempDir Path temp) throws Exception {
		Files.createDirectories(temp.resolve("source/BDMV/STREAM"));
		Files.createDirectories(temp.resolve("source/BDMV/PLAYLIST"));
		Files.createDirectories(temp.resolve("source/BDMV/CLIPINF"));
		Files.writeString(temp.resolve("source/BDMV/index.bdmv"), "index");
		Path output = temp.resolve("output");

		var result = new DiscShrinkOrchestrator(path -> {
			throw new java.io.IOException("No clips should require conversion");
		}).shrink(temp.resolve("source"), output, new DiscShrinkDescriptor(), 100, Engine.FFMPEG);

		assertThat(result.withinLimit()).isTrue();
		assertThat(result.actualBytes()).isEqualTo(5);
		assertThat(output.resolve("BDMV/index.bdmv")).hasContent("index");
	}

	@Test
	void retainsOutputAndReportsHardLimitOverrun(@TempDir Path temp) throws Exception {
		Files.createDirectories(temp.resolve("source/BDMV/STREAM"));
		Files.createDirectories(temp.resolve("source/BDMV/PLAYLIST"));
		Files.writeString(temp.resolve("source/BDMV/index.bdmv"), "larger than cap");
		Path output = temp.resolve("output");

		assertThatThrownBy(() -> new DiscShrinkOrchestrator(path -> {
		}).shrink(temp.resolve("source"), output, new DiscShrinkDescriptor(), 1, Engine.FFMPEG))
				.isInstanceOf(java.io.IOException.class).hasMessageContaining("output retained");
		assertThat(output.resolve("BDMV/index.bdmv")).exists();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void copiesUnmanageableClipWithoutParsingFailureAbortingDisc(boolean runtimeFailure, @TempDir Path temp)
			throws Exception {
		Path source = sourcePair(temp);
		Path output = temp.resolve("output");
		var result = new DiscShrinkOrchestrator(path -> {
			throw new AssertionError("Unmanageable clips must not be converted");
		}, (root, id) -> {
			if (runtimeFailure) {
				throw new IllegalArgumentException("Unsupported metadata");
			}
			throw new IOException("Unreadable CLPI");
		}).shrink(source, output, new DiscShrinkDescriptor(), 10_000, Engine.FFMPEG);

		assertOriginalPair(source, output);
		assertThat(result.actualBytes()).isEqualTo(Files.size(source.resolve("BDMV/STREAM/00001.m2ts"))
				+ Files.size(source.resolve("BDMV/CLIPINF/00001.clpi")));
		assertThat(result.diagnostics()).anyMatch(s -> s.contains("00001") && s.contains("discovery failed"));
	}

	@ParameterizedTest
	@ValueSource(longs = { 0, -1 })
	void copiesDurationlessClipEvenWithOtherwiseSupportedTopology(long duration, @TempDir Path temp) throws Exception {
		Path source = sourcePair(temp);
		Path output = temp.resolve("output");
		var descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of());
		var result = new DiscShrinkOrchestrator(path -> {
			throw new AssertionError("Durationless clips must not be converted");
		}, (root, id) -> metadata(root, duration)).shrink(source, output, descriptor, 10_000, Engine.FFMPEG);

		assertOriginalPair(source, output);
		assertThat(result.withinLimit()).isTrue();
		assertThat(result.diagnostics()).anyMatch(s -> s.contains("00001") && s.contains("without reduction"));
	}

	@Test
	void unreadablePlaylistKeepsAllClipsAndPlaylistUntouched(@TempDir Path temp) throws Exception {
		Path source = sourcePair(temp);
		Files.writeString(source.resolve("BDMV/PLAYLIST/00010.mpls"), "unsupported playlist");
		Path output = temp.resolve("output");
		var descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of());
		var result = new DiscShrinkOrchestrator(path -> {
			throw new AssertionError("Unknown playlist references must prevent conversion");
		}, (root, id) -> metadata(root, 900_000)).shrink(source, output, descriptor, 10_000, Engine.FFMPEG);

		assertOriginalPair(source, output);
		assertThat(output.resolve("BDMV/PLAYLIST/00010.mpls")).hasContent("unsupported playlist");
		assertThat(result.diagnostics()).anyMatch(s -> s.contains("Playlist 00010") && s.contains("all clips"));
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2 })
	void restoresBothOriginalFilesAfterFailedAttempt(int publishedFiles, @TempDir Path temp) throws Exception {
		Path source = sourcePair(temp);
		Path output = temp.resolve("output");
		var descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of());
		try (var resolvers = mockConstruction(M2tsShrinkInputResolver.class, (mock, context) -> {
			when(mock.resolve(any(), eq("00001"))).thenAnswer(invocation -> {
				if (publishedFiles >= 1) {
					Files.writeString(output.resolve("BDMV/STREAM/00001.m2ts"), "partial stream");
				}
				if (publishedFiles >= 2) {
					Files.writeString(output.resolve("BDMV/CLIPINF/00001.clpi"), "partial clip info");
				}
				throw new IOException("Unsupported conversion");
			});
		})) {
			var result = new DiscShrinkOrchestrator(path -> {
			}, (root, id) -> metadata(root, 900_000)).shrink(source, output, descriptor, 10_000, Engine.FFMPEG);

			assertOriginalPair(source, output);
			assertThat(result.diagnostics()).anyMatch(s -> s.contains("copied unchanged: Unsupported conversion"));
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 2, 3 })
	void externalIgsAllowsMainClipReductionAndSurvivesPlaylistRewrite(int streamType, @TempDir Path temp)
			throws Exception {
		Path source = sourcePair(temp);
		writeIgsPlaylist(source, streamType);
		Path output = temp.resolve("output");
		var descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of());
		var counts = metadata(source, 900_000).inventory();
		var prepared = mock(M2tsShrinkCache.Prepared.class, RETURNS_DEEP_STUBS);
		var clipPlan = mock(M2tsShrinkPlan.class);
		when(clipPlan.decision()).thenReturn(M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED);
		try (var resolvers = mockConstruction(M2tsShrinkInputResolver.class, (mock, context) -> {
			when(mock.resolve(source, "00001")).thenReturn(counts);
		}); var caches = mockConstruction(M2tsShrinkCache.class, (mock, context) -> {
			when(mock.prepare(eq(source), eq("00001"), any(), eq(output), any())).thenReturn(prepared);
		}); var planners = mockConstruction(M2tsShrinkPlanner.class, (mock, context) -> {
			when(mock.plan(eq(counts), eq("00001"), any(), anyLong(), eq(Engine.FFMPEG))).thenReturn(clipPlan);
		}); var converters = mockConstruction(BatchWholeM2tsConverter.class, (mock, context) -> {
			doAnswer(invocation -> {
				Files.writeString(output.resolve("BDMV/STREAM/00001.m2ts"), "reduced");
				Files.writeString(output.resolve("BDMV/CLIPINF/00001.clpi"), "new clpi");
				return null;
			}).when(mock).convert(any());
		})) {
			var result = new DiscShrinkOrchestrator(path -> {
				throw new AssertionError("Conversion is mocked");
			}, (root, id) -> metadata(root, 900_000)).shrink(source, output, descriptor, 10_000, Engine.FFMPEG);

			assertThat(converters.constructed()).hasSize(1);
			assertThat(caches.constructed()).hasSize(1);
			assertThat(planners.constructed()).hasSize(1);
			verify(resolvers.constructed().get(0)).resolve(source, "00001");
			assertThat(output.resolve("BDMV/STREAM/00001.m2ts")).hasContent("reduced");
			assertThat(result.diagnostics()).noneMatch(s -> s.contains("Clip 00001"));
			assertThat(result.diagnostics()).anyMatch(s -> s.contains("Clip 00077") && s.contains("embedded IGS"));
		}
		for (String relative : List.of("BDMV/STREAM/00077.m2ts", "BDMV/CLIPINF/00077.clpi")) {
			assertThat(Files.readAllBytes(output.resolve(relative)))
					.isEqualTo(Files.readAllBytes(source.resolve(relative)));
		}
		var playlist = new MoviePlaylistParser().parse(output.resolve("BDMV/PLAYLIST/00010.mpls"));
		assertThat(playlist.getPlayItems().get(0).getStreams()).extracting(PlayItemStream::getPid)
				.containsExactly(0x1011, 0x1400);
		var igs = playlist.getPlayItems().get(0).getStreams().get(1);
		assertThat(igs.getStreamType()).isEqualTo(streamType);
		assertThat(igs.getSubpathId()).isZero();
		assertThat(igs.getSubclipId()).isZero();
		assertThat(playlist.getSubPaths().get(0).getSubPathType()).isEqualTo(3);
		assertThat(playlist.getSubPaths().get(0).getSubPlayItems().get(0).getClipName()).isEqualTo("00077");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1 })
	void inMuxIgsKeepsMainClipUnchanged(int streamType, @TempDir Path temp) throws Exception {
		Path source = sourcePair(temp);
		writeIgsPlaylist(source, streamType);
		Path output = temp.resolve("output");
		var descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of());
		try (var resolvers = mockConstruction(M2tsShrinkInputResolver.class)) {
			var result = new DiscShrinkOrchestrator(path -> {
				throw new AssertionError("Embedded IGS must not be converted");
			}, (root, id) -> metadata(root, 900_000)).shrink(source, output, descriptor, 10_000, Engine.FFMPEG);

			assertOriginalPair(source, output);
			assertThat(resolvers.constructed()).isEmpty();
			assertThat(result.diagnostics()).anyMatch(s -> s.contains("Clip 00001") && s.contains("embedded IGS"));
		}
	}

	@Test
	void physicalIgsKeepsMainClipUnchangedDespiteExternalPlaylistIgs(@TempDir Path temp) throws Exception {
		Path source = sourcePair(temp);
		writeIgsPlaylist(source, PlayItemStream.STREAM_TYPE_OUT_OF_MUX);
		Path output = temp.resolve("output");
		var descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of());
		try (var resolvers = mockConstruction(M2tsShrinkInputResolver.class)) {
			var result = new DiscShrinkOrchestrator(path -> {
				throw new AssertionError("Physical IGS must not be converted");
			}, (root, id) -> {
				var discovered = metadata(root, 900_000);
				var tracks = new ArrayList<>(discovered.inventory().tracks());
				tracks.add(
						new M2tsShrinkInventory.Track(0x1400, StreamCodingType.INTERACTIVE_GRAPHICS, "fra", null, 0));
				return new M2tsShrinkInputResolver.Metadata(new M2tsShrinkInventory(discovered.inventory().m2tsPath(),
						discovered.inventory().clpiPath(), discovered.inventory().sourceBytes(), 900_000, tracks),
						discovered.info());
			}).shrink(source, output, descriptor, 10_000, Engine.FFMPEG);

			assertOriginalPair(source, output);
			assertThat(resolvers.constructed()).isEmpty();
			assertThat(result.diagnostics()).anyMatch(s -> s.contains("Clip 00001") && s.contains("embedded IGS"));
		}
	}

	private static void writeIgsPlaylist(Path source, int streamType) throws IOException {
		Files.writeString(source.resolve("BDMV/STREAM/00077.m2ts"), "external menu");
		Files.writeString(source.resolve("BDMV/CLIPINF/00077.clpi"), "external menu clpi");
		var video = new PlayItemStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		var audio = new PlayItemStream();
		audio.setPid(0x1100);
		audio.setCodingType(StreamCodingType.DOLBY_AC3);
		audio.setLanguage("eng");
		var igs = new PlayItemStream();
		igs.setPid(0x1400);
		igs.setCodingType(StreamCodingType.INTERACTIVE_GRAPHICS);
		igs.setStreamType(streamType);
		igs.setLanguage("fra");
		var item = new PlayItem();
		item.setClipName("00001");
		item.setOutTimeTicks(450_000);
		item.setStreams(List.of(video, audio, igs));
		var subItem = new SubPath.SubPlayItem();
		subItem.setClipName("00077");
		subItem.setConnectionCondition(1);
		subItem.setOutTimeTicks(1_875);
		var subPath = new SubPath();
		subPath.setSubPathType(3);
		subPath.setSubPlayItems(List.of(subItem));
		var playlist = new MoviePlaylist();
		playlist.setPlayItems(List.of(item));
		playlist.setSubPaths(List.of(subPath));
		new MoviePlaylistWriter().write(playlist, source.resolve("BDMV/PLAYLIST/00010.mpls"));
	}

	private static Path sourcePair(Path temp) throws IOException {
		Path source = temp.resolve("source");
		Files.createDirectories(source.resolve("BDMV/STREAM"));
		Files.createDirectories(source.resolve("BDMV/CLIPINF"));
		Files.createDirectories(source.resolve("BDMV/PLAYLIST"));
		Files.writeString(source.resolve("BDMV/STREAM/00001.m2ts"), "original stream");
		Files.writeString(source.resolve("BDMV/CLIPINF/00001.clpi"), "original clpi");
		return source;
	}

	private static void assertOriginalPair(Path source, Path output) throws IOException {
		for (String relative : List.of("BDMV/STREAM/00001.m2ts", "BDMV/CLIPINF/00001.clpi")) {
			assertThat(Files.readAllBytes(output.resolve(relative)))
					.isEqualTo(Files.readAllBytes(source.resolve(relative)));
		}
	}

	private static M2tsShrinkInputResolver.Metadata metadata(Path root, long duration) throws IOException {
		Path m2ts = root.resolve("BDMV/STREAM/00001.m2ts");
		M2tsStreamInfo video = new M2tsStreamInfo();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		M2tsStreamInfo audio = new M2tsStreamInfo();
		audio.setPid(0x1100);
		audio.setCodingType(StreamCodingType.DOLBY_AC3);
		M2tsInfo info = new M2tsInfo();
		info.setStreams(List.of(video, audio));
		return new M2tsShrinkInputResolver.Metadata(
				new M2tsShrinkInventory(m2ts, root.resolve("BDMV/CLIPINF/00001.clpi"), Files.size(m2ts), duration,
						List.of(new M2tsShrinkInventory.Track(0x1011, StreamCodingType.H264_AVC, null, null, 0),
								new M2tsShrinkInventory.Track(0x1100, StreamCodingType.DOLBY_AC3, "eng", null, 0))),
				info);
	}

	@Test
	void rejectsNonEmptyOutputBeforeCopy(@TempDir Path temp) throws Exception {
		Files.createDirectories(temp.resolve("source/BDMV/STREAM"));
		Files.createDirectories(temp.resolve("source/BDMV/PLAYLIST"));
		Files.createDirectories(temp.resolve("existing"));
		Files.writeString(temp.resolve("existing/keep.txt"), "existing");

		assertThatThrownBy(() -> new DiscShrinkOrchestrator(path -> {
		}).shrink(temp.resolve("source"), temp.resolve("existing"), new DiscShrinkDescriptor(), 100, Engine.FFMPEG))
				.isInstanceOf(java.io.IOException.class).hasMessageContaining("empty directory");
		assertThat(temp.resolve("existing/keep.txt")).hasContent("existing");
	}

}
