/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/m2ts/PreservingM2tsClipWriterCacheTest.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

package org.brts.lowlevel.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.m2ts.ExtractionCache;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.M2tsTiming;
import org.brts.common.m2ts.TargetRootWorkspace;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.TsMuxerUtils;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreservingM2tsClipWriterCacheTest {

	@TempDir
	Path temp;

	@Test
	void acceptsOnlyTheCompletePinnedSelectionAndCanonicalTimingPath() throws Exception {
		var fixture = fixture();
		var validated = validate(fixture, fixture.stage());
		assertThat(validated.manifest().info().getSourcePath()).isEqualTo(fixture.source().toString());
		assertThat(validated.directory()).isEqualTo(Path.of(fixture.descriptor().getTimingManifest()).getParent());
		assertThat(validated.timing()).isEqualTo(M2tsTiming.read(Path.of(fixture.descriptor().getTimingManifest())));
		var audio = new M2tsDescriptor.StreamEntry();
		audio.setPid(0x1100);
		audio.setStreamTypeByte(0x81);
		fixture.descriptor().setStreams(List.of(fixture.descriptor().getStreams().getFirst(), audio));
		assertThatThrownBy(() -> validate(fixture, fixture.stage())).isInstanceOf(IOException.class)
				.hasMessageContaining("complete extraction cache selection");
		fixture.descriptor().setStreams(List.of());
		assertThatThrownBy(() -> validate(fixture, fixture.stage())).isInstanceOf(IOException.class)
				.hasMessageContaining("complete extraction cache selection");
	}

	@Test
	void writerRejectsSourceAndClpiMismatchBeforeBackendOrStaging() throws Exception {
		var fixture = fixture();
		Path other = Files.createDirectory(temp.resolve("other-source"));
		Path otherSource = Files.write(other.resolve("00001.m2ts"), new byte[192]);
		Path otherClpi = Files.writeString(other.resolve("00001.clpi"), "not the cached CLPI");
		fixture.descriptor().setSourceM2ts(otherSource.toString());
		assertWriteRejected(fixture, "does not match extraction cache");
		fixture.descriptor().setSourceM2ts(fixture.source().toString());
		fixture.descriptor().setSourceClpi(otherClpi.toString());
		assertWriteRejected(fixture, "does not match extraction cache");
		assertThat(fixture.root()).doesNotExist();
	}

	@Test
	void rejectsCopiedOrAliasedTimingEvenWhenContentsAreIdentical() throws Exception {
		var fixture = fixture();
		Path timing = Path.of(fixture.descriptor().getTimingManifest());
		Path copy = Files.copy(timing, temp.resolve("copied-timing.json"));
		fixture.descriptor().setTimingManifest(copy.toString());
		assertWriteRejected(fixture, "directory/timing.json");
		Path alias = Files.createSymbolicLink(temp.resolve("timing-alias.json"), timing);
		fixture.descriptor().setTimingManifest(alias.toString());
		assertWriteRejected(fixture, "directory/timing.json");
		assertThat(fixture.root()).doesNotExist();
	}

	@Test
	void rejectsDuplicateSelectionAndStaleArtifactBeforeBackend() throws Exception {
		var fixture = fixture();
		var video = fixture.descriptor().getStreams().getFirst();
		fixture.descriptor().setStreams(List.of(video, video));
		assertWriteRejected(fixture, "Duplicate descriptor PID");
		fixture.descriptor().setStreams(List.of(video));
		Files.writeString(Path.of(video.getFile()), "changed cache bytes");
		assertWriteRejected(fixture, "Invalid extraction cache");
		assertThat(fixture.root()).doesNotExist();
	}

	@Test
	void rejectsTargetsAndStageOutsideTheCachedTargetRootIncludingSymlinkEscape() throws Exception {
		var fixture = fixture();
		Path outside = Files.createDirectory(temp.resolve("outside"));
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateCache(fixture.descriptor(), fixture.source(),
				fixture.clpi(), Path.of(fixture.descriptor().getTimingManifest()), outside.resolve("00001.m2ts"),
				fixture.targetClpi(), fixture.root(), null)).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateCache(fixture.descriptor(), fixture.source(),
				fixture.clpi(), Path.of(fixture.descriptor().getTimingManifest()), fixture.target(),
				outside.resolve("00001.clpi"), fixture.root(), null)).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
		assertThatThrownBy(() -> validate(fixture, outside.resolve("attempt"))).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
		Path alias = Files.createSymbolicLink(fixture.target().getParent().resolve("escaped"), outside);
		assertThatThrownBy(() -> validate(fixture, alias.resolve("attempt"))).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
		assertThatThrownBy(() -> PreservingM2tsClipWriter.validateCache(fixture.descriptor(), fixture.source(),
				fixture.clpi(), Path.of(fixture.descriptor().getTimingManifest()), fixture.target(),
				fixture.targetClpi(), outside.resolve("writer-root"), null)).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
	}

	@Test
	void repeatedPublicationGateDetectsCacheMutation() throws Exception {
		var fixture = fixture();
		validate(fixture, fixture.stage());
		Files.writeString(fixture.clpi(), "changed original CLPI");
		assertThatThrownBy(() -> validate(fixture, fixture.stage())).isInstanceOf(IOException.class)
				.hasMessageContaining("Invalid extraction cache");
		assertThat(fixture.target()).doesNotExist();
		assertThat(fixture.targetClpi()).doesNotExist();
	}

	@Test
	void repeatedPublicationGateRejectsStageReplacedByEscapingSymlink() throws Exception {
		var fixture = fixture();
		validate(fixture, fixture.stage());
		Files.createDirectories(fixture.root());
		Path outside = Files.createDirectory(temp.resolve("outside-stage"));
		Files.createSymbolicLink(fixture.stage(), outside);
		assertThatThrownBy(() -> validate(fixture, fixture.stage())).isInstanceOf(IOException.class)
				.hasMessageContaining("inside target root");
	}

	@Test
	void cacheBackedWriterReusesMetadataAndEsButStillRunsBoundedGeometryValidation() throws Exception {
		var fixture = fixture();
		try (var backend = mockStatic(TsMuxerUtils.class);
				var parsers = mockConstruction(M2tsParser.class);
				var extractors = mockConstruction(M2tsExtractor.class);
				var clpiParsers = mockConstruction(ClipInfoParser.class,
						(mock, context) -> when(mock.parse(any(Path.class))).thenReturn(originalClip()));
				var geometry = mockStatic(PreservationVideoGeometry.class)) {
			backend.when(TsMuxerUtils::resolveTsMuxeRBinary).thenReturn("unused-tsmuxer");
			geometry.when(() -> PreservationVideoGeometry.read(fixture.source(), 0x1011, 0x1b))
					.thenThrow(new IOException("bounded geometry validation reached"));
			assertWriteRejected(fixture, "bounded geometry validation reached");
			assertThat(parsers.constructed()).isEmpty();
			assertThat(extractors.constructed()).isEmpty();
			assertThat(clpiParsers.constructed()).hasSize(1);
			verify(clpiParsers.constructed().getFirst()).parse(fixture.clpi());
			geometry.verify(() -> PreservationVideoGeometry.read(fixture.source(), 0x1011, 0x1b));
		}
		try (var paths = Files.walk(fixture.root())) {
			assertThat(paths.map(p -> p.getFileName().toString()).toList()).doesNotContain("source-check");
		}
		assertThat(ExtractionCache.validate(fixture.descriptor().getExtractionCacheReference())).isNotNull();
	}

	@Test
	void legacyDescriptorStillParsesAndReextractsSourceAndRejectsStaleTiming() throws Exception {
		var fixture = fixture();
		var cache = ExtractionCache.validate(fixture.descriptor().getExtractionCacheReference());
		fixture.descriptor().setExtractionCacheReference(null);
		// Legacy contracts may place their outputs outside a cache target root.
		Path legacyTarget = temp.resolve("legacy-output/00001.m2ts");
		Path legacyClpi = temp.resolve("legacy-output/00001.clpi");
		try (var backend = mockStatic(TsMuxerUtils.class);
				var parsers = mockConstruction(M2tsParser.class,
						(mock, context) -> when(mock.parse(any(Path.class))).thenReturn(cache.manifest().info()));
				var clpiParsers = mockConstruction(ClipInfoParser.class,
						(mock, context) -> when(mock.parse(any(Path.class))).thenReturn(originalClip()));
				var extractors = mockConstruction(M2tsExtractor.class, (mock, context) -> doAnswer(invocation -> {
					Path output = invocation.getArgument(2);
					Files.createDirectories(output);
					M2tsTiming.write(output.resolve(M2tsTimingManifest.FILE_NAME), timing(fixture.source(), 1L));
					return null;
				}).when(mock).extract(any(Path.class), any(M2tsInfo.class), any(Path.class), any(), eq(true)))) {
			backend.when(TsMuxerUtils::resolveTsMuxeRBinary).thenReturn("unused-tsmuxer");
			assertThatThrownBy(
					() -> new PreservingM2tsClipWriter().write(fixture.descriptor(), legacyTarget, legacyClpi))
					.isInstanceOf(IOException.class).hasMessageContaining("differs from re-extraction");
			assertThat(parsers.constructed()).hasSize(1);
			verify(parsers.constructed().getFirst()).parse(fixture.source());
			assertThat(extractors.constructed()).hasSize(1);
			verify(extractors.constructed().getFirst()).extract(eq(fixture.source()), any(M2tsInfo.class),
					any(Path.class), eq(Set.of(0x1011)), eq(true));
		}
		try (var paths = Files.walk(legacyTarget.getParent())) {
			assertThat(paths.map(p -> p.getFileName().toString()).toList()).contains("source-check");
		}
		assertThat(legacyTarget).doesNotExist();
		assertThat(legacyClpi).doesNotExist();
	}

	private ExtractionCache.Validated validate(Fixture fixture, Path stage) throws IOException {
		return PreservingM2tsClipWriter.validateCache(fixture.descriptor(), fixture.source(), fixture.clpi(),
				Path.of(fixture.descriptor().getTimingManifest()), fixture.target(), fixture.targetClpi(),
				fixture.root(), stage);
	}

	private void assertWriteRejected(Fixture fixture, String message) {
		assertThatThrownBy(() -> new PreservingM2tsClipWriter().write(fixture.descriptor(), fixture.target(),
				fixture.targetClpi())).isInstanceOf(IOException.class).hasMessageContaining(message);
		assertThat(fixture.target()).doesNotExist();
		assertThat(fixture.targetClpi()).doesNotExist();
	}

	private Fixture fixture() throws IOException {
		Path sourceRoot = Files.createDirectory(temp.resolve("source"));
		Path targetRoot = TargetRootWorkspace.prepare(sourceRoot, temp.resolve("target"), null);
		Path source = Files.write(sourceRoot.resolve("00001.m2ts"), new byte[192]).toRealPath();
		Path clpi = Files.writeString(sourceRoot.resolve("00001.clpi"), "original CLPI").toRealPath();
		Path directory = Files.createDirectory(targetRoot.resolve("cache"));
		Path es = Files.writeString(directory.resolve("pid_1011.h264"), "original video ES");
		Path timing = directory.resolve(M2tsTimingManifest.FILE_NAME);
		M2tsTiming.write(timing, timing(source, 0L));
		var stream = new M2tsStreamInfo();
		stream.setPid(0x1011);
		stream.setCodingType(StreamCodingType.H264_AVC);
		stream.setStreamTypeByte(0x1b);
		var info = new M2tsInfo();
		info.setSourcePath(source.toString());
		info.setTotalPackets(1);
		info.setStreams(List.of(stream));
		Path marker = directory.resolve(ExtractionCache.FILE_NAME);
		ExtractionCache.write(marker, new ExtractionCache.Manifest(ExtractionCache.VERSION, targetRoot.toString(),
				ExtractionCache.identity(source), ExtractionCache.identity(clpi), info, List.of(0x1011),
				Map.of(es.getFileName().toString(), relative(es), M2tsTimingManifest.FILE_NAME, relative(timing)),
				90_000));
		var descriptor = new M2tsDescriptor();
		descriptor.setOutputName("00001");
		descriptor.setSourceM2ts(source.toString());
		descriptor.setSourceClpi(clpi.toString());
		descriptor.setTimingManifest(timing.toString());
		descriptor.setMaximumOutputBytes(192L);
		descriptor.setExtractionCacheReference(ExtractionCache.reference(marker));
		var video = new M2tsDescriptor.StreamEntry();
		video.setPid(0x1011);
		video.setStreamTypeByte(0x1b);
		video.setFrameRateFps(25.0);
		video.setVideoFormat(6);
		video.setFile(es.toString());
		descriptor.setStreams(List.of(video));
		Path target = targetRoot.resolve("BDMV/STREAM/00001.m2ts");
		Path targetClpi = targetRoot.resolve("BDMV/CLIPINF/00001.clpi");
		Path root = target.getParent().resolve(".brts-preserve-00001");
		return new Fixture(descriptor, source, clpi, target, targetClpi, root, root.resolve("attempt-test"));
	}

	private static M2tsTimingManifest timing(Path source, long pts) {
		return new M2tsTimingManifest(
				2, source.toString(), 192, null, List.of(new StreamTiming(0x1011, "pid_1011.h264",
						StreamCodingType.H264_AVC, pts, null, List.of(new PesTiming(0, 0, pts, null)), List.of())),
				null, Map.of(0x1011, 1L));
	}

	private static ClipInfo originalClip() {
		var stream = new ClipStream();
		stream.setPid(0x1011);
		stream.setCodingType(StreamCodingType.H264_AVC);
		var clip = new ClipInfo();
		clip.setApplicationType(ClipInfo.APPLICATION_TYPE_MOVIE);
		clip.setStreams(List.of(stream));
		return clip;
	}

	private static ExtractionCache.Identity relative(Path path) throws IOException {
		var identity = ExtractionCache.identity(path);
		return new ExtractionCache.Identity(path.getFileName().toString(), identity.size(), identity.modified(),
				identity.fileKey());
	}

	private record Fixture(M2tsDescriptor descriptor, Path source, Path clpi, Path target, Path targetClpi, Path root,
			Path stage) {
	}
}
