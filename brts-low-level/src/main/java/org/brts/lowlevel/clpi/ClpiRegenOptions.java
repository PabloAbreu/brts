/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/clpi/ClpiRegenOptions.java' is part of BRTS.
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

package org.brts.lowlevel.clpi;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

/**
 * Optional, per-PID video metadata overrides for CLPI regeneration. Null fields retain detected values.
 */
@Getter
@Setter
public class ClpiRegenOptions {

	public enum ProbeMode {
		BEST_EFFORT, STRICT, DISABLED
	}

	/**
	 * Probe mode for CLPI regeneration. Determines how strictly the source transport is analyzed.
	 */
	private ProbeMode probeMode = ProbeMode.BEST_EFFORT;

	/**
	 * Source transport PIDs omitted from CLPI metadata, probing and primary-video selection. This does not rewrite the
	 * transport or change packet numbering/PCR information. Empty or null includes every stream.
	 */
	private Set<Integer> excludedStreamPids = new LinkedHashSet<>();

	/**
	 * Video stream overrides for CLPI regeneration.
	 *
	 * Keys are transport PIDs (decimal JSON object keys), not FFmpeg stream indexes.
	 */
	private Map<Integer, VideoOverrides> videoStreams = new LinkedHashMap<>();
	/**
	 * Audio stream overrides for CLPI regeneration.
	 */
	private Map<Integer, AudioOverrides> audioStreams = new LinkedHashMap<>();
	/**
	 * Subtitle stream overrides for CLPI regeneration.
	 */
	private Map<Integer, SubtitleOverrides> subtitleStreams = new LinkedHashMap<>();

	/** Nullable overrides; explicit zero and false are authoritative. */
	@Getter
	@Setter
	public static class VideoOverrides {
		/** Video format code, or {@code null} to retain the detected value. */
		private Integer videoFormat;
		/** Frame-rate code, or {@code null} to retain the detected value. */
		private Integer frameRate;
		/** Aspect-ratio code, or {@code null} to retain the detected value. */
		private Integer aspectRatio;
		/** Original-content flag value, or {@code null} to retain the detected value. */
		private Boolean ocFlag;
		/** Constant-rate flag value, or {@code null} to retain the detected value. */
		private Boolean crFlag;
		/** Dynamic-range type code, or {@code null} to retain the detected value. */
		private Integer dynamicRangeType;
		/** Color-space code, or {@code null} to retain the detected value. */
		private Integer colorSpace;
		/** HDR10+ flag value, or {@code null} to retain the detected value. */
		private Boolean hdrPlusFlag;
	}

	/**
	 * Nullable overrides for audio metadata associated with a transport PID. A null field retains its detected value;
	 * explicit zero and false values are applied as supplied.
	 */
	@Getter
	@Setter
	public static class AudioOverrides {
		/**
		 * Audio channel layout (0x01=mono, 0x03=stereo, 0x06=multi, 0x0C=combo, etc.).
		 */
		private Integer audioChannelLayout;

		/** Sample rate (0x01=48kHz, 0x04=96kHz, 0x05=192kHz, etc.). */
		private Integer sampleRate;

		/** ISO 639-2 language code (audio and subtitle streams). */
		private String language;
	}

	/**
	 * Nullable overrides for subtitle metadata associated with a transport PID. A null field retains its detected
	 * value; explicit zero and false values are applied as supplied.
	 */
	@Getter
	@Setter
	public static class SubtitleOverrides {
		/** ISO 639-2 language code (audio and subtitle streams). */
		private String language;
	}
}
