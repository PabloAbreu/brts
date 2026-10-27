/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/m2ts/PreservationPublication.java' is part of BRTS.
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

package org.brts.lowlevel.m2ts;

import static org.brts.lowlevel.m2ts.PreservationTransportNormalizer.require;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.brts.common.json.JsonMapperFactory;

/** Exclusive hard-link publication: never overwrites and keeps both staged inodes for recovery. */
final class PreservationPublication {
	private PreservationPublication() {
	}

	record Pending(String stagedM2ts, String stagedClpi, String targetM2ts, String targetClpi) {
	}

	static void recover(Path root, Path m2ts, Path clpi) throws IOException {
		Path journal = root.resolve("publication-pending.json");
		if (!Files.exists(journal))
			return;
		Pending pending = JsonMapperFactory.get().readValue(journal.toFile(), Pending.class);
		require(m2ts.toAbsolutePath().normalize().toString().equals(pending.targetM2ts())
				&& clpi.toAbsolutePath().normalize().toString().equals(pending.targetClpi()),
				"Publication journal belongs to different destinations");
		Path stagedM2ts = Path.of(pending.stagedM2ts());
		Path stagedClpi = Path.of(pending.stagedClpi());
		require(stagedM2ts.normalize().startsWith(root.toAbsolutePath().normalize())
				&& stagedClpi.normalize().startsWith(root.toAbsolutePath().normalize()), "Invalid publication journal");
		rollback(stagedM2ts, m2ts);
		rollback(stagedClpi, clpi);
		Files.delete(journal);
	}

	static void publish(Path root, Path stageM2ts, Path stageClpi, Path m2ts, Path clpi) throws IOException {
		absent(m2ts);
		absent(clpi);
		Files.createDirectories(clpi.toAbsolutePath().getParent());
		Path journal = root.resolve("publication-pending.json");
		Pending pending = new Pending(stageM2ts.toAbsolutePath().normalize().toString(),
				stageClpi.toAbsolutePath().normalize().toString(), m2ts.toAbsolutePath().normalize().toString(),
				clpi.toAbsolutePath().normalize().toString());
		byte[] json = JsonMapperFactory.get().writeValueAsBytes(pending);
		try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
			java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(json);
			while (buffer.hasRemaining())
				channel.write(buffer);
			channel.force(true);
		}
		try {
			// createLink is exclusive even when another process races the preflight check. Both targets must
			// support hard links to the workspace filesystem; unsupported/cross-device layouts fail closed.
			Files.createLink(m2ts, stageM2ts);
			Files.createLink(clpi, stageClpi);
			Files.delete(journal);
		} catch (IOException | UnsupportedOperationException failure) {
			try {
				recover(root, m2ts, clpi);
			} catch (IOException recovery) {
				failure.addSuppressed(recovery);
			}
			throw new IOException("Preservation publication failed; staged files retained in " + root, failure);
		}
	}

	private static void rollback(Path staged, Path target) throws IOException {
		if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
			return;
		require(!Files.isSymbolicLink(target) && Files.isSameFile(staged, target),
				"Refusing to remove unrelated publication destination " + target);
		Files.delete(target);
	}

	static void absent(Path target) throws IOException {
		require(!Files.exists(target, LinkOption.NOFOLLOW_LINKS), "Destination already exists: " + target);
	}
}
