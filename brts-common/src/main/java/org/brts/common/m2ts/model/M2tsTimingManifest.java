/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/model/M2tsTimingManifest.java' is part of BRTS.
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

package org.brts.common.m2ts.model;

import java.util.List;
import java.util.Map;

import org.brts.common.model.StreamCodingType;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Source-clock extraction contract. All timestamps are unwrapped, absolute 90 kHz ticks, never rebased. Missing DTS is
 * null (not an inferred PTS). SUP stores only the low 32 bits; {@link SegmentTiming} retains the complete timestamps
 * for each SUP record, including wrap epochs. Offsets are byte offsets in the output file. Optional sourceSha256 binds
 * the extraction to all original transport bytes. Version 2 includes counts of every source transport PID, not just
 * extracted elementary streams; version 3 adds optional video entry-point scan results for each extracted video PID.
 * Version 4 uses compact JSON with one-letter properties on repeated timing records, without legacy-name aliases.
 */
public record M2tsTimingManifest(int version, String sourcePath, long sourceSize, Long firstPcr90k,
		List<StreamTiming> streams, String sourceSha256, Map<Integer, Long> sourcePacketCounts) {

	public M2tsTimingManifest {
		sourcePacketCounts = sourcePacketCounts == null ? Map.of() : Map.copyOf(sourcePacketCounts);
	}

	/** Backward-compatible constructor for manifests without all-PID packet counts. */
	public M2tsTimingManifest(int version, String sourcePath, long sourceSize, Long firstPcr90k,
			List<StreamTiming> streams, String sourceSha256) {
		this(version, sourcePath, sourceSize, firstPcr90k, streams, sourceSha256, Map.of());
	}

	/** Backward-compatible constructor for legacy manifests without source identity. */
	public M2tsTimingManifest(int version, String sourcePath, long sourceSize, Long firstPcr90k,
			List<StreamTiming> streams) {
		this(version, sourcePath, sourceSize, firstPcr90k, streams, null);
	}

	public static final String FILE_NAME = "timing.json";

	public static final int CURRENT_VERSION = 4;

	/**
	 * PES offsets for raw ES; for SUP, use segment offsets to locate complete records. The first PTS and DTS are the
	 * first actually present timestamps of each kind, not synthetic replacements for absent fields.
	 */
	public record StreamTiming(int pid, String fileName, StreamCodingType codingType, Long firstPts90k,
			Long firstDts90k, List<PesTiming> pesTimings, List<SegmentTiming> segments, VideoScan videoScan) {
		public StreamTiming(int pid, String fileName, StreamCodingType codingType, Long firstPts90k, Long firstDts90k,
				List<PesTiming> pesTimings, List<SegmentTiming> segments) {
			this(pid, fileName, codingType, firstPts90k, firstDts90k, pesTimings, segments, null);
		}
	}

	/** Result of the CLPI-compatible random-access picture scan for a video PID. */
	public record VideoScan(Long firstPts90k, Long lastPts90k, List<EntryPoint> entries, long malformedHeaders,
			long untimestampedPictures) {
		public VideoScan {
			entries = entries == null ? List.of() : List.copyOf(entries);
		}
	}

	/** Entry-point PTS and source packet number (SPN), in scan order. */
	public record EntryPoint(@JsonProperty(value = "p", required = true) long pts90k,
			@JsonProperty(value = "s", required = true) long spn) {
	}

	/** A timestamp-less PES is retained with null timestamps, not silently assigned a fabricated clock. */
	public record PesTiming(@JsonProperty(value = "i", required = true) long packetIndex,
			@JsonProperty(value = "o", required = true) long outputOffset, @JsonProperty("p") Long pts90k,
			@JsonProperty("d") Long dts90k) {
	}

	public record SegmentTiming(@JsonProperty(value = "o", required = true) long outputOffset,
			@JsonProperty(value = "t", required = true) int type, @JsonProperty(value = "s", required = true) int size,
			@JsonProperty(value = "p", required = true) long pts90k, @JsonProperty("d") Long dts90k) {
	}
}
