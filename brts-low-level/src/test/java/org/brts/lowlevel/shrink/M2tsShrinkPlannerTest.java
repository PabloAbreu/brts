/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/shrink/M2tsShrinkPlannerTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.junit.jupiter.api.Test;

class M2tsShrinkPlannerTest {

	private final M2tsShrinkPlanner planner = new M2tsShrinkPlanner();

	@Test
	void exactRemovedAudioAndSubtitlePacketsCanProveFitWithSafetyReserve() {
		var inventory = inventory(20_000, track(0x1011, StreamCodingType.H264_AVC, 30),
				track(0x1100, StreamCodingType.DOLBY_AC3, 10), track(0x1101, StreamCodingType.DOLBY_AC3_PLUS, 30),
				track(0x1200, StreamCodingType.PRESENTATION_GRAPHICS, 5));
		M2tsShrinkConfig config = new M2tsShrinkConfig();
		config.setAudioPids(List.of(0x1100));
		config.setSubtitlePids(List.of());

		var plan = planner.plan(inventory, "00001", config, 15_000, Engine.QSVENCC);

		assertThat(plan.decision()).isEqualTo(M2tsShrinkPlan.Decision.AUDIO_PID_REMOVAL_FITS_ESTIMATE);
		assertThat(plan.exactDroppedTransportBytes()).isEqualTo((30L + 5L) * 192);
		assertThat(plan.estimatedBytesAfterPidRemoval()).isEqualTo(20_000 - (35L * 192));
		assertThat(plan.safetyReserveBytes()).isEqualTo(300);
		assertThat(plan.retainedAudioPids()).containsExactly(0x1100);
		assertThat(plan.removedPids()).containsExactlyInAnyOrder(0x1101, 0x1200);
		assertThat(plan.status()).isEqualTo("PLAN_ONLY");
	}

	@Test
	void insufficientExactPidRemovalRequiresVideoConversion() {
		var inventory = inventory(100_000, track(0x1011, StreamCodingType.H265_HEVC, 400),
				track(0x1100, StreamCodingType.DOLBY_TRUEHD, 20));
		M2tsShrinkConfig config = new M2tsShrinkConfig();

		var plan = planner.plan(inventory, "00002", config, 50_000, Engine.FFMPEG);

		assertThat(plan.decision()).isEqualTo(M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED);
		assertThat(plan.exactDroppedTransportBytes()).isEqualTo(20L * 192);
	}

	@Test
	void validatesWhitelistTypeAndDowngradeCompatibility() {
		var inventory = inventory(10_000, track(0x1011, StreamCodingType.H264_AVC, 30),
				track(0x1100, StreamCodingType.DOLBY_AC3, 10));
		M2tsShrinkConfig config = new M2tsShrinkConfig();
		config.setAudioPids(List.of(0x1011));
		assertThatThrownBy(() -> planner.plan(inventory, "00001", config, 1_000, Engine.FFMPEG))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-audio");

		config.setAudioPids(List.of(0x1100));
		config.setAudioDowngrades(Map.of(0x1100, M2tsShrinkConfig.AudioDowngrade.REMOVE_DTS_HD_EXTENSION));
		assertThatThrownBy(() -> planner.plan(inventory, "00001", config, 1_000, Engine.FFMPEG))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("DTS-HD");
	}

	@Test
	void audioDowngradeAndMinimumRateHintsAreNotCreditedAsSavings() {
		var inventory = inventory(50_000, track(0x1011, StreamCodingType.H264_AVC, 100),
				track(0x1100, StreamCodingType.DTS, 100));
		M2tsShrinkConfig config = new M2tsShrinkConfig();
		config.setAudioPids(List.of(0x1100));
		config.setAudioDowngrades(Map.of(0x1100, M2tsShrinkConfig.AudioDowngrade.DTS_TO_AC3));
		config.setMinimumAudioBitrateKbps(Map.of(0x1100, 768));

		var plan = planner.plan(inventory, "00003", config, 49_000, Engine.NVENCC);

		assertThat(plan.exactDroppedTransportBytes()).isZero();
		assertThat(plan.estimatedBytesAfterPidRemoval()).isEqualTo(50_000);
		assertThat(plan.decision()).isEqualTo(M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED);
		assertThat(plan.warnings()).anyMatch(message -> message.contains("not executed"));
		assertThat(plan.warnings()).anyMatch(message -> message.contains("not used for the exact source-PID"));
	}

	@Test
	void rejectsDuplicatePids() {
		var inventory = inventory(10_000, track(0x1011, StreamCodingType.H264_AVC, 30),
				track(0x1100, StreamCodingType.DOLBY_AC3, 10));
		M2tsShrinkConfig config = new M2tsShrinkConfig();
		config.setAudioPids(List.of(0x1100, 0x1100));
		assertThatThrownBy(() -> planner.plan(inventory, "00001", config, 1_000, Engine.FFMPEG))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicate PID");
	}

	private static M2tsShrinkInventory inventory(long bytes, M2tsShrinkInventory.Track... tracks) {
		return new M2tsShrinkInventory(Path.of("source.m2ts"), Path.of("source.clpi"), bytes, 900_000, List.of(tracks));
	}

	private static M2tsShrinkInventory.Track track(int pid, StreamCodingType type, long packets) {
		return new M2tsShrinkInventory.Track(pid, type, "eng", null, packets);
	}
}
