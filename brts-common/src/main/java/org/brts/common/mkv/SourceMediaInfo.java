package org.brts.common.mkv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/mkv/SourceMediaInfo.java' is part of BRTS.
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

import java.util.List;

import org.brts.common.model.StreamCodingType;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstract representation of the tracks found in a source media container (MKV, etc.). Low-level components use this
 * model to map source tracks to target Blu-ray PIDs.
 */
@Getter
@Setter
public class SourceMediaInfo {

	/** Path to the source file. */
	private String sourcePath;

	/** Duration of the media in milliseconds. */
	private long durationMs;

	private List<SourceTrack> tracks;

	// -------------------------------------------------------------------------

	/** Describes a single track within the source container. */
	@Getter
	@Setter
	public static class SourceTrack {

		/** Track number as reported by the container (1-based in MKV). */
		private int trackNumber;

		private StreamCodingType codingType;

		// --- Video ---
		private Integer widthPixels;

		private Integer heightPixels;

		private Double frameRateFps;

		// --- Audio ---
		private Integer channels;

		private Integer sampleRateHz;

		private Integer bitrateKbps;

		private String language;

		/** Human-readable track name from the container (e.g. "Director's Commentary"). May be null. */
		private String trackName;

		// --- Subtitle ---
		private String subtitleFormat; // e.g. "ASS", "SRT", "PGS"

		/** Codec private data (e.g. AVCDecoderConfigurationRecord for H.264). */
		private byte[] codecPrivate;

	}

}
