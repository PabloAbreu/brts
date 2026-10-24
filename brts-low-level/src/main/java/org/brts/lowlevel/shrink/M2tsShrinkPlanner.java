/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsShrinkPlanner.java' is part of BRTS.
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;

/** Makes the first conservative size decision using exact source packet counts for tracks removed by PID filtering. */
public final class M2tsShrinkPlanner {

	public static final int SAFETY_RESERVE_PERCENT = 2;

	public M2tsShrinkPlan plan(M2tsShrinkInventory inventory, String clipId, M2tsShrinkConfig config, long targetBytes,
			Engine engine) {
		if (inventory == null || config == null) {
			throw new IllegalArgumentException("Inventory and shrink config are required");
		}
		if (targetBytes <= 0) {
			throw new IllegalArgumentException("targetBytes must be positive");
		}
		config.validate(inventory);
		Set<Integer> audioToKeep = config.getAudioPids() == null ? null : new HashSet<>(config.getAudioPids());
		Set<Integer> subtitlesToKeep = config.getSubtitlePids() == null ? null
				: new HashSet<>(config.getSubtitlePids());
		List<Integer> retainedAudio = new ArrayList<>();
		List<Integer> retainedSubtitles = new ArrayList<>();
		List<Integer> removed = new ArrayList<>();
		long removedBytes = 0;
		for (M2tsShrinkInventory.Track track : inventory.tracks()) {
			StreamCodingType type = track.codingType();
			if (type != null && type.isAudio()) {
				if (audioToKeep == null || audioToKeep.contains(track.pid())) {
					retainedAudio.add(track.pid());
				} else {
					removed.add(track.pid());
					removedBytes = Math.addExact(removedBytes, track.sourceTransportBytes());
				}
			} else if (type != null && type.isSubtitle()) {
				if (subtitlesToKeep == null || subtitlesToKeep.contains(track.pid())) {
					retainedSubtitles.add(track.pid());
				} else {
					removed.add(track.pid());
					removedBytes = Math.addExact(removedBytes, track.sourceTransportBytes());
				}
			}
		}
		long reserve = targetBytes / 50 + (targetBytes % 50 == 0 ? 0 : 1);
		long fitCeiling = targetBytes - reserve;
		long afterRemoval = inventory.sourceBytes() - removedBytes;
		M2tsShrinkPlan.Decision decision = afterRemoval <= fitCeiling
				? M2tsShrinkPlan.Decision.AUDIO_PID_REMOVAL_FITS_ESTIMATE
				: M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED;
		List<String> warnings = new ArrayList<>();
		warnings.add(
				"Plan only: no stream filtering, audio downgrade, video conversion, or output writing was performed");
		warnings.add(
				"Source packet counts precisely measure input PID bytes, not future remux overhead or encoder output");
		if (config.getAudioDowngrades() != null && !config.getAudioDowngrades().isEmpty()) {
			warnings.add(
					"Audio downgrade operations are recorded but not executed; their size savings are not credited");
		}
		if ((config.getMinimumAudioBitrateKbps() != null && !config.getMinimumAudioBitrateKbps().isEmpty())
				|| (config.getFixedAudioBitrateKbps() != null && !config.getFixedAudioBitrateKbps().isEmpty())) {
			warnings.add(
					"Configured fixed/minimum bitrate hints are preserved but not used for the exact source-PID byte count");
		}
		return new M2tsShrinkPlan("PLAN_ONLY",
				new M2tsShrinkPlan.M2tsShrinkInput(inventory.m2tsPath().toString(), inventory.clpiPath().toString(),
						clipId, inventory.duration90k()),
				targetBytes, reserve, inventory.sourceBytes(), removedBytes, afterRemoval, decision, retainedAudio,
				retainedSubtitles, removed,
				config.getAudioDowngrades() == null ? java.util.Map.of() : config.getAudioDowngrades(), engine,
				warnings);
	}
}
