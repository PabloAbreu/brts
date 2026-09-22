package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/bdmv/NavigationCommandUtilsTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NavigationCommandUtils}'s GPR-driven stream selection branch program.
 */
class NavigationCommandUtilsTest {

	private long simulateAndCountSetStream(int audioTrackCount, int subtitleTrackCount, Long audioChoice,
			Long subChoice) {
		List<NavigationCommand> program = new ArrayList<>(
				NavigationCommandUtils.buildAudioSubtitleSelectionProgram(audioTrackCount, subtitleTrackCount));
		program.add(NavigationCommand
				.fromParsed(ParsedNavigationCommand.compile(NavigationCommandMnemonic.PLAY_PL, 1, true, 0, false)));

		Map<Integer, Long> gprInit = new HashMap<>();
		if (audioChoice != null) {
			gprInit.put(NavigationCommandUtils.GPR_AUDIO_CHOICE, audioChoice);
		}
		if (subChoice != null) {
			gprInit.put(NavigationCommandUtils.GPR_SUB_CHOICE, subChoice);
		}

		NavigationCommandSimulator.SimulationResult result = new NavigationCommandSimulator(null, gprInit, 10_000)
				.run(program);

		assertThat(result.terminationReason()).isEqualTo("PLAY_PL");
		return result.externalEffects().stream().filter(e -> e.contains("SET_STREAM")).count();
	}

	@Test
	void matchingAudioAndSubtitleChoice_emitsExactlyOneSetStreamEach() {
		assertThat(simulateAndCountSetStream(3, 2, 2L, 1L)).isEqualTo(2);
	}

	@Test
	void unsetGprChoices_fallThroughExceptSubtitleOffWhichMatchesDefaultZero() {
		// GPR default is 0, which is a legitimate "subtitles off" candidate, so it matches; audio has no candidate 0.
		assertThat(simulateAndCountSetStream(3, 2, null, null)).isEqualTo(1);
	}

	@Test
	void outOfRangeChoice_fallsThroughWithNoSetStream() {
		assertThat(simulateAndCountSetStream(3, 2, 99L, 99L)).isZero();
	}

	@Test
	void subtitleChoiceZero_isTreatedAsOffCandidate() {
		assertThat(simulateAndCountSetStream(0, 2, null, 0L)).isEqualTo(1);
	}

	@Test
	void zeroTrackCounts_produceEmptyProgram() {
		assertThat(NavigationCommandUtils.buildAudioSubtitleSelectionProgram(0, 0)).isEmpty();
	}

	@Test
	void initializeAudioSubtitleChoices_writesOnlyDeclaredChoices() {
		List<NavigationCommand> program = new ArrayList<>(NavigationCommandUtils.initializeAudioSubtitleChoices(2, 0));
		NavigationCommandSimulator.SimulationResult result = new NavigationCommandSimulator(null, new HashMap<>(),
				10_000).run(program);

		assertThat(result.finalGprState()).containsEntry(NavigationCommandUtils.GPR_AUDIO_CHOICE, 2L)
				.containsEntry(NavigationCommandUtils.GPR_SUB_CHOICE, 0L);
	}

	@Test
	void initializeAudioSubtitleChoices_withoutDefaults_isEmpty() {
		assertThat(NavigationCommandUtils.initializeAudioSubtitleChoices(0, -1)).isEmpty();
	}

}
