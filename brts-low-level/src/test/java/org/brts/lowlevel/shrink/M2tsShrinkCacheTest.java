/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/shrink/M2tsShrinkCacheTest.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.brts.common.m2ts.ExtractionCache;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class M2tsShrinkCacheTest {
	@TempDir
	Path temp;

	@Test
	void hitAvoidsDiscoveryAndExtractionAndRebuildsAllPidInventory() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger discoveries = new AtomicInteger();
		AtomicInteger extractions = new AtomicInteger();
		M2tsShrinkCache cache = cache(fixture, discoveries, extractions);
		M2tsShrinkConfig config = new M2tsShrinkConfig();
		config.setAudioPids(List.of(0x1100));
		var first = cache.prepare(fixture.root, "00001", config, fixture.output, null);
		var second = cache.prepare(fixture.root, "00001", config, fixture.output, null);
		assertThat(discoveries).hasValue(1);
		assertThat(extractions).hasValue(1);
		assertThat(second.cache().reference()).isEqualTo(first.cache().reference());
		assertThat(second.inventory()).isEqualTo(first.inventory());
		assertThat(second.inventory().duration90k()).isEqualTo(180_000);
		assertThat(second.inventory().track(0x1102).sourcePacketCount()).isEqualTo(1);
		assertThat(second.inventory().track(0x1100).language()).isEqualTo("eng");
		assertThat(second.cache().manifest().pids()).containsExactly(0x1011, 0x1100);
		assertThat(second.cache().timing().sourcePacketCounts()).containsEntry(0x1FFF, 1L).hasSize(4);
		assertThat(second.cache().timing().sourceSha256()).isNull();
		assertThat(second.cache().directory().resolve("pid_1102.ac3")).doesNotExist();
		// A new service instance proves there is no RAM-only cache dependency.
		M2tsShrinkCache reopened = new M2tsShrinkCache((root, id) -> {
			throw new AssertionError("Cache hit must not discover source metadata");
		}, (source, info, directory, pids) -> {
			throw new AssertionError("Cache hit must not scan transport");
		});
		assertThat(reopened.prepare(fixture.root, "00001", config, fixture.output, null).inventory())
				.isEqualTo(first.inventory());
	}

	@Test
	void sortsSelectionButDistinguishesNullAndEmptyAndIgnoresBitrateHints() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger discoveries = new AtomicInteger();
		AtomicInteger extractions = new AtomicInteger();
		M2tsShrinkCache cache = cache(fixture, discoveries, extractions);
		M2tsShrinkConfig config = new M2tsShrinkConfig();
		config.setAudioPids(List.of(0x1102, 0x1100));
		var first = cache.prepare(fixture.root, "00001", config, fixture.output, null);
		config.setAudioPids(List.of(0x1100, 0x1102));
		config.getMinimumAudioBitrateKbps().put(0x1100, 192);
		assertThat(cache.prepare(fixture.root, "00001", config, fixture.output, null).cache().reference())
				.isEqualTo(first.cache().reference());
		assertThat(extractions).hasValue(1);
		config.setAudioPids(List.of());
		var empty = cache.prepare(fixture.root, "00001", config, fixture.output, null);
		assertThat(empty.cache().manifest().pids()).containsExactly(0x1011);
		config.setAudioPids(null);
		var all = cache.prepare(fixture.root, "00001", config, fixture.output, null);
		assertThat(all.cache().manifest().pids()).containsExactly(0x1011, 0x1100, 0x1102);
		assertThat(all.cache().directory()).isNotEqualTo(empty.cache().directory());
		assertThat(extractions).hasValue(3);
	}

	@Test
	void corruptCompletedEntryFailsWithoutReprobingOrOverwriting() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger discoveries = new AtomicInteger();
		AtomicInteger extractions = new AtomicInteger();
		M2tsShrinkCache cache = cache(fixture, discoveries, extractions);
		var prepared = cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null);
		Path es = prepared.cache().directory().resolve("pid_1011.h264");
		Files.writeString(es, "corrupt");
		assertThatThrownBy(() -> cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null))
				.isInstanceOf(IOException.class).hasMessageContaining("replan");
		assertThat(Files.readString(es)).isEqualTo("corrupt");
		assertThat(discoveries).hasValue(1);
		assertThat(extractions).hasValue(1);
	}

	@Test
	void changedClpiSelectsNewEntryAndInvalidatesPinnedOldReference() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger discoveries = new AtomicInteger();
		AtomicInteger extractions = new AtomicInteger();
		M2tsShrinkCache cache = cache(fixture, discoveries, extractions);
		var first = cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null);
		Files.writeString(fixture.clpi, "changed CLPI metadata");
		var second = cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null);
		assertThat(second.cache().directory()).isNotEqualTo(first.cache().directory());
		assertThat(extractions).hasValue(2);
		assertThatThrownBy(() -> ExtractionCache.validate(first.cache().reference())).isInstanceOf(IOException.class);
		assertThat(first.cache().directory()).isDirectory();
	}

	@Test
	void sourceMutationDuringExtractionRetainsAttemptButDoesNotPublish() throws Exception {
		Fixture fixture = fixture();
		M2tsShrinkCache cache = new M2tsShrinkCache((root, id) -> metadata(fixture),
				(source, info, directory, pids) -> {
					var timing = new M2tsExtractor().extractTiming(source, info, directory, pids, null);
					Files.setLastModifiedTime(source,
							FileTime.fromMillis(Files.getLastModifiedTime(source).toMillis() + 10_000));
					return timing;
				});
		assertThatThrownBy(() -> cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null))
				.isInstanceOf(IOException.class).hasMessageContaining("Source changed");
		try (var paths = Files.walk(fixture.output)) {
			assertThat(paths.filter(path -> path.getFileName().toString().equals(ExtractionCache.FILE_NAME)).toList())
					.isEmpty();
		}
		try (var paths = Files.list(fixture.output.resolve(".brts-shrink/cache/00001"))) {
			assertThat(paths.filter(Files::isDirectory).map(path -> path.getFileName().toString()).toList()).hasSize(1)
					.allMatch(name -> name.contains("-partial-"));
		}
	}

	@Test
	void concurrentPreparationsPublishOnce() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger discoveries = new AtomicInteger();
		AtomicInteger extractions = new AtomicInteger();
		M2tsShrinkCache cache = cache(fixture, discoveries, extractions);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var one = executor.submit(() -> {
				start.await();
				return cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null);
			});
			var two = executor.submit(() -> {
				start.await();
				return cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null);
			});
			start.countDown();
			assertThat(one.get().cache().reference()).isEqualTo(two.get().cache().reference());
		}
		assertThat(discoveries).hasValue(1);
		assertThat(extractions).hasValue(1);
	}

	@Test
	void rejectsOutsideWorkAndUnsafeDefaultDirectoriesBeforeDiscovery() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger discoveries = new AtomicInteger();
		M2tsShrinkCache cache = cache(fixture, discoveries, new AtomicInteger());
		assertThatThrownBy(() -> cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output,
				temp.resolve("outside-work"))).isInstanceOf(IOException.class);
		Path bdmv = Files.createDirectories(fixture.output.resolve("BDMV"));
		Files.createSymbolicLink(bdmv.resolve("STREAM"), fixture.root);
		assertThatThrownBy(() -> cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null))
				.isInstanceOf(IOException.class);
		assertThat(discoveries).hasValue(0);
	}

	@Test
	void invalidSelectionAndUnsupportedVideoFailBeforeExtraction() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger extractions = new AtomicInteger();
		M2tsShrinkCache cache = cache(fixture, new AtomicInteger(), extractions);
		M2tsShrinkConfig config = new M2tsShrinkConfig();
		config.setAudioPids(List.of(0x1234));
		assertThatThrownBy(() -> cache.prepare(fixture.root, "00001", config, fixture.output, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unknown or non-audio");
		fixture.info.getStreams().getFirst().setCodingType(StreamCodingType.MPEG2_VIDEO);
		assertThatThrownBy(() -> cache.prepare(fixture.root, "00001", new M2tsShrinkConfig(), fixture.output, null))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("primary AVC/HEVC");
		assertThat(extractions).hasValue(0);
	}

	private static M2tsShrinkCache cache(Fixture fixture, AtomicInteger discoveries, AtomicInteger extractions) {
		return new M2tsShrinkCache((root, id) -> {
			discoveries.incrementAndGet();
			return metadata(fixture);
		}, (source, info, directory, pids) -> {
			extractions.incrementAndGet();
			return new M2tsExtractor().extractTiming(source, info, directory, pids, null);
		});
	}

	private static M2tsShrinkInputResolver.Metadata metadata(Fixture fixture) throws IOException {
		var tracks = fixture.info.getStreams().stream().map(stream -> new M2tsShrinkInventory.Track(stream.getPid(),
				stream.getCodingType(), stream.getLanguage(), stream.getBitrateKbps(), 0)).toList();
		return new M2tsShrinkInputResolver.Metadata(
				new M2tsShrinkInventory(fixture.media, fixture.clpi, Files.size(fixture.media), 180_000, tracks),
				fixture.info);
	}

	private Fixture fixture() throws IOException {
		Path root = Files.createDirectory(temp.resolve("source"));
		Path stream = Files.createDirectories(root.resolve("BDMV/STREAM"));
		Path clipinf = Files.createDirectories(root.resolve("BDMV/CLIPINF"));
		Path clpi = Files.writeString(clipinf.resolve("00001.clpi"), "small injected CLPI");
		M2tsInfo info = new M2tsInfo();
		info.setStreams(List.of(stream(0x1011, StreamCodingType.H264_AVC), stream(0x1100, StreamCodingType.DOLBY_AC3),
				stream(0x1102, StreamCodingType.DOLBY_AC3)));
		ByteArrayOutputStream transport = new ByteArrayOutputStream();
		for (var metadata : info.getStreams())
			transport.writeBytes(packet(metadata.getPid(), true));
		transport.writeBytes(packet(0x1FFF, false));
		Path media = Files.write(stream.resolve("00001.m2ts"), transport.toByteArray());
		info.setSourcePath(media.toString());
		info.setTotalPackets(4);
		return new Fixture(root, media, clpi, temp.resolve("output"), info);
	}

	private static M2tsStreamInfo stream(int pid, StreamCodingType type) {
		M2tsStreamInfo stream = new M2tsStreamInfo();
		stream.setPid(pid);
		stream.setCodingType(type);
		stream.setLanguage("eng");
		return stream;
	}

	/** One complete bounded PES with valid PTS=0 and two ES bytes, plus adaptation stuffing. */
	private static byte[] packet(int pid, boolean pes) {
		byte[] packet = new byte[192];
		Arrays.fill(packet, (byte) 0xFF);
		Arrays.fill(packet, 0, 4, (byte) 0);
		packet[4] = 0x47;
		packet[5] = (byte) ((pes ? 0x40 : 0) | pid >> 8);
		packet[6] = (byte) pid;
		if (pes) {
			byte[] payload = { 0, 0, 1, (byte) (pid == 0x1011 ? 0xE0 : 0xBD), 0, 10, (byte) 0x80, (byte) 0x80, 5, 0x21,
					0, 1, 0, 1, 1, 2 };
			packet[7] = 0x30;
			packet[8] = (byte) (183 - payload.length);
			packet[9] = 0;
			System.arraycopy(payload, 0, packet, 192 - payload.length, payload.length);
		} else {
			packet[7] = 0x10;
		}
		return packet;
	}

	private record Fixture(Path root, Path media, Path clpi, Path output, M2tsInfo info) {
	}
}
