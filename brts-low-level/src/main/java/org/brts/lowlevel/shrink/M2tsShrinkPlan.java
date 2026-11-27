/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsShrinkPlan.java' is part of BRTS.
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

import java.util.List;
import java.util.Map;

import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;

/** Immutable analysis-only result. No conversion or filtering has been executed. */
public record M2tsShrinkPlan(String status, M2tsShrinkInput input, long targetBytes, long safetyReserveBytes,
		long sourceBytes, long exactDroppedTransportBytes, long estimatedBytesAfterPidRemoval, Decision decision,
		List<Integer> retainedAudioPids, List<Integer> retainedSubtitlePids, List<Integer> removedPids,
		Map<Integer, M2tsShrinkConfig.AudioDowngrade> plannedAudioDowngrades, Engine engine, List<String> warnings) {

	public M2tsShrinkPlan {
		retainedAudioPids = List.copyOf(retainedAudioPids);
		retainedSubtitlePids = List.copyOf(retainedSubtitlePids);
		removedPids = List.copyOf(removedPids);
		plannedAudioDowngrades = Map.copyOf(plannedAudioDowngrades);
		warnings = List.copyOf(warnings);
	}

	public enum Decision {
		AUDIO_PID_REMOVAL_FITS_ESTIMATE, VIDEO_CONVERSION_REQUIRED
	}

	public record M2tsShrinkInput(String m2tsPath, String clpiPath, String clipId, long duration90k) {
	}
}
