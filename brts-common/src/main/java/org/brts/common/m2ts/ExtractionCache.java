/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/ExtractionCache.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.m2ts.model.M2tsTimingManifest;

/** Portable, attribute-pinned immutable extraction cache contract. No media hashing or source probing is performed. */
public final class ExtractionCache {
	public static final int VERSION = 1;
	public static final String FILE_NAME = "cache.json";

	private ExtractionCache() {
	}

	public record Identity(String path, long size, String modified, String fileKey) {
	}

	public record Manifest(int version, String targetRoot, Identity source, Identity clpi, M2tsInfo info,
			List<Integer> pids, Map<String, Identity> artifacts, long duration90k) {
		public Manifest {
			pids = pids == null ? null : List.copyOf(pids);
			artifacts = artifacts == null ? null : Map.copyOf(artifacts);
		}
	}

	public record Reference(String manifest, Identity identity) {
	}

	public record Validated(Reference reference, Manifest manifest, M2tsTimingManifest timing, Path directory) {
	}

	/** Captures the full FileTime precision; file keys are optional and provider-specific. */
	public static Identity identity(Path path) throws IOException {
		Path real = path.toRealPath();
		BasicFileAttributes attrs = Files.readAttributes(real, BasicFileAttributes.class);
		if (!attrs.isRegularFile() || !Files.isReadable(real))
			throw new IOException("Cache identity requires a readable regular file: " + path);
		return new Identity(real.toString(), attrs.size(), attrs.lastModifiedTime().toString(),
				attrs.fileKey() == null ? null : attrs.fileKey().toString());
	}

	public static Reference reference(Path manifestPath) throws IOException {
		Identity identity = identity(manifestPath);
		return new Reference(identity.path(), identity);
	}

	/** Writes the completion marker last, never overwriting an existing immutable marker. */
	public static void write(Path manifestPath, Manifest manifest) throws IOException {
		try (var out = Files.newOutputStream(manifestPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			JsonMapperFactory.get().writeValue(out, manifest);
		}
	}

	/** Validates the pinned marker, source pair, selected coverage and every saved extraction artifact. */
	public static Validated validate(Reference reference) throws IOException {
		try {
			return validateEntry(reference);
		} catch (IOException | RuntimeException e) {
			throw new IOException(
					"Invalid extraction cache; remove the stale entry and replan (do not remove entries used by pending jobs): "
							+ (reference == null ? "null" : reference.manifest()) + ": " + e.getMessage(),
					e);
		}
	}

	private static Validated validateEntry(Reference reference) throws IOException {
		require(reference != null && reference.manifest() != null && reference.identity() != null,
				"Missing pinned manifest identity");
		Path marker = Path.of(reference.manifest());
		require(marker.isAbsolute() && marker.getFileName().toString().equals(FILE_NAME), "Invalid manifest path");
		require(marker.equals(marker.toRealPath()), "Manifest path must be canonical, not a symlink alias");
		checkIdentity(marker, reference.identity(), false);
		Manifest manifest = JsonMapperFactory.get().readValue(marker.toFile(), Manifest.class);
		require(manifest != null && manifest.version() == VERSION && manifest.targetRoot() != null
				&& manifest.source() != null && manifest.clpi() != null && manifest.info() != null
				&& manifest.pids() != null && !manifest.pids().isEmpty() && manifest.artifacts() != null
				&& manifest.duration90k() > 0, "Invalid cache schema/version");
		Path root = Path.of(manifest.targetRoot());
		require(root.isAbsolute() && root.toString().equals(TargetRootWorkspace.canonical(root).toString()),
				"Target root must be canonical");
		Path directory = TargetRootWorkspace.requireInside(root, marker.getParent());
		require(directory.equals(marker.getParent()) && Files.isDirectory(directory), "Noncanonical cache directory");
		TargetRootWorkspace.requireInside(root, marker);
		TargetRootWorkspace.requireInside(root, root.resolve("BDMV/STREAM"));
		TargetRootWorkspace.requireInside(root, root.resolve("BDMV/CLIPINF"));
		checkIdentity(Path.of(manifest.source().path()), manifest.source(), false);
		checkIdentity(Path.of(manifest.clpi().path()), manifest.clpi(), false);
		require(!Path.of(manifest.source().path()).startsWith(root)
				&& !Path.of(manifest.clpi().path()).startsWith(root), "Source pair must be outside target root");
		M2tsInfo info = manifest.info();
		require(Objects.equals(info.getSourcePath(), manifest.source().path()) && manifest.source().size() > 0
				&& manifest.source().size() % 192 == 0 && info.getTotalPackets() == manifest.source().size() / 192
				&& info.getStreams() != null, "Cached metadata disagrees with source identity");
		Map<Integer, M2tsStreamInfo> streams = new HashMap<>();
		for (M2tsStreamInfo stream : info.getStreams()) {
			require(stream != null && stream.getPid() >= 0 && stream.getPid() <= 0x1FFF
					&& streams.put(stream.getPid(), stream) == null, "Invalid/duplicate metadata PID");
		}
		TreeSet<Integer> selected = new TreeSet<>(manifest.pids());
		require(manifest.pids().equals(List.copyOf(selected)), "Selected PIDs must be unique and sorted");
		for (var artifact : manifest.artifacts().entrySet()) {
			String name = artifact.getKey();
			safeName(name);
			require(artifact.getValue() != null && name.equals(artifact.getValue().path()),
					"Artifact identities must use relative basenames");
			Path path = directory.resolve(name);
			require(TargetRootWorkspace.requireInside(root, path).equals(path), "Artifact symlink/alias is forbidden");
			require(!Files.isSameFile(path, Path.of(manifest.source().path()))
					&& !Files.isSameFile(path, Path.of(manifest.clpi().path())), "Artifact aliases source pair");
			checkIdentity(path, artifact.getValue(), true);
		}
		require(manifest.artifacts().containsKey(M2tsTimingManifest.FILE_NAME), "Missing timing artifact");
		M2tsTimingManifest timing = M2tsTiming.read(directory.resolve(M2tsTimingManifest.FILE_NAME));
		require(timing.version() >= 2 && timing.version() <= M2tsTimingManifest.CURRENT_VERSION
				&& timing.sourceSize() == manifest.source().size()
				&& Objects.equals(timing.sourcePath(), manifest.source().path()), "Timing/source metadata mismatch");
		var timingPids = new TreeSet<Integer>();
		var names = new HashSet<String>();
		names.add(M2tsTimingManifest.FILE_NAME);
		for (var stream : timing.streams()) {
			safeName(stream.fileName());
			M2tsStreamInfo metadata = streams.get(stream.pid());
			require(timingPids.add(stream.pid()) && names.add(stream.fileName()) && metadata != null
					&& stream.codingType() != null && stream.codingType() == metadata.getCodingType()
					&& timing.sourcePacketCounts().getOrDefault(stream.pid(), 0L) > 0 && stream.pesTimings() != null
					&& !stream.pesTimings().isEmpty() && stream.segments() != null && stream.firstPts90k() != null,
					"Timing stream metadata/coverage mismatch");
			Identity artifact = manifest.artifacts().get(stream.fileName());
			require(artifact != null, "Missing elementary artifact identity");
			long previousPacket = -1;
			long previousOffset = -1;
			Long firstPts = null;
			Long firstDts = null;
			for (var pes : stream.pesTimings()) {
				require(pes != null && pes.packetIndex() > previousPacket && pes.packetIndex() < info.getTotalPackets()
						&& pes.outputOffset() >= previousOffset && pes.outputOffset() >= 0
						&& pes.outputOffset() <= artifact.size(), "Invalid PES source packet/output offset");
				previousPacket = pes.packetIndex();
				previousOffset = pes.outputOffset();
				if (firstPts == null)
					firstPts = pes.pts90k();
				if (firstDts == null)
					firstDts = pes.dts90k();
			}
			require(Objects.equals(firstPts, stream.firstPts90k()) && Objects.equals(firstDts, stream.firstDts90k()),
					"Timing stream first timestamps disagree with PES metadata");
		}
		require(timingPids.equals(selected) && names.equals(manifest.artifacts().keySet()),
				"Artifacts and selected timing PIDs must match exactly");
		// Detect ordinary concurrent replacements during metadata reads, without rereading media bytes.
		checkIdentity(marker, reference.identity(), false);
		checkIdentity(Path.of(manifest.source().path()), manifest.source(), false);
		checkIdentity(Path.of(manifest.clpi().path()), manifest.clpi(), false);
		for (var artifact : manifest.artifacts().entrySet())
			checkIdentity(directory.resolve(artifact.getKey()), artifact.getValue(), true);
		return new Validated(reference, manifest, timing, directory);
	}

	private static void safeName(String name) throws IOException {
		require(name != null && !name.isBlank() && !name.equals(".") && !name.equals("..") && !name.contains("/")
				&& !name.contains("\\") && !Path.of(name).isAbsolute() && Path.of(name).getNameCount() == 1,
				"Artifact path must be exactly a relative basename");
	}

	private static void checkIdentity(Path path, Identity expected, boolean artifact) throws IOException {
		Identity actual = identity(path);
		require(expected != null && (artifact || actual.path().equals(expected.path())) && actual.size() > 0
				&& actual.size() == expected.size() && Objects.equals(actual.modified(), expected.modified())
				&& (expected.fileKey() == null || actual.fileKey() == null
						|| expected.fileKey().equals(actual.fileKey())),
				"File identity changed: " + path);
	}

	private static void require(boolean valid, String message) throws IOException {
		if (!valid)
			throw new IOException(message);
	}
}
