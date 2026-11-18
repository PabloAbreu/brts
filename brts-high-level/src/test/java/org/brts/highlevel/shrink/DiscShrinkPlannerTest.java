/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-high-level/src/test/java/org/brts/highlevel/shrink/DiscShrinkPlannerTest.java' is part of BRTS.
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

package org.brts.highlevel.shrink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.brts.highlevel.shrink.DiscShrinkPlanner.Clip;
import org.brts.highlevel.shrink.DiscShrinkPlanner.Playlist;
import org.brts.highlevel.shrink.DiscShrinkPlanner.Stream;
import org.brts.highlevel.shrink.DiscShrinkPlanner.StreamKind;
import org.junit.jupiter.api.Test;

class DiscShrinkPlannerTest {

	private final DiscShrinkPlanner planner = new DiscShrinkPlanner();

	@Test
	void selectsLanguagesAndRetainsUnknownLanguageTracks() {
		DiscShrinkDescriptor descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of("eng"));
		descriptor.setSubtitleLanguages(List.of("fra"));
		Clip clip = new Clip("00001", 1000,
				List.of(new Stream(0x1100, StreamKind.AUDIO, "eng"), new Stream(0x1101, StreamKind.AUDIO, "jpn"),
						new Stream(0x1102, StreamKind.AUDIO, "und"), new Stream(0x1200, StreamKind.SUBTITLE, "fra"),
						new Stream(0x1201, StreamKind.SUBTITLE, null)),
				false, true);

		var plan = planner.plan(descriptor, List.of(new Playlist("00010", List.of("00001"))), List.of(clip), 100, 800);

		assertThat(plan.clips().get("00001").retainedAudioPids()).containsExactly(0x1100, 0x1102);
		assertThat(plan.clips().get("00001").retainedSubtitlePids()).containsExactly(0x1200, 0x1201);
	}

	@Test
	void playlistOverrideInheritsNullListAndHonorsExplicitEmptyList() {
		DiscShrinkDescriptor descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of("eng"));
		DiscShrinkDescriptor.PlaylistPreferences override = new DiscShrinkDescriptor.PlaylistPreferences();
		override.setAudioLanguages(List.of());
		descriptor.setPlaylists(Map.of("00010", override));
		Clip clip = new Clip("00001", 1000,
				List.of(new Stream(0x1100, StreamKind.AUDIO, "eng"), new Stream(0x1101, StreamKind.AUDIO, "jpn")),
				false, true);

		var plan = planner.plan(descriptor, List.of(new Playlist("00010", List.of("00001"))), List.of(clip), 0, 1000);

		assertThat(plan.clips().get("00001").retainedAudioPids()).isEmpty();
	}

	@Test
	void rejectsDifferentSelectionsForSharedClip() {
		DiscShrinkDescriptor descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of("eng"));
		DiscShrinkDescriptor.PlaylistPreferences override = new DiscShrinkDescriptor.PlaylistPreferences();
		override.setAudioLanguages(List.of("jpn"));
		descriptor.setPlaylists(Map.of("00011", override));
		Clip clip = new Clip("00001", 1000,
				List.of(new Stream(0x1100, StreamKind.AUDIO, "eng"), new Stream(0x1101, StreamKind.AUDIO, "jpn")),
				false, true);

		assertThatThrownBy(() -> planner.plan(descriptor,
				List.of(new Playlist("00010", List.of("00001")), new Playlist("00011", List.of("00001"))),
				List.of(clip), 0, 1000)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("shared clip 00001");
	}

	@Test
	void unsupportedSharedClipRetainsAllStreamsDespiteConflictingPreferences() {
		DiscShrinkDescriptor descriptor = new DiscShrinkDescriptor();
		descriptor.setAudioLanguages(List.of("eng"));
		DiscShrinkDescriptor.PlaylistPreferences override = new DiscShrinkDescriptor.PlaylistPreferences();
		override.setAudioLanguages(List.of("jpn"));
		descriptor.setPlaylists(Map.of("00011", override));
		Clip clip = new Clip("00001", 1000,
				List.of(new Stream(0x1100, StreamKind.AUDIO, "eng"), new Stream(0x1101, StreamKind.AUDIO, "jpn")),
				false, false);

		var plan = planner.plan(descriptor,
				List.of(new Playlist("00010", List.of("00001")), new Playlist("00011", List.of("00001"))),
				List.of(clip), 100, 2000);

		assertThat(plan.clips().get("00001").reducible()).isFalse();
		assertThat(plan.clips().get("00001").retainedAudioPids()).containsExactly(0x1100, 0x1101);
		assertThat(plan.fixedBytes()).isEqualTo(1100);
	}

	@Test
	void proportionallyAllocatesBudgetAndDoesNotReduceEmbeddedIgs() {
		Clip first = new Clip("00001", 600, List.of(new Stream(0x1100, StreamKind.AUDIO, "eng")), false, true);
		Clip second = new Clip("00002", 400, List.of(new Stream(0x1100, StreamKind.AUDIO, "eng")), false, true);
		Clip menu = new Clip("00003", 100, List.of(new Stream(0x1101, StreamKind.AUDIO, "eng")), true, true);

		var plan = planner.plan(new DiscShrinkDescriptor(), List.of(), List.of(first, second, menu), 100, 600);

		assertThat(plan.clips().get("00001").targetBytes()).isEqualTo(240);
		assertThat(plan.clips().get("00002").targetBytes()).isEqualTo(160);
		assertThat(plan.clips().get("00003").targetBytes()).isEqualTo(100);
		assertThat(plan.clips().get("00003").reducible()).isFalse();
		assertThat(plan.fixedBytes()).isEqualTo(200);
		assertThat(plan.allocatedClipBytes()).isEqualTo(400);
	}
}
