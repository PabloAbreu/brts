/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/M2tsTiming.java' is part of BRTS.
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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.model.M2tsTimingManifest;

import com.fasterxml.jackson.databind.SerializationFeature;

/** Shared timing/SUP helpers, independent of authoring layers. */
public final class M2tsTiming {
	public static final long TIMESTAMP_WRAP = 1L << 33;

	private M2tsTiming() {
	}

	/** Selects the nearest 33-bit epoch to reference. A half-wrap ambiguity is rejected. */
	public static long unwrap33(long raw, long reference) {
		if (raw < 0 || raw >= TIMESTAMP_WRAP)
			throw new IllegalArgumentException("Timestamp must be an unsigned 33-bit value");
		long delta = raw - Math.floorMod(reference, TIMESTAMP_WRAP);
		if (Math.abs(delta) == TIMESTAMP_WRAP / 2)
			throw new IllegalArgumentException("Ambiguous timestamp epoch");
		if (delta > TIMESTAMP_WRAP / 2)
			delta -= TIMESTAMP_WRAP;
		if (delta < -TIMESTAMP_WRAP / 2)
			delta += TIMESTAMP_WRAP;
		return Math.addExact(reference, delta);
	}

	/** Reads a manifest file (not a directory); rejects unsupported contract versions. */
	public static M2tsTimingManifest read(Path path) throws IOException {
		M2tsTimingManifest manifest = JsonMapperFactory.get().readValue(path.toFile(), M2tsTimingManifest.class);
		validate(path, manifest);
		return manifest;
	}

	private static void validate(Path path, M2tsTimingManifest manifest) throws IOException {
		if (manifest == null || (manifest.version() < 1 || manifest.version() > M2tsTimingManifest.CURRENT_VERSION)
				|| manifest.sourcePath() == null || manifest.sourceSize() < 0 || manifest.streams() == null
				|| manifest.streams().isEmpty())
			throw new IOException("Invalid or unsupported timing manifest: " + path);
		if (manifest.version() >= 2) {
			try {
				long packets = 0;
				for (var entry : manifest.sourcePacketCounts().entrySet()) {
					if (entry.getKey() < 0 || entry.getKey() > 0x1FFF || entry.getValue() < 0)
						throw new IOException("Invalid source packet PID/count in timing manifest: " + path);
					packets = Math.addExact(packets, entry.getValue());
				}
				if (Math.multiplyExact(packets, 192L) != manifest.sourceSize())
					throw new IOException("Source packet counts do not match source size in timing manifest: " + path);
			} catch (ArithmeticException e) {
				throw new IOException("Source packet counts overflow in timing manifest: " + path, e);
			}
		}
		if (manifest.version() >= 3) {
			long packetCount = manifest.sourceSize() / 192;
			for (var stream : manifest.streams()) {
				var scan = stream.videoScan();
				if (scan == null)
					continue;
				if (stream.codingType() == null || !stream.codingType().isVideo() || scan.malformedHeaders() < 0
						|| scan.untimestampedPictures() < 0
						|| (scan.firstPts90k() == null) != (scan.lastPts90k() == null)
						|| scan.firstPts90k() != null && scan.lastPts90k() < scan.firstPts90k())
					throw new IOException("Invalid video EP scan metadata in timing manifest: " + path);
				for (var entry : scan.entries()) {
					if (entry == null || entry.spn() < 0 || entry.spn() >= packetCount || entry.pts90k() < 0
							|| scan.firstPts90k() == null || entry.pts90k() < scan.firstPts90k()
							|| entry.pts90k() > scan.lastPts90k())
						throw new IOException("Invalid video EP entry in timing manifest: " + path);
				}
			}
		}
	}

	/** Writes to a manifest file path. Extraction publishes this only after all streams validate and close. */
	public static void write(Path path, M2tsTimingManifest manifest) throws IOException {
		validate(path, manifest);
		try (OutputStream out = Files.newOutputStream(path)) {
			JsonMapperFactory.get().writer().without(SerializationFeature.INDENT_OUTPUT).writeValue(out, manifest);
		}
	}

	/**
	 * Writes a complete SUP record. The format has unsigned 32-bit timestamp fields; callers must retain full
	 * timestamps separately. Absent DTS is encoded as zero, as required by conventional SUP consumers.
	 */
	public static void writeSupSegment(OutputStream out, long pts90k, Long dts90k, int type, byte[] data, int offset,
			int length) throws IOException {
		if (type < 0 || type > 255 || length < 0 || length > 65535 || offset < 0 || offset > data.length
				|| length > data.length - offset)
			throw new IllegalArgumentException("Invalid SUP segment type, size or byte range");
		DataOutputStream record = new DataOutputStream(out);
		record.writeShort(0x5047);
		record.writeInt((int) pts90k);
		record.writeInt(dts90k == null ? 0 : dts90k.intValue());
		record.writeByte(type);
		record.writeShort(length);
		record.write(data, offset, length);
	}
}
