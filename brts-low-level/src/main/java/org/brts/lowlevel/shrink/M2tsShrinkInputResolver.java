/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsShrinkInputResolver.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.paths.BrPath;
import org.brts.common.utils.paths.BrPath.BrRoot;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.parser.ClipInfoParser;

import lombok.RequiredArgsConstructor;

/** Resolves and cross-checks the M2TS/CLPI pair for a disc root and five-digit clip ID. */
@RequiredArgsConstructor
public final class M2tsShrinkInputResolver {

	@FunctionalInterface
	interface M2tsReader {
		M2tsInfo parse(Path path) throws IOException;
	}

	@FunctionalInterface
	interface ClpiReader {
		ClipInfo parse(Path path) throws IOException;
	}

	private final M2tsReader m2tsReader;
	private final ClpiReader clpiReader;
	private final M2tsPidPacketCounter packetCounter;

	public M2tsShrinkInputResolver() {
		this(new M2tsParser()::parse, new ClipInfoParser()::parse, new M2tsPidPacketCounter());
	}

	public M2tsShrinkInventory resolve(Path discRoot, String clipId) throws IOException {
		if (discRoot == null) {
			throw new IllegalArgumentException("discRoot is required");
		}
		if (clipId == null || !clipId.matches("[0-9]{5}")) {
			throw new IllegalArgumentException("clipId must contain exactly five digits");
		}
		Path root = discRoot.toAbsolutePath().normalize();
		BrRoot rootPath = BrPath.root(root);
		Path m2ts = rootPath.bdmv().stream(clipId);
		Path clpi = rootPath.bdmv().clipinf(clipId);
		requireReadableFile(m2ts, "M2TS");
		requireReadableFile(clpi, "CLPI");
		long bytes = Files.size(m2ts);
		M2tsInfo streamInfo = m2tsReader.parse(m2ts);
		ClipInfo clipInfo = clpiReader.parse(clpi);
		if (clipInfo.getDuration() == null || clipInfo.getDuration().getTicks() <= 0) {
			throw new IOException("CLPI has no positive duration: " + clpi);
		}
		Map<Integer, M2tsStreamInfo> pmtStreams = new LinkedHashMap<>();
		if (streamInfo.getStreams() != null) {
			for (M2tsStreamInfo stream : streamInfo.getStreams()) {
				pmtStreams.put(stream.getPid(), stream);
			}
		}
		Map<Integer, ClipStream> clpiStreams = new LinkedHashMap<>();
		if (clipInfo.getStreams() != null) {
			for (ClipStream stream : clipInfo.getStreams()) {
				clpiStreams.put(stream.getPid(), stream);
			}
		}
		for (var entry : clpiStreams.entrySet()) {
			M2tsStreamInfo pmtStream = pmtStreams.get(entry.getKey());
			if (pmtStream == null) {
				throw new IOException("CLPI PID " + entry.getKey() + " is absent from M2TS PMT: " + m2ts);
			}
			StreamCodingType clpiType = entry.getValue().getCodingType();
			if (pmtStream.getCodingType() != null && clpiType != null && pmtStream.getCodingType() != clpiType) {
				throw new IOException("M2TS/CLPI coding type mismatch for PID " + entry.getKey() + ": "
						+ pmtStream.getCodingType() + " vs " + clpiType);
			}
		}
		Map<Integer, Long> packetCounts = packetCounter.count(m2ts);
		var tracks = pmtStreams.values().stream().filter(stream -> stream.getCodingType() != null).map(stream -> {
			ClipStream clipStream = clpiStreams.get(stream.getPid());
			if (clipStream == null) {
				throw new IllegalArgumentException("M2TS PID " + stream.getPid() + " is absent from CLPI " + clpi);
			}
			return new M2tsShrinkInventory.Track(stream.getPid(), stream.getCodingType(),
					stream.getLanguage() != null ? stream.getLanguage() : clipStream.getLanguage(),
					stream.getBitrateKbps(), packetCounts.getOrDefault(stream.getPid(), 0L));
		}).toList();
		return new M2tsShrinkInventory(m2ts, clpi, bytes, clipInfo.getDuration().getTicks(), tracks);
	}

	private static void requireReadableFile(Path path, String kind) throws IOException {
		if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
			throw new IOException(kind + " must be a readable regular file: " + path);
		}
	}
}
