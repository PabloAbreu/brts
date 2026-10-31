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

	private ProbeMode probeMode = ProbeMode.BEST_EFFORT;

	/**
	 * Source transport PIDs omitted from CLPI metadata, probing and primary-video selection. This does not rewrite the
	 * transport or change packet numbering/PCR information. Empty or null includes every stream.
	 */
	private Set<Integer> excludedStreamPids = new LinkedHashSet<>();

	/**
	 * Keys are transport PIDs (decimal JSON object keys), not FFmpeg stream indexes.
	 */
	private Map<Integer, VideoOverrides> videoStreams = new LinkedHashMap<>();
	private Map<Integer, AudioOverrides> audioStreams = new LinkedHashMap<>();
	private Map<Integer, SubtitleOverrides> subtitleStreams = new LinkedHashMap<>();

	/** Nullable overrides; explicit zero and false are authoritative. */
	@Getter
	@Setter
	public static class VideoOverrides {
		private Integer videoFormat;
		private Integer frameRate;
		private Integer aspectRatio;
		private Boolean ocFlag;
		private Boolean crFlag;
		private Integer dynamicRangeType;
		private Integer colorSpace;
		private Boolean hdrPlusFlag;
	}

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

	@Getter
	@Setter
	public static class SubtitleOverrides {
		/** ISO 639-2 language code (audio and subtitle streams). */
		private String language;
	}
}
