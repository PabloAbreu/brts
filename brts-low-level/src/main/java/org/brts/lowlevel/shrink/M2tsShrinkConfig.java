/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsShrinkConfig.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Input for a plan-only {@code m2ts-shrink} run. */
@Data
@NoArgsConstructor
public class M2tsShrinkConfig {
	/** Audio PIDs to retain. Missing/null retains all audio tracks; empty retains none. */
	private List<Integer> audioPids;

	/** PGS/text subtitle PIDs to retain. Missing/null retains all subtitle tracks; empty retains none. */
	private List<Integer> subtitlePids;

	/** Optional transformations for retained audio tracks; execution is not implemented yet. */
	private Map<Integer, AudioDowngrade> audioDowngrades = new LinkedHashMap<>();

	/** Optional positive lower-bound rate hints, in kbps, for variable-rate audio. */
	private Map<Integer, Integer> minimumAudioBitrateKbps = new LinkedHashMap<>();

	/** Optional exact/fixed audio bitrate declarations, in kbps. */
	private Map<Integer, Integer> fixedAudioBitrateKbps = new LinkedHashMap<>();

	/**
	 * Audio transformations represented in plans. DTS-HD core mode means removing the HD extension while retaining the
	 * lossy DTS core.
	 */
	public enum AudioDowngrade {
		REMOVE_DTS_HD_EXTENSION, DTS_TO_AC3
	}

	/** Validates shape and PID-level consistency against streams discovered in the M2TS/CLPI pair. */
	public void validate(M2tsShrinkInventory inventory) {
		List<Integer> selectedAudio = normalized(audioPids, "audioPids");
		List<Integer> selectedSubtitles = normalized(subtitlePids, "subtitlePids");
		if (selectedAudio.stream().anyMatch(selectedSubtitles::contains)) {
			throw new IllegalArgumentException("A PID cannot be listed in both audioPids and subtitlePids");
		}
		for (int pid : selectedAudio) {
			var track = inventory.track(pid);
			if (track == null || track.codingType() == null || !track.codingType().isAudio()) {
				throw new IllegalArgumentException("audioPids contains unknown or non-audio PID " + pid);
			}
		}
		for (int pid : selectedSubtitles) {
			var track = inventory.track(pid);
			if (track == null || track.codingType() == null || !track.codingType().isSubtitle()) {
				throw new IllegalArgumentException("subtitlePids contains unknown or non-subtitle PID " + pid);
			}
		}
		if (audioDowngrades != null) {
			for (var entry : audioDowngrades.entrySet()) {
				int pid = requirePid(entry.getKey(), "audioDowngrades");
				var track = inventory.track(pid);
				if ((audioPids != null && !selectedAudio.contains(pid)) || track == null || track.codingType() == null
						|| !track.codingType().isAudio()) {
					throw new IllegalArgumentException("audioDowngrades PID " + pid + " must be a retained audio PID");
				}
				AudioDowngrade downgrade = entry.getValue();
				if (downgrade == null) {
					throw new IllegalArgumentException("audioDowngrades PID " + pid + " must have an operation");
				}
				if (downgrade == AudioDowngrade.REMOVE_DTS_HD_EXTENSION
						&& track.codingType() != org.brts.common.model.StreamCodingType.DTS_HD
						&& track.codingType() != org.brts.common.model.StreamCodingType.DTS_HD_MASTER_AUDIO) {
					throw new IllegalArgumentException("REMOVE_DTS_HD_EXTENSION requires a DTS-HD PID; got " + pid);
				}
				if (downgrade == AudioDowngrade.DTS_TO_AC3
						&& track.codingType() != org.brts.common.model.StreamCodingType.DTS
						&& track.codingType() != org.brts.common.model.StreamCodingType.DTS_HD
						&& track.codingType() != org.brts.common.model.StreamCodingType.DTS_HD_MASTER_AUDIO) {
					throw new IllegalArgumentException("DTS_TO_AC3 requires a DTS-family PID; got " + pid);
				}
			}
		}
		validateBitrateHints(inventory, minimumAudioBitrateKbps, "minimumAudioBitrateKbps", "Minimum");
		validateBitrateHints(inventory, fixedAudioBitrateKbps, "fixedAudioBitrateKbps", "Fixed");
		if (minimumAudioBitrateKbps != null && fixedAudioBitrateKbps != null) {
			for (Integer pid : minimumAudioBitrateKbps.keySet()) {
				if (fixedAudioBitrateKbps.containsKey(pid)) {
					throw new IllegalArgumentException(
							"PID " + pid + " cannot have both fixed and minimum bitrate hints");
				}
			}
		}
	}

	private static void validateBitrateHints(M2tsShrinkInventory inventory, Map<Integer, Integer> hints, String field,
			String label) {
		if (hints == null) {
			return;
		}
		for (var entry : hints.entrySet()) {
			int pid = requirePid(entry.getKey(), field);
			var track = inventory.track(pid);
			if (track == null || track.codingType() == null || !track.codingType().isAudio()) {
				throw new IllegalArgumentException(field + " contains unknown or non-audio PID " + pid);
			}
			if (entry.getValue() == null || entry.getValue() <= 0) {
				throw new IllegalArgumentException(label + " audio bitrate for PID " + pid + " must be positive");
			}
		}
	}

	private static List<Integer> normalized(List<Integer> pids, String field) {
		if (pids == null) {
			return List.of();
		}
		List<Integer> result = new ArrayList<>();
		for (Integer pid : pids) {
			int validPid = requirePid(pid, field);
			if (result.contains(validPid)) {
				throw new IllegalArgumentException(field + " contains duplicate PID " + validPid);
			}
			result.add(validPid);
		}
		return result;
	}

	private static int requirePid(Integer pid, String field) {
		if (pid == null || pid < 0x20 || pid > 0x1FFE) {
			throw new IllegalArgumentException(field + " PID must be in 0x20..0x1FFE, got " + pid);
		}
		return pid;
	}
}
