/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsShrinkCache.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.ExtractionCache;
import org.brts.common.m2ts.ExtractionCache.Identity;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.TargetRootWorkspace;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;
import org.brts.common.model.StreamCodingType;

/** Builds immutable, target-local extraction entries, reused independently of bitrate and encoding engine. */
public final class M2tsShrinkCache {
	// Avoid OverlappingFileLockException for same-JVM callers; the file lock coordinates other processes.
	private static final Object[] LOCKS = new Object[64];
	static {
		Arrays.setAll(LOCKS, ignored -> new Object());
	}

	@FunctionalInterface
	public interface Discovery {
		M2tsShrinkInputResolver.Metadata discover(Path discRoot, String clipId) throws IOException;
	}

	@FunctionalInterface
	public interface TimingExtractor {
		M2tsTimingManifest extract(Path source, M2tsInfo info, Path directory, Set<Integer> pids) throws IOException;
	}

	public record Prepared(M2tsShrinkInventory inventory, ExtractionCache.Validated cache) {
	}

	private record Key(int version, int timingVersion, Identity source, Identity clpi, List<Integer> audioPids,
			List<Integer> subtitlePids) {
	}

	private final Discovery discovery;
	private final TimingExtractor extractor;

	public M2tsShrinkCache() {
		this(new M2tsShrinkInputResolver());
	}

	public M2tsShrinkCache(M2tsShrinkInputResolver resolver) {
		this(resolver::discover, (source, info, directory, pids) -> new M2tsExtractor().extractTiming(source, info,
				directory, pids, null));
	}

	public M2tsShrinkCache(Discovery discovery, TimingExtractor extractor) {
		this.discovery = java.util.Objects.requireNonNull(discovery);
		this.extractor = java.util.Objects.requireNonNull(extractor);
	}

	/** A cache hit performs attribute checks and small JSON reads only, never metadata discovery or a source scan. */
	public Prepared prepare(Path discRoot, String clipId, M2tsShrinkConfig config, Path outputRoot, Path workDir)
			throws IOException {
		if (clipId == null || !clipId.matches("[0-9]{5}"))
			throw new IllegalArgumentException("clipId must contain exactly five digits");
		if (config == null)
			throw new IllegalArgumentException("config is required");
		Path sourceRoot = TargetRootWorkspace.canonical(discRoot);
		Path target = TargetRootWorkspace.prepare(sourceRoot, outputRoot, workDir);
		Identity source = ExtractionCache.identity(sourceRoot.resolve("BDMV/STREAM/" + clipId + ".m2ts"));
		Identity clpi = ExtractionCache.identity(sourceRoot.resolve("BDMV/CLIPINF/" + clipId + ".clpi"));
		if (Path.of(source.path()).startsWith(target) || Path.of(clpi.path()).startsWith(target))
			throw new IOException("Source pair aliases the target tree");
		String key = key(new Key(ExtractionCache.VERSION, M2tsTimingManifest.CURRENT_VERSION, source, clpi,
				normalized(config.getAudioPids()), normalized(config.getSubtitlePids())));
		Path parent = target.resolve(".brts-shrink/cache/" + clipId);
		TargetRootWorkspace.directory(target, parent);
		parent = TargetRootWorkspace.requireInside(target, parent);
		Path entry = parent.resolve(key);
		Path lock = parent.resolve(key + ".lock");
		synchronized (LOCKS[Math.floorMod(entry.hashCode(), LOCKS.length)]) {
			// No symlink aliases for locks: different lexical locks must never guard the same entry.
			if (Files.isSymbolicLink(lock))
				throw new IOException("Cache lock must not be a symbolic link: " + lock);
			TargetRootWorkspace.requireInside(target, lock);
			try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
					var held = channel.lock()) {
				if (Files.exists(entry, LinkOption.NOFOLLOW_LINKS))
					return load(entry, target, source, clpi, config);
				M2tsShrinkInputResolver.Metadata metadata = discovery.discover(sourceRoot, clipId);
				M2tsShrinkInventory inventory = metadata.inventory();
				config.validate(inventory);
				Set<Integer> selected = selectedPids(inventory, metadata.info(), config);
				assertIdentity(source);
				assertIdentity(clpi);
				if (!Path.of(source.path()).equals(inventory.m2tsPath().toRealPath())
						|| !Path.of(clpi.path()).equals(inventory.clpiPath().toRealPath())
						|| source.size() != inventory.sourceBytes() || inventory.duration90k() <= 0)
					throw new IOException("Discovery returned inconsistent source metadata");
				// Discovery may have been called using symlinked input filenames. Persist real source identity.
				metadata.info().setSourcePath(source.path());
				Path attempt = Files.createTempDirectory(parent, key + "-partial-");
				TargetRootWorkspace.requireInside(target, attempt);
				M2tsTimingManifest timing = extractor.extract(Path.of(source.path()), metadata.info(), attempt,
						selected);
				assertIdentity(source);
				assertIdentity(clpi);
				Map<String, Identity> artifacts = new LinkedHashMap<>();
				addArtifact(attempt, M2tsTimingManifest.FILE_NAME, artifacts);
				for (var stream : timing.streams())
					addArtifact(attempt, stream.fileName(), artifacts);
				var manifest = new ExtractionCache.Manifest(ExtractionCache.VERSION, target.toString(), source, clpi,
						metadata.info(), List.copyOf(new TreeSet<>(selected)), artifacts, inventory.duration90k());
				Path marker = attempt.resolve(ExtractionCache.FILE_NAME);
				ExtractionCache.write(marker, manifest);
				// Validate BEFORE publication: failures remain visibly incomplete/diagnosable sibling attempts.
				ExtractionCache.validate(ExtractionCache.reference(marker));
				TargetRootWorkspace.requireInside(target, entry);
				if (Files.exists(entry, LinkOption.NOFOLLOW_LINKS))
					throw new IOException("Cache entry appeared during locked build: " + entry);
				// No fallback copy and no REPLACE_EXISTING; all cooperating publishers hold this same lock.
				Files.move(attempt, entry, StandardCopyOption.ATOMIC_MOVE);
				return load(entry, target, source, clpi, config);
			}
		}
	}

	private static Prepared load(Path entry, Path target, Identity source, Identity clpi, M2tsShrinkConfig config)
			throws IOException {
		if (!TargetRootWorkspace.requireInside(target, entry).equals(entry))
			throw new IOException("Cache entry must not be a symbolic link alias: " + entry);
		if (!Files.isRegularFile(entry.resolve(ExtractionCache.FILE_NAME)))
			throw new IOException("Incomplete cache entry; remove the stale entry and replan: " + entry);
		var validated = ExtractionCache.validate(ExtractionCache.reference(entry.resolve(ExtractionCache.FILE_NAME)));
		var manifest = validated.manifest();
		if (!manifest.targetRoot().equals(target.toString()) || !manifest.source().equals(source)
				|| !manifest.clpi().equals(clpi))
			throw new IOException("Cache identity/key mismatch; remove stale entry and replan: " + entry);
		var tracks = manifest.info().getStreams().stream().filter(stream -> stream.getCodingType() != null)
				.map(stream -> new M2tsShrinkInventory.Track(stream.getPid(), stream.getCodingType(),
						stream.getLanguage(), stream.getBitrateKbps(),
						validated.timing().sourcePacketCounts().getOrDefault(stream.getPid(), 0L)))
				.toList();
		var inventory = new M2tsShrinkInventory(Path.of(source.path()), Path.of(clpi.path()), source.size(),
				manifest.duration90k(), tracks);
		config.validate(inventory);
		if (!manifest.pids().equals(List.copyOf(new TreeSet<>(selectedPids(inventory, manifest.info(), config)))))
			throw new IOException("Cached PID selection differs from requested selection: " + entry);
		return new Prepared(inventory, validated);
	}

	private static Set<Integer> selectedPids(M2tsShrinkInventory inventory, M2tsInfo info, M2tsShrinkConfig config) {
		var videos = inventory.tracks().stream().filter(t -> t.codingType() != null && t.codingType().isVideo())
				.toList();
		var primary = videos.stream().filter(t -> t.pid() == 0x1011).findFirst().orElse(null);
		boolean dolbyVision = videos.size() == 2 && primary != null
				&& primary.codingType() == StreamCodingType.H265_HEVC
				&& videos.stream().anyMatch(t -> t.pid() == 0x1015 && t.codingType() == StreamCodingType.H265_HEVC);
		if (primary == null
				|| !Set.of(StreamCodingType.H264_AVC, StreamCodingType.H265_HEVC).contains(primary.codingType())
				|| (videos.size() != 1 && !dolbyVision))
			throw new IllegalArgumentException("Exactly one primary AVC/HEVC video (PID 0x1011) is required");
		if (info.getStreams() == null || info.getStreams().size() != inventory.tracks().size()
				|| info.getStreams().stream().anyMatch(s -> s.getCodingType() == null || s.getCodingType().isMenu())
				|| (info.getProgramPidMap() != null && info.getProgramPidMap().size() > 1))
			throw new IllegalArgumentException(
					"Unknown/interactive streams or multiple transport programs are unsupported");
		var selected = new TreeSet<Integer>();
		selected.add(primary.pid());
		for (var track : inventory.tracks()) {
			if (track.codingType().isAudio()
					&& (config.getAudioPids() == null || config.getAudioPids().contains(track.pid()))
					|| track.codingType().isSubtitle()
							&& (config.getSubtitlePids() == null || config.getSubtitlePids().contains(track.pid())))
				selected.add(track.pid());
		}
		return Set.copyOf(selected);
	}

	private static void addArtifact(Path directory, String name, Map<String, Identity> artifacts) throws IOException {
		if (name == null || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals(".."))
			throw new IOException("Invalid extraction artifact basename: " + name);
		Identity identity = ExtractionCache.identity(directory.resolve(name));
		if (artifacts.put(name, new Identity(name, identity.size(), identity.modified(), identity.fileKey())) != null)
			throw new IOException("Duplicate extraction artifact: " + name);
	}

	private static void assertIdentity(Identity expected) throws IOException {
		if (!ExtractionCache.identity(Path.of(expected.path())).equals(expected))
			throw new IOException("Source changed during cache preparation: " + expected.path());
	}

	private static List<Integer> normalized(List<Integer> pids) {
		if (pids == null)
			return null;
		if (pids.stream().anyMatch(pid -> pid == null || pid < 0x20 || pid > 0x1FFE)
				|| new java.util.HashSet<>(pids).size() != pids.size())
			throw new IllegalArgumentException("Selection must contain unique PIDs in 0x20..0x1FFE");
		return pids.stream().sorted().toList();
	}

	private static String key(Key metadata) throws IOException {
		try {
			// SHA-256 is only over a tiny metadata document, never over the source media.
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(JsonMapperFactory.get().writeValueAsBytes(metadata)));
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 metadata key unavailable", e);
		}
	}
}
