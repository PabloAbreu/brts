/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/ExtractionCacheTest.java' is part of BRTS.
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

package org.brts.common.m2ts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.brts.common.m2ts.ExtractionCache.Identity;
import org.brts.common.m2ts.ExtractionCache.Manifest;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.m2ts.model.M2tsTimingManifest.PesTiming;
import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractionCacheTest {
	@TempDir
	Path temp;

	@Test
	void roundTripsMetadataCountsAndSurvivesTemporaryDirectoryMove() throws Exception {
		Fixture fixture = fixture();
		Path marker = fixture.directory.resolve(ExtractionCache.FILE_NAME);
		ExtractionCache.write(marker, fixture.manifest);
		Path destination = fixture.directory.resolveSibling("published");
		Files.move(fixture.directory, destination);
		var reference = ExtractionCache.reference(destination.resolve(ExtractionCache.FILE_NAME));
		var cache = ExtractionCache.validate(reference);
		assertThat(cache.directory()).isEqualTo(destination);
		assertThat(cache.manifest().duration90k()).isEqualTo(90_000);
		assertThat(cache.timing().sourcePacketCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(0x1011, 1L));
		assertThat(cache.timing().sourceSha256()).isNull();
		assertThat(cache.manifest().artifacts().values()).allMatch(id -> !Path.of(id.path()).isAbsolute());
		assertThat(cache.manifest().info().getStreams().getFirst().getLanguage()).isEqualTo("eng");
	}

	@Test
	void rejectsChangedOrMissingArtifactsAndNeverRewritesThem() throws Exception {
		Fixture fixture = fixture();
		var reference = publish(fixture);
		Path es = fixture.directory.resolve("pid_1011.h264");
		Files.writeString(es, "changed extraction");
		assertThatThrownBy(() -> ExtractionCache.validate(reference)).isInstanceOf(IOException.class)
				.hasMessageContaining("File identity changed");
		assertThat(Files.readString(es)).isEqualTo("changed extraction");
		Files.delete(es);
		assertThatThrownBy(() -> ExtractionCache.validate(reference)).isInstanceOf(IOException.class);
	}

	@Test
	void rejectsPinnedManifestMutationAndSourceOrClpiMutation() throws Exception {
		Fixture fixture = fixture();
		var reference = publish(fixture);
		Files.writeString(fixture.directory.resolve(ExtractionCache.FILE_NAME), "{}");
		assertThatThrownBy(() -> ExtractionCache.validate(reference)).isInstanceOf(IOException.class)
				.hasMessageContaining("File identity changed");
		fixture = fixture();
		var sourceReference = publish(fixture);
		Files.writeString(Path.of(fixture.manifest.source().path()), "mutated source");
		assertThatThrownBy(() -> ExtractionCache.validate(sourceReference)).isInstanceOf(IOException.class);
		fixture = fixture();
		var clpiReference = publish(fixture);
		Files.writeString(Path.of(fixture.manifest.clpi().path()), "mutated CLPI");
		assertThatThrownBy(() -> ExtractionCache.validate(clpiReference)).isInstanceOf(IOException.class);
	}

	@Test
	void rejectsUnsafeArtifactBasenamesEvenWhenOutsideFileExists() throws Exception {
		Fixture fixture = fixture();
		var artifacts = new LinkedHashMap<>(fixture.manifest.artifacts());
		Identity identity = artifacts.remove("pid_1011.h264");
		artifacts.put("../escape.h264", new Identity("../escape.h264", identity.size(), identity.modified(), null));
		Manifest invalid = withArtifacts(fixture.manifest, artifacts);
		ExtractionCache.write(fixture.directory.resolve(ExtractionCache.FILE_NAME), invalid);
		assertThatThrownBy(() -> ExtractionCache
				.validate(ExtractionCache.reference(fixture.directory.resolve(ExtractionCache.FILE_NAME))))
				.isInstanceOf(IOException.class).hasMessageContaining("relative basename");
	}

	@Test
	void rejectsTimingVersionOneAndMetadataCountMismatch() throws Exception {
		Fixture fixture = fixture();
		Path timingPath = fixture.directory.resolve(M2tsTimingManifest.FILE_NAME);
		var timing = M2tsTiming.read(timingPath);
		M2tsTiming.write(timingPath,
				new M2tsTimingManifest(1, timing.sourcePath(), timing.sourceSize(), null, timing.streams()));
		var artifacts = new LinkedHashMap<>(fixture.manifest.artifacts());
		artifacts.put(M2tsTimingManifest.FILE_NAME, relative(timingPath));
		ExtractionCache.write(fixture.directory.resolve(ExtractionCache.FILE_NAME),
				withArtifacts(fixture.manifest, artifacts));
		assertThatThrownBy(() -> ExtractionCache
				.validate(ExtractionCache.reference(fixture.directory.resolve(ExtractionCache.FILE_NAME))))
				.isInstanceOf(IOException.class).hasMessageContaining("Timing/source metadata mismatch");
		Fixture wrongMetadata = fixture();
		wrongMetadata.manifest.info().setTotalPackets(2);
		var reference = publish(wrongMetadata);
		assertThatThrownBy(() -> ExtractionCache.validate(reference)).isInstanceOf(IOException.class)
				.hasMessageContaining("metadata disagrees");
	}

	@Test
	void rejectsCoverageSchemaAndTimingArtifactTampering() throws Exception {
		Fixture fixture = fixture();
		Manifest manifest = fixture.manifest;
		ExtractionCache.write(fixture.directory.resolve(ExtractionCache.FILE_NAME),
				new Manifest(manifest.version(), manifest.targetRoot(), manifest.source(), manifest.clpi(),
						manifest.info(), List.of(0x1100), manifest.artifacts(), manifest.duration90k()));
		var coverage = ExtractionCache.reference(fixture.directory.resolve(ExtractionCache.FILE_NAME));
		assertThatThrownBy(() -> ExtractionCache.validate(coverage)).isInstanceOf(IOException.class)
				.hasMessageContaining("match exactly");
		fixture = fixture();
		manifest = fixture.manifest;
		ExtractionCache.write(fixture.directory.resolve(ExtractionCache.FILE_NAME),
				new Manifest(999, manifest.targetRoot(), manifest.source(), manifest.clpi(), manifest.info(),
						manifest.pids(), manifest.artifacts(), manifest.duration90k()));
		var schema = ExtractionCache.reference(fixture.directory.resolve(ExtractionCache.FILE_NAME));
		assertThatThrownBy(() -> ExtractionCache.validate(schema)).isInstanceOf(IOException.class)
				.hasMessageContaining("schema/version");
		fixture = fixture();
		var timingReference = publish(fixture);
		Files.writeString(fixture.directory.resolve(M2tsTimingManifest.FILE_NAME), "{}");
		assertThatThrownBy(() -> ExtractionCache.validate(timingReference)).isInstanceOf(IOException.class)
				.hasMessageContaining("File identity changed");
	}

	@Test
	void retainsSubMillisecondPrecisionAndAcceptsAbsentFileKeys() throws Exception {
		Fixture fixture = fixture();
		Path es = fixture.directory.resolve("pid_1011.h264");
		Files.setLastModifiedTime(es, FileTime.from(Instant.parse("2026-01-01T00:00:00.123456789Z")));
		Identity actual = ExtractionCache.identity(es);
		assertThat(actual.modified()).isEqualTo(Files.getLastModifiedTime(es).toString());
		var artifacts = new LinkedHashMap<>(fixture.manifest.artifacts());
		artifacts.put("pid_1011.h264", new Identity("pid_1011.h264", actual.size(), actual.modified(), null));
		ExtractionCache.write(fixture.directory.resolve(ExtractionCache.FILE_NAME),
				withArtifacts(fixture.manifest, artifacts));
		var reference = ExtractionCache.reference(fixture.directory.resolve(ExtractionCache.FILE_NAME));
		assertThat(ExtractionCache.validate(reference)).isNotNull();
		Files.setLastModifiedTime(es, FileTime.from(Instant.parse("2026-01-01T00:00:00.123999999Z")));
		if (!Files.getLastModifiedTime(es).toString().equals(actual.modified()))
			assertThatThrownBy(() -> ExtractionCache.validate(reference)).isInstanceOf(IOException.class);
	}

	@Test
	void rejectsArtifactSymlinkAndManifestOverwrite() throws Exception {
		Fixture fixture = fixture();
		var reference = publish(fixture);
		Path artifact = fixture.directory.resolve("pid_1011.h264");
		Path outside = Files.writeString(temp.resolve("outside.h264"), "elementary bytes");
		Files.delete(artifact);
		Files.createSymbolicLink(artifact, outside);
		assertThatThrownBy(() -> ExtractionCache.validate(reference)).isInstanceOf(IOException.class);
		assertThatThrownBy(
				() -> ExtractionCache.write(fixture.directory.resolve(ExtractionCache.FILE_NAME), fixture.manifest))
				.isInstanceOf(IOException.class);
	}

	private ExtractionCache.Reference publish(Fixture fixture) throws IOException {
		Path marker = fixture.directory.resolve(ExtractionCache.FILE_NAME);
		ExtractionCache.write(marker, fixture.manifest);
		return ExtractionCache.reference(marker);
	}

	private Fixture fixture() throws IOException {
		Path source = Files.createTempDirectory(temp, "source-");
		Path target = TargetRootWorkspace.prepare(source, Files.createTempDirectory(temp, "target-"), null);
		Path media = Files.write(source.resolve("00001.m2ts"), new byte[192]).toRealPath();
		Path clpi = Files.writeString(source.resolve("00001.clpi"), "small CLPI");
		Path directory = Files.createDirectory(target.resolve("partial"));
		M2tsStreamInfo stream = new M2tsStreamInfo();
		stream.setPid(0x1011);
		stream.setCodingType(StreamCodingType.H264_AVC);
		stream.setLanguage("eng");
		M2tsInfo info = new M2tsInfo();
		info.setSourcePath(media.toString());
		info.setTotalPackets(1);
		info.setStreams(List.of(stream));
		Path es = Files.writeString(directory.resolve("pid_1011.h264"), "elementary bytes");
		Path timing = directory.resolve(M2tsTimingManifest.FILE_NAME);
		M2tsTiming
				.write(timing,
						new M2tsTimingManifest(2, media.toString(), 192, null,
								List.of(new StreamTiming(0x1011, es.getFileName().toString(), StreamCodingType.H264_AVC,
										0L, null, List.of(new PesTiming(0, 0, 0L, null)), List.of())),
								null, Map.of(0x1011, 1L)));
		return new Fixture(directory, new Manifest(1, target.toString(), ExtractionCache.identity(media),
				ExtractionCache.identity(clpi), info, List.of(0x1011),
				Map.of(es.getFileName().toString(), relative(es), M2tsTimingManifest.FILE_NAME, relative(timing)),
				90_000));
	}

	private static Identity relative(Path path) throws IOException {
		Identity identity = ExtractionCache.identity(path);
		return new Identity(path.getFileName().toString(), identity.size(), identity.modified(), identity.fileKey());
	}

	private static Manifest withArtifacts(Manifest manifest, Map<String, Identity> artifacts) {
		return new Manifest(manifest.version(), manifest.targetRoot(), manifest.source(), manifest.clpi(),
				manifest.info(), manifest.pids(), artifacts, manifest.duration90k());
	}

	private record Fixture(Path directory, Manifest manifest) {
	}
}
