package org.brts.lowlevel.bdmv;

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

}
