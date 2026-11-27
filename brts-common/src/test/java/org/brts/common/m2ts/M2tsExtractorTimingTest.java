/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/test/java/org/brts/common/m2ts/M2tsExtractorTimingTest.java' is part of BRTS.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsExtractorTimingTest {
	@TempDir
	Path temp;

	@Test
	void compactTimingJsonUsesShortNamesWithoutChangingOtherDescriptorFormatting() throws Exception {
		long pts = (1L << 33) + 90_000;
		var video = new M2tsTimingManifest.StreamTiming(0x1011, "video.h264", StreamCodingType.H264_AVC, pts, pts - 90,
				List.of(new M2tsTimingManifest.PesTiming(0, 0, pts, pts - 90),
						new M2tsTimingManifest.PesTiming(1, 100, null, null)),
				List.of(),
				new M2tsTimingManifest.VideoScan(pts, pts, List.of(new M2tsTimingManifest.EntryPoint(pts, 0)), 0, 0));
		var pgs = new M2tsTimingManifest.StreamTiming(0x1200, "subtitle.pgs", StreamCodingType.PRESENTATION_GRAPHICS,
				pts, null, List.of(new M2tsTimingManifest.PesTiming(2, 0, pts, null)),
				List.of(new M2tsTimingManifest.SegmentTiming(0, 0x80, 0, pts, null)));
		var manifest = new M2tsTimingManifest(M2tsTimingManifest.CURRENT_VERSION,
				temp.resolve("source.m2ts").toString(), 576, null, List.of(video, pgs), null,
				Map.of(0x1011, 2L, 0x1200, 1L));
		String prettyBefore = JsonMapperFactory.get().writeValueAsString(manifest);
		Path path = temp.resolve(M2tsTimingManifest.FILE_NAME);
		M2tsTiming.write(path, manifest);
		String compact = Files.readString(path);
		assertThat(compact).doesNotContain("\n", "\r", "\"packetIndex\"", "\"outputOffset\"", "\"pts90k\"",
				"\"dts90k\"", "\"spn\"");
		assertThat(compact.length()).isLessThan(prettyBefore.length());
		assertThat(M2tsTiming.read(path)).isEqualTo(manifest);
		var json = JsonMapperFactory.get().readTree(compact);
		assertThat(json.path("version").asInt()).isEqualTo(4);
		var videoJson = json.path("streams").get(0);
		assertThat(videoJson.path("pesTimings").get(0).properties()).extracting(Map.Entry::getKey)
				.containsExactlyInAnyOrder("i", "o", "p", "d");
		assertThat(videoJson.path("pesTimings").get(1).properties()).extracting(Map.Entry::getKey)
				.containsExactlyInAnyOrder("i", "o");
		assertThat(videoJson.path("videoScan").path("entries").get(0).properties()).extracting(Map.Entry::getKey)
				.containsExactlyInAnyOrder("p", "s");
		assertThat(json.path("streams").get(1).path("segments").get(0).properties()).extracting(Map.Entry::getKey)
				.containsExactlyInAnyOrder("o", "t", "s", "p");
		assertThat(prettyBefore).contains("\n");
		assertThat(JsonMapperFactory.get().writeValueAsString(manifest)).isEqualTo(prettyBefore);
	}

	@Test
	void shortTimingPropertiesPreserveNullZeroNegativeAndFullWidthTimestamps() throws Exception {
		for (Long timestamp : Arrays.asList(null, 0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, (1L << 33) + 123)) {
			var pes = new M2tsTimingManifest.PesTiming(Long.MAX_VALUE, Long.MAX_VALUE, timestamp, timestamp);
			String json = JsonMapperFactory.get().writeValueAsString(pes);
			assertThat(JsonMapperFactory.get().readValue(json, M2tsTimingManifest.PesTiming.class)).isEqualTo(pes);
			var segment = new M2tsTimingManifest.SegmentTiming(Long.MAX_VALUE, 0x80, 0,
					timestamp == null ? 0 : timestamp, timestamp);
			json = JsonMapperFactory.get().writeValueAsString(segment);
			assertThat(JsonMapperFactory.get().readValue(json, M2tsTimingManifest.SegmentTiming.class))
					.isEqualTo(segment);
		}
	}

	@Test
	void oldLongNameRecordsAreRejectedInsteadOfSilentlyDefaultingPrimitiveFields() {
		assertThatThrownBy(() -> JsonMapperFactory.get().readValue(
				"{\"packetIndex\":7,\"outputOffset\":12,\"pts90k\":90000}", M2tsTimingManifest.PesTiming.class))
				.isInstanceOf(IOException.class).hasMessageContaining("Missing required creator property");
		assertThatThrownBy(() -> JsonMapperFactory.get().readValue(
				"{\"outputOffset\":12,\"type\":128,\"size\":0,\"pts90k\":90000}",
				M2tsTimingManifest.SegmentTiming.class)).isInstanceOf(IOException.class)
				.hasMessageContaining("Missing required creator property");
		assertThatThrownBy(() -> JsonMapperFactory.get().readValue("{\"pts90k\":90000,\"spn\":7}",
				M2tsTimingManifest.EntryPoint.class)).isInstanceOf(IOException.class)
				.hasMessageContaining("Missing required creator property");
	}

	@Test
	void sourceDigestBindsManifestAndLegacyJsonRemainsReadable() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1, 2 }, 123L, null, false), 8);
		Path source = Files.write(temp.resolve("source.m2ts"), fixture.bytes.toByteArray());
		Path out = temp.resolve("fingerprinted");
		new M2tsExtractor().extractTiming(source, fixture.info, out, null, SourceFingerprint.sha256(source));
		var manifest = M2tsTiming.read(out.resolve("timing.json"));
		assertThat(manifest.sourceSha256()).isEqualTo(SourceFingerprint.sha256(temp.resolve("source.m2ts")));
		assertThat(manifest.sourceSize()).isEqualTo(Files.size(temp.resolve("source.m2ts")));
		var legacyJson = JsonMapperFactory.get().valueToTree(manifest);
		((com.fasterxml.jackson.databind.node.ObjectNode) legacyJson).remove("sourceSha256");
		assertThat(JsonMapperFactory.get().treeToValue(legacyJson, M2tsTimingManifest.class).sourceSha256()).isNull();
	}

	@Test
	void expectedDigestRejectsSameSizeMutationBeforeAnyWritesForBothModes() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1, 2 }, 123L, null, false), 8);
		Path source = temp.resolve("mutated.m2ts");
		byte[] bytes = fixture.bytes.toByteArray();
		Files.write(source, bytes);
		String expected = SourceFingerprint.sha256(source);
		bytes[0] ^= 1;
		Files.write(source, bytes);
		for (boolean preserve : List.of(false, true)) {
			Path out = temp.resolve("untouched-" + preserve);
			assertThatThrownBy(() -> new M2tsExtractor().extract(source, fixture.info, out, null, preserve, expected))
					.isInstanceOf(IOException.class).hasMessageContaining("SHA-256 mismatch");
			assertThat(out).doesNotExist();
		}
	}

	@Test
	void rejectedFingerprintLeavesExistingExtractionAndManifestUntouched() throws Exception {
		Path source = Files.writeString(temp.resolve("stale.m2ts"), "changed");
		Path out = Files.createDirectory(temp.resolve("existing"));
		Path es = Files.writeString(out.resolve("pid_1100.ac3"), "old media");
		Path manifest = Files.writeString(out.resolve("timing.json"), "old manifest");
		assertThatThrownBy(() -> new M2tsExtractor().extract(source, new M2tsInfo(), out, null, true, "0".repeat(64)))
				.isInstanceOf(IOException.class).hasMessageContaining("SHA-256 mismatch");
		assertThat(Files.readString(es)).isEqualTo("old media");
		assertThat(Files.readString(manifest)).isEqualTo("old manifest");
	}

	@Test
	void fingerprintCoversUnselectedTransportPackets() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1102, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1 }, 123L, null, false), 8);
		fixture.pes(0x1102, pes(new byte[] { 2 }, 123L, null, false), 8);
		Path source = Files.write(temp.resolve("all-packets.m2ts"), fixture.bytes.toByteArray());
		Path out = temp.resolve("one-pid");
		new M2tsExtractor().extract(source, fixture.info, out, Set.of(0x1100), true, SourceFingerprint.sha256(source));
		var manifest = M2tsTiming.read(out.resolve("timing.json"));
		assertThat(manifest.sourceSha256()).isEqualTo(SourceFingerprint.sha256(source));
		assertThat(manifest.streams()).extracting(s -> s.pid()).containsExactly(0x1100);
	}

	@Test
	void integratedCountsIncludeUnselectedPsiNullAndAdaptationPacketsWithTimingParity() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1102, StreamCodingType.DOLBY_AC3));
		fixture.info.setPcrPid(0x1001);
		fixture.packet(0, new byte[] { 0 }, true, true); // PAT
		fixture.packet(0x100, new byte[] { 0 }, true, true); // PMT
		fixture.packet(0x1F, new byte[] { 0 }, true, true); // SIT
		fixture.packet(0x1FFF, new byte[184], false, true); // null
		fixture.pcr(90);
		fixture.adaptationOnly(0x1100); // selected, no payload or continuity increment
		fixture.pes(0x1100, pes(new byte[] { 1, 2 }, 123L, null, false), 8);
		fixture.adaptationOnly(0x1100);
		fixture.pes(0x1102, pes(new byte[] { 3 }, 456L, null, false), 8);
		fixture.adaptationOnly(0x1102); // unselected
		fixture.adaptationOnly(0x1FFF);
		Path source = Files.write(temp.resolve("counts.m2ts"), fixture.bytes.toByteArray());
		Path out = temp.resolve("counts");
		M2tsTimingManifest selected = new M2tsExtractor().extractTiming(source, fixture.info, out, Set.of(0x1100),
				null);
		assertThat(selected.version()).isEqualTo(M2tsTimingManifest.CURRENT_VERSION);
		assertThat(selected.sourceSha256()).isNull(); // hashing stays opt-in
		assertThat(selected.sourcePacketCounts()).containsExactlyInAnyOrderEntriesOf(
				Map.of(0, 1L, 0x100, 1L, 0x1F, 1L, 0x1FFF, 2L, 0x1001, 1L, 0x1100, 4L, 0x1102, 3L));
		// Independent packet-only scan, matching the standalone counter without a common -> low-level dependency.
		Map<Integer, Long> counted = new HashMap<>();
		byte[] transport = Files.readAllBytes(source);
		for (int offset = 0; offset < transport.length; offset += 192) {
			assertThat(transport[offset + 4]).isEqualTo((byte) 0x47);
			int pid = ((transport[offset + 5] & 0x1F) << 8) | (transport[offset + 6] & 0xFF);
			counted.merge(pid, 1L, Long::sum);
		}
		assertThat(selected.sourcePacketCounts()).isEqualTo(counted);
		assertThat(selected.sourcePacketCounts().values().stream().mapToLong(Long::longValue).sum() * 192)
				.isEqualTo(Files.size(source));
		assertThatThrownBy(() -> selected.sourcePacketCounts().put(7, 1L))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThat(M2tsTiming.read(out.resolve(M2tsTimingManifest.FILE_NAME))).isEqualTo(selected);
		assertThat(out.resolve("pid_1102.ac3")).doesNotExist();
		assertThat(Files.readAllBytes(out.resolve("pid_1100.ac3"))).containsExactly((byte) 1, (byte) 2);
		M2tsTimingManifest all = new M2tsExtractor().extractTiming(source, fixture.info, temp.resolve("all"), null,
				null);
		assertThat(all.sourcePacketCounts()).isEqualTo(selected.sourcePacketCounts());
		assertThat(all.firstPcr90k()).isEqualTo(selected.firstPcr90k()).isEqualTo(90L);
		assertThat(all.streams().getFirst()).isEqualTo(selected.streams().getFirst());
		assertThat(selected.streams().getFirst().pesTimings().getFirst().packetIndex()).isEqualTo(6L);
	}

	@Test
	void legacyConstructorsAndVersionOneJsonWithoutCountsRemainReadable() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 7 }, 123L, null, false), 184);
		Path out = extract(fixture, "legacy-contract", true);
		M2tsTimingManifest current = M2tsTiming.read(out.resolve(M2tsTimingManifest.FILE_NAME));
		M2tsTimingManifest five = new M2tsTimingManifest(1, current.sourcePath(), current.sourceSize(),
				current.firstPcr90k(), current.streams());
		M2tsTimingManifest six = new M2tsTimingManifest(1, current.sourcePath(), current.sourceSize(),
				current.firstPcr90k(), current.streams(), "a".repeat(64));
		assertThat(five.sourcePacketCounts()).isEmpty();
		assertThat(five.sourceSha256()).isNull();
		assertThat(six.sourcePacketCounts()).isEmpty();
		for (M2tsTimingManifest legacy : List.of(five, six)) {
			var json = JsonMapperFactory.get().valueToTree(legacy);
			((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("sourcePacketCounts");
			if (legacy.sourceSha256() == null)
				((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("sourceSha256");
			Path path = temp.resolve("v1-" + (legacy.sourceSha256() == null) + ".json");
			JsonMapperFactory.get().writeValue(path.toFile(), json);
			assertThat(M2tsTiming.read(path)).isEqualTo(legacy);
		}
		Map<Integer, Long> mutable = new HashMap<>(current.sourcePacketCounts());
		M2tsTimingManifest copied = new M2tsTimingManifest(2, current.sourcePath(), current.sourceSize(),
				current.firstPcr90k(), current.streams(), null, mutable);
		mutable.clear();
		assertThat(copied.sourcePacketCounts()).isEqualTo(current.sourcePacketCounts());
	}

	@Test
	void versionTwoRejectsInvalidCountsAndCheckedArithmeticOverflow() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 7 }, 123L, null, false), 184);
		Path out = extract(fixture, "validation", true);
		M2tsTimingManifest current = M2tsTiming.read(out.resolve(M2tsTimingManifest.FILE_NAME));
		List<Map<Integer, Long>> invalid = List.of(Map.of(-1, 1L), Map.of(8192, 1L), Map.of(0, -1L), Map.of(),
				Map.of(0, 2L), Map.of(0, Long.MAX_VALUE), Map.of(0, Long.MAX_VALUE, 1, 1L));
		for (Map<Integer, Long> counts : invalid) {
			M2tsTimingManifest bad = new M2tsTimingManifest(2, current.sourcePath(), current.sourceSize(),
					current.firstPcr90k(), current.streams(), null, counts);
			Path path = temp.resolve("invalid-counts.json");
			JsonMapperFactory.get().writeValue(path.toFile(), bad);
			assertThatThrownBy(() -> M2tsTiming.read(path)).isInstanceOf(IOException.class)
					.hasMessageContaining("packet");
			assertThatThrownBy(() -> M2tsTiming.write(temp.resolve("not-written.json"), bad))
					.isInstanceOf(IOException.class);
			assertThat(temp.resolve("not-written.json")).doesNotExist();
		}
		var missing = JsonMapperFactory.get().valueToTree(current);
		((com.fasterxml.jackson.databind.node.ObjectNode) missing).remove("sourcePacketCounts");
		Path path = temp.resolve("missing-counts.json");
		JsonMapperFactory.get().writeValue(path.toFile(), missing);
		assertThatThrownBy(() -> M2tsTiming.read(path)).isInstanceOf(IOException.class);
		M2tsTimingManifest boundaries = new M2tsTimingManifest(2, current.sourcePath(), 192, current.firstPcr90k(),
				current.streams(), null, Map.of(0, 0L, 8191, 1L));
		M2tsTiming.write(path, boundaries);
		assertThat(M2tsTiming.read(path)).isEqualTo(boundaries);
	}

	@Test
	void versionThreeRejectsEntryPointOutsideSourcePacketRange() throws Exception {
		var stream = new M2tsTimingManifest.StreamTiming(0x1011, "video.h264", StreamCodingType.H264_AVC, 90_000L, null,
				List.of(new M2tsTimingManifest.PesTiming(0, 0, 90_000L, null)), List.of(),
				new M2tsTimingManifest.VideoScan(90_000L, 90_000L,
						List.of(new M2tsTimingManifest.EntryPoint(90_000L, 1)), 0, 0));
		var invalid = new M2tsTimingManifest(3, temp.resolve("source.m2ts").toString(), 192, null, List.of(stream),
				null, Map.of(0x1011, 1L));
		assertThatThrownBy(() -> M2tsTiming.write(temp.resolve("bad-ep.json"), invalid)).isInstanceOf(IOException.class)
				.hasMessageContaining("Invalid video EP entry");
	}

	@Test
	void unselectedLostSyncAndTruncatedTailNeverPublishCounts() throws Exception {
		for (boolean truncated : List.of(false, true)) {
			Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
			fixture.pes(0x1100, pes(new byte[] { 1 }, 123L, null, false), 184);
			fixture.packet(0x1FFF, new byte[184], false, true);
			byte[] bytes = fixture.bytes.toByteArray();
			if (truncated)
				bytes = Arrays.copyOf(bytes, bytes.length - 1);
			else
				bytes[192 + 4] = 0;
			Path source = Files.write(temp.resolve("bad-tail.m2ts"), bytes);
			Path out = Files.createDirectory(temp.resolve("bad-tail-" + truncated));
			Files.writeString(out.resolve(M2tsTimingManifest.FILE_NAME), "stale");
			assertThatThrownBy(() -> new M2tsExtractor().extractTiming(source, fixture.info, out, Set.of(0x1100), null))
					.isInstanceOf(IOException.class);
			assertThat(out.resolve(M2tsTimingManifest.FILE_NAME)).doesNotExist();
		}
	}

	@Test
	void matchingExpectedDigestExtractsAndPublishesIdentity() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 7 }, 123L, null, false), 8);
		Path source = temp.resolve("matching.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		String expected = SourceFingerprint.sha256(source);
		Path out = temp.resolve("matching");
		new M2tsExtractor().extract(source, fixture.info, out, null, true, expected);
		assertThat(M2tsTiming.read(out.resolve("timing.json")).sourceSha256()).isEqualTo(expected);
	}

	@Test
	void splitPesHeaderAtEveryPositionAndExcludeBoundedStuffing() throws Exception {
		byte[] elementary = { 1, 2, (byte) 0xFF, 3 };
		byte[] pes = pes(elementary, 123456L, 123000L, false);
		for (int split = 1; split < 19; split++) {
			Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
			fixture.packet(0x1100, Arrays.copyOf(pes, split), true, false);
			fixture.packet(0x1100, Arrays.copyOfRange(pes, split, pes.length), false, true);
			Path out = extract(fixture, "split-" + split, true);
			assertThat(Files.readAllBytes(out.resolve("pid_1100.ac3"))).containsExactly(elementary);
			var timing = M2tsTiming.read(out.resolve("timing.json")).streams().getFirst();
			assertThat(timing.firstPts90k()).isEqualTo(123456L);
			assertThat(timing.firstDts90k()).isEqualTo(123000L);
			assertThat(timing.pesTimings()).hasSize(1);
			assertThat(timing.pesTimings().getFirst().packetIndex()).isZero();
		}
	}

	@Test
	void pgsSegmentsSpanPesAndTsBoundariesAndKeepFullAbsoluteTiming() throws Exception {
		Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		byte[] body = new byte[300];
		for (int i = 0; i < body.length; i++)
			body[i] = (byte) i;
		byte[] segment = concat(new byte[] { 0x15, 1, 44 }, body);
		long pts = (1L << 32) + 900000;
		long dts = pts - 90;
		// Even the 3-byte PGS segment header straddles PES boundaries.
		fixture.pes(0x1200, pes(Arrays.copyOf(segment, 2), pts, dts, false), 5);
		fixture.pes(0x1200,
				pes(concat(Arrays.copyOfRange(segment, 2, segment.length), new byte[] { (byte) 0x80, 0, 0 }),
						pts + 9000, dts + 9000, false),
				7);
		Path out = extract(fixture, "pgs", true);
		byte[] sup = Files.readAllBytes(out.resolve("pid_1200.pgs"));
		try (DataInputStream records = new DataInputStream(new ByteArrayInputStream(sup))) {
			assertSup(records, pts, dts, 0x15, body);
			assertSup(records, pts + 9000, dts + 9000, 0x80, new byte[0]);
			assertThat(records.available()).isZero();
		}
		M2tsTimingManifest manifest = M2tsTiming.read(out.resolve("timing.json"));
		var timing = manifest.streams().getFirst();
		assertThat(timing.fileName()).isEqualTo("pid_1200.pgs");
		assertThat(timing.segments()).hasSize(2);
		assertThat(timing.segments().getFirst().pts90k()).isEqualTo(pts);
		assertThat(timing.segments().getFirst().dts90k()).isEqualTo(dts);
		assertThat(timing.segments().get(1).outputOffset()).isEqualTo(313);
		assertThat(timing.segments().get(1).pts90k()).isEqualTo(pts + 9000);
		assertThat(JsonMapperFactory.get().readValue(JsonMapperFactory.get().writeValueAsBytes(manifest),
				M2tsTimingManifest.class)).isEqualTo(manifest);
	}

	@Test
	void unwrapsAcrossPidsAndKeepsPtsAndDtsInSameEpoch() throws Exception {
		long wrap = M2tsTiming.TIMESTAMP_WRAP;
		Fixture fixture = new Fixture(stream(0x1011, StreamCodingType.H264_AVC),
				stream(0x1102, StreamCodingType.DOLBY_AC3), stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		fixture.pes(0x1011, pes(new byte[] { 1 }, wrap - 100, wrap - 200, true), 4);
		fixture.pes(0x1011, pes(new byte[] { 2 }, 100L, 0L, true), 6);
		fixture.pes(0x1102, pes(new byte[] { 3 }, 50L, null, false), 3);
		fixture.pes(0x1200, pes(new byte[] { (byte) 0x80, 0, 0 }, 200L, 190L, false), 9);
		Path out = extract(fixture, "wrap", true);
		var timings = M2tsTiming.read(out.resolve("timing.json")).streams();
		assertThat(timings.get(0).pesTimings().get(1).pts90k()).isEqualTo(wrap + 100);
		assertThat(timings.get(0).pesTimings().get(1).dts90k()).isEqualTo(wrap);
		assertThat(timings.get(1).firstPts90k()).isEqualTo(wrap + 50);
		assertThat(timings.get(1).firstDts90k()).isNull();
		assertThat(timings.get(2).segments().getFirst().pts90k()).isEqualTo(wrap + 200);
		assertThat(timings.get(2).segments().getFirst().dts90k()).isEqualTo(wrap + 190);
	}

	@Test
	void timingPreservingExtractionCapturesClpiCompatibleVideoEntryPoints() throws Exception {
		Fixture fixture = new Fixture(stream(0x1011, StreamCodingType.H264_AVC));
		fixture.pes(0x1011, pes(new byte[] { 0, 0, 1, 0x65, (byte) 0x80 }, 90_000L, null, true), 8);
		Path out = extract(fixture, "video-ep", true);
		var manifest = M2tsTiming.read(out.resolve(M2tsTimingManifest.FILE_NAME));
		var timing = manifest.streams().getFirst();
		assertThat(timing.videoScan()).isNotNull();
		assertThat(timing.videoScan().firstPts90k()).isEqualTo(90_000L);
		assertThat(timing.videoScan().lastPts90k()).isEqualTo(90_000L);
		assertThat(timing.videoScan().entries()).containsExactly(new M2tsTimingManifest.EntryPoint(90_000L, 0L));
		assertThat(M2tsTiming.read(out.resolve(M2tsTimingManifest.FILE_NAME))).isEqualTo(manifest);
	}

	@Test
	void pcrAnchorsLateFirstSelectedStreamAfterWrap() throws Exception {
		long wrap = M2tsTiming.TIMESTAMP_WRAP;
		Fixture fixture = new Fixture(stream(0x1102, StreamCodingType.DOLBY_AC3));
		fixture.info.setPcrPid(0x1001);
		fixture.pcr(wrap - 200);
		fixture.pcr(10);
		fixture.pes(0x1102, pes(new byte[] { 7 }, 100L, null, false), 8);
		Path out = extract(fixture, "pcr", true);
		var manifest = M2tsTiming.read(out.resolve("timing.json"));
		assertThat(manifest.firstPcr90k()).isEqualTo(wrap - 200);
		assertThat(manifest.streams().getFirst().firstPts90k()).isEqualTo(wrap + 100);
		assertThat(Files.readAllBytes(out.resolve("pid_1102.ac3"))).containsExactly((byte) 7);
	}

	@Test
	void absentPgsDtsUsesSupZeroButManifestRetainsAbsence() throws Exception {
		Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		fixture.pes(0x1200, pes(new byte[] { (byte) 0x80, 0, 0 }, 987654L, null, false), 2);
		Path out = extract(fixture, "no-dts", true);
		try (DataInputStream in = new DataInputStream(Files.newInputStream(out.resolve("pid_1200.pgs")))) {
			assertSup(in, 987654L, 0L, 0x80, new byte[0]);
		}
		var timing = M2tsTiming.read(out.resolve("timing.json")).streams().getFirst();
		assertThat(timing.firstDts90k()).isNull();
		assertThat(timing.segments().getFirst().dts90k()).isNull();
	}

	@Test
	void legacyExtractionAndFalseOverloadRemainRawIncludingLegacyStuffing() throws Exception {
		Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		byte[] raw = { (byte) 0x80, 0, 0 };
		byte[] pes = pes(raw, 500L, null, false);
		fixture.packet(0x1200, pes, true, true);
		Path source = temp.resolve("legacy.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		Path legacy = temp.resolve("legacy-out");
		new M2tsExtractor().extract(source, fixture.info, legacy);
		Path explicitFalse = temp.resolve("false-out");
		new M2tsExtractor().extract(source, fixture.info, explicitFalse, Set.of(0x1200), false);
		byte[] expected = new byte[184 - 14];
		Arrays.fill(expected, (byte) 0xFF);
		System.arraycopy(raw, 0, expected, 0, raw.length);
		assertThat(Files.readAllBytes(legacy.resolve("pid_1200.pgs"))).containsExactly(expected);
		assertThat(Files.readAllBytes(explicitFalse.resolve("pid_1200.pgs"))).containsExactly(expected);
		assertThat(legacy.resolve("timing.json")).doesNotExist();
		assertThat(explicitFalse.resolve("timing.json")).doesNotExist();
	}

	@Test
	void keepsOnlyRequestedSparsePidAndRejectsUnknownPid() throws Exception {
		Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1102, StreamCodingType.DOLBY_AC3));
		fixture.pes(0x1100, pes(new byte[] { 1 }, 10L, null, false), 3);
		fixture.pes(0x1102, pes(new byte[] { 2 }, 20L, null, false), 3);
		Path source = temp.resolve("selected.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		Path out = temp.resolve("selected");
		new M2tsExtractor().extract(source, fixture.info, out, Set.of(0x1102), true);
		assertThat(out.resolve("pid_1100.ac3")).doesNotExist();
		assertThat(M2tsTiming.read(out.resolve("timing.json")).streams()).extracting(s -> s.pid())
				.containsExactly(0x1102);
		assertThatThrownBy(() -> new M2tsExtractor().extract(source, fixture.info, out, Set.of(0x1103), true))
				.isInstanceOf(IOException.class).hasMessageContaining("requested PID");
		assertThat(out.resolve("timing.json")).doesNotExist();
	}

	@Test
	void malformedHeadersAndTruncatedPesFailWithoutManifestEvenAfterSuccessfulRun() throws Exception {
		byte[] good = pes(new byte[] { 1, 2, 3 }, 12345L, null, false);
		for (int malformed = 0; malformed < 8; malformed++) {
			Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
			byte[] bad = good.clone();
			switch (malformed) {
			case 0 -> bad[9] &= (byte) 0xFE; // broken PTS marker
			case 1 -> bad[9] = (byte) ((bad[9] & 0x0F) | 0x30); // wrong PTS prefix
			case 2 -> bad[7] = 0x40; // illegal PTS_DTS flags
			case 3 -> bad[8] = 4; // insufficient timestamp header bytes
			case 4 -> bad[5] = 1; // declared length smaller than header
			case 5 -> bad = Arrays.copyOf(bad, 10); // truncated optional header
			case 6 -> bad = Arrays.copyOf(bad, bad.length - 1); // truncated bounded payload
			case 7 -> bad = concat(bad, new byte[] { 42 }); // nonstuffing tail
			default -> throw new AssertionError();
			}
			fixture.packet(0x1100, bad, true, false);
			Path out = temp.resolve("malformed-" + malformed);
			Files.createDirectories(out);
			Files.writeString(out.resolve("timing.json"), "old-success");
			assertThatThrownBy(() -> extractTo(fixture, out, true)).isInstanceOf(IOException.class);
			assertThat(out.resolve("timing.json")).doesNotExist();
		}
	}

	@Test
	void unfinishedPgsSegmentAndMissingTimingAreRejected() throws Exception {
		for (byte[] payload : List.of(new byte[] { 0x15, 0 }, new byte[] { 0x15, 0, 5, 1 },
				new byte[] { 0x55, 0, 0 })) {
			Fixture fixture = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
			fixture.pes(0x1200, pes(payload, 10L, null, false), 4);
			assertThatThrownBy(() -> extract(fixture, "incomplete-pgs", true)).isInstanceOf(IOException.class);
			assertThat(temp.resolve("incomplete-pgs/timing.json")).doesNotExist();
		}
		Fixture untimed = new Fixture(stream(0x1200, StreamCodingType.PRESENTATION_GRAPHICS));
		untimed.pes(0x1200, pes(new byte[] { (byte) 0x80, 0, 0 }, null, null, false), 3);
		assertThatThrownBy(() -> extract(untimed, "untimed", true)).isInstanceOf(IOException.class)
				.hasMessageContaining("no source PTS");
	}

	@Test
	void backwardsDtsAndTransportErrorsFailClosed() throws Exception {
		Fixture backwards = new Fixture(stream(0x1011, StreamCodingType.H264_AVC));
		backwards.pes(0x1011, pes(new byte[] { 1 }, 1000L, 900L, false), 184);
		backwards.pes(0x1011, pes(new byte[] { 2 }, 1100L, 800L, false), 184);
		assertThatThrownBy(() -> extract(backwards, "backwards", true)).isInstanceOf(IOException.class)
				.hasMessageContaining("Backward DTS");
		for (int malformed = 0; malformed < 6; malformed++) {
			Fixture fixture = new Fixture(stream(0x1100, StreamCodingType.DOLBY_AC3));
			fixture.pes(0x1100, pes(new byte[] { 1 }, 1000L, null, false), 7);
			byte[] bytes = fixture.bytes.toByteArray();
			switch (malformed) {
			case 0 -> bytes[5] |= (byte) 0x80; // TEI
			case 1 -> bytes[7] |= (byte) 0x80; // scrambling
			case 2 -> bytes[9] |= (byte) 0x80; // discontinuity
			case 3 -> bytes[192 + 7] = (byte) ((bytes[192 + 7] & 0xF0) | 7); // lost packet
			case 4 -> bytes[4] = 0; // sync
			case 5 -> bytes = Arrays.copyOf(bytes, bytes.length - 1); // partial TS
			default -> throw new AssertionError();
			}
			fixture.bytes.reset();
			fixture.bytes.write(bytes);
			String directory = "bad-transport-" + malformed;
			assertThatThrownBy(() -> extract(fixture, directory, true)).isInstanceOf(IOException.class);
			assertThat(temp.resolve(directory).resolve("timing.json")).doesNotExist();
		}
	}

	@Test
	void unwrapHelperRejectsAmbiguousEpochAndManifestRejectsUnsupportedVersion() throws Exception {
		assertThat(M2tsTiming.unwrap33(5, M2tsTiming.TIMESTAMP_WRAP - 5)).isEqualTo(M2tsTiming.TIMESTAMP_WRAP + 5);
		assertThatThrownBy(() -> M2tsTiming.unwrap33(1L << 32, 0)).isInstanceOf(IllegalArgumentException.class);
		Path invalid = temp.resolve("unsupported.json");
		Files.writeString(invalid, "{\"version\":999}");
		assertThatThrownBy(() -> M2tsTiming.read(invalid)).isInstanceOf(IOException.class);
	}

	private Path extract(Fixture fixture, String directory, boolean preserve) throws IOException {
		Path out = temp.resolve(directory);
		extractTo(fixture, out, preserve);
		return out;
	}

	private void extractTo(Fixture fixture, Path out, boolean preserve) throws IOException {
		Path source = temp.resolve("source.m2ts");
		Files.write(source, fixture.bytes.toByteArray());
		new M2tsExtractor().extract(source, fixture.info, out, preserve);
	}

	private static void assertSup(DataInputStream in, long pts, long dts, int type, byte[] data) throws IOException {
		assertThat(in.readUnsignedShort()).isEqualTo(0x5047);
		assertThat(Integer.toUnsignedLong(in.readInt())).isEqualTo(pts & 0xFFFF_FFFFL);
		assertThat(Integer.toUnsignedLong(in.readInt())).isEqualTo(dts & 0xFFFF_FFFFL);
		assertThat(in.readUnsignedByte()).isEqualTo(type);
		assertThat(in.readUnsignedShort()).isEqualTo(data.length);
		assertThat(in.readNBytes(data.length)).containsExactly(data);
	}

	private static M2tsStreamInfo stream(int pid, StreamCodingType type) {
		M2tsStreamInfo stream = new M2tsStreamInfo();
		stream.setPid(pid);
		stream.setCodingType(type);
		return stream;
	}

	private static byte[] pes(byte[] data, Long pts, Long dts, boolean unbounded) {
		int optional = pts == null ? 0 : dts == null ? 5 : 10;
		byte[] result = new byte[9 + optional + data.length];
		result[2] = 1;
		result[3] = (byte) (unbounded ? 0xE0 : 0xBD);
		int size = unbounded ? 0 : 3 + optional + data.length;
		result[4] = (byte) (size >> 8);
		result[5] = (byte) size;
		result[6] = (byte) 0x80;
		result[7] = (byte) (pts == null ? 0 : dts == null ? 0x80 : 0xC0);
		result[8] = (byte) optional;
		if (pts != null)
			System.arraycopy(timestamp(pts, dts == null ? 2 : 3), 0, result, 9, 5);
		if (dts != null)
			System.arraycopy(timestamp(dts, 1), 0, result, 14, 5);
		System.arraycopy(data, 0, result, 9 + optional, data.length);
		return result;
	}

	private static byte[] timestamp(long value, int prefix) {
		return new byte[] { (byte) ((prefix << 4) | ((value >> 29) & 0x0E) | 1), (byte) (value >> 22),
				(byte) (((value >> 14) & 0xFE) | 1), (byte) (value >> 7), (byte) (((value << 1) & 0xFE) | 1) };
	}

	private static byte[] concat(byte[] first, byte[] second) {
		byte[] result = Arrays.copyOf(first, first.length + second.length);
		System.arraycopy(second, 0, result, first.length, second.length);
		return result;
	}

	private static final class Fixture {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final M2tsInfo info = new M2tsInfo();
		final Map<Integer, Integer> continuity = new HashMap<>();

		Fixture(M2tsStreamInfo... streams) {
			info.setStreams(List.of(streams));
		}

		void pes(int pid, byte[] pes, int firstChunk) {
			int offset = 0;
			while (offset < pes.length) {
				int size = Math.min(offset == 0 ? firstChunk : 184, pes.length - offset);
				packet(pid, Arrays.copyOfRange(pes, offset, offset + size), offset == 0, false);
				offset += size;
			}
		}

		void packet(int pid, byte[] payload, boolean start, boolean stuffing) {
			byte[] packet = new byte[192];
			Arrays.fill(packet, (byte) 0xFF);
			Arrays.fill(packet, 0, 4, (byte) 0);
			packet[4] = 0x47;
			packet[5] = (byte) ((start ? 0x40 : 0) | (pid >> 8));
			packet[6] = (byte) pid;
			int cc = continuity.getOrDefault(pid, 0);
			continuity.put(pid, (cc + 1) & 15);
			int offset = 8;
			if (stuffing || payload.length == 184) {
				packet[7] = (byte) (0x10 | cc);
			} else {
				packet[7] = (byte) (0x30 | cc);
				int afLength = 183 - payload.length;
				packet[8] = (byte) afLength;
				if (afLength > 0)
					packet[9] = 0;
				offset = 9 + afLength;
			}
			System.arraycopy(payload, 0, packet, offset, payload.length);
			bytes.writeBytes(packet);
		}

		void pcr(long base) {
			byte[] packet = new byte[192];
			Arrays.fill(packet, (byte) 0xFF);
			Arrays.fill(packet, 0, 4, (byte) 0);
			int pid = info.getPcrPid();
			packet[4] = 0x47;
			packet[5] = (byte) (pid >> 8);
			packet[6] = (byte) pid;
			packet[7] = 0x20;
			packet[8] = (byte) 183;
			packet[9] = 0x10;
			packet[10] = (byte) (base >> 25);
			packet[11] = (byte) (base >> 17);
			packet[12] = (byte) (base >> 9);
			packet[13] = (byte) (base >> 1);
			packet[14] = (byte) (((base & 1) << 7) | 0x7E);
			packet[15] = 0;
			bytes.writeBytes(packet);
		}

		void adaptationOnly(int pid) {
			byte[] packet = new byte[192];
			Arrays.fill(packet, (byte) 0xFF);
			Arrays.fill(packet, 0, 4, (byte) 0);
			packet[4] = 0x47;
			packet[5] = (byte) (pid >> 8);
			packet[6] = (byte) pid;
			packet[7] = (byte) (0x20 | ((continuity.getOrDefault(pid, 0) - 1) & 15));
			packet[8] = (byte) 183;
			packet[9] = 0;
			bytes.writeBytes(packet);
		}
	}
}
