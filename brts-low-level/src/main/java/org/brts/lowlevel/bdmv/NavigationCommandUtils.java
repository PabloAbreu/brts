package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/bdmv/NavigationCommandUtils.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;

/**
 * Collection of utility methods for navigation commands.
 *
 * Some commands have some quirks that require special handling.
 *
 *
 */
public class NavigationCommandUtils {
	private static final int GPR_BUTTON = 1234;
	private static final int GPR_PAGE = 1235;
	/** Persists the user's chosen 1-based audio stream index across titles (set by a title-menu settings submenu). */
	public static final int GPR_AUDIO_CHOICE = 1236;
	/** Persists the user's chosen subtitle stream index (0 = off) across titles. */
	public static final int GPR_SUB_CHOICE = 1237;

	public static List<NavigationCommand> setButtonPage(int page, int button) {
		return list(ParsedNavigationCommand.compile(NavigationCommandMnemonic.MOVE, GPR_BUTTON, false, button, true),
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.MOVE, GPR_PAGE, false, page, true),
				ParsedNavigationCommand.generateSetButtonPageCommand(GPR_BUTTON, GPR_PAGE, true));

	}

	private static List<NavigationCommand> list(ParsedNavigationCommand... parsedCommands) {
		List<NavigationCommand> result = new ArrayList<>();
		for (ParsedNavigationCommand parsed : parsedCommands) {
			result.add(NavigationCommand.fromParsed(parsed));
		}
		return result;
	}

	// shorter version when list has a single element
	private static List<NavigationCommand> cmdList(NavigationCommandMnemonic mnemonic, long op1, boolean op1IsImmediate,
			long op2, boolean op2IsImmediate) {
		return list(ParsedNavigationCommand.compile(mnemonic, op1, op1IsImmediate, op2, op2IsImmediate));
	}

	public static List<NavigationCommand> popupOff() {
		return cmdList(NavigationCommandMnemonic.POPUP_OFF, 0, false, 0, false);
	}

	/**
	 * SET_STREAM command that selects a primary audio stream.
	 * <p>
	 * Operand1 layout: bit 31 = primary audio enable; bits [27:16] = stream ID.
	 */
	public static List<NavigationCommand> setAudio(int streamIndex) {
		long op1 = (1L << 31) | ((long) (streamIndex & 0xFFF) << 16);
		// unconditionally XOR this value to ensure that display of subs is not affected
		// if we don't set it, it defaults to zero, and subs are disabled, even if they were enabled before. This is a
		// quirk of the SET_STREAM command.
		op1 ^= 0x4000; // XOR with bit 14 (display flag)
		return cmdList(NavigationCommandMnemonic.SET_STREAM, op1, true, 0, false);
	}

	/**
	 * SET_STREAM command that selects a PG/subtitle stream and enables display.
	 * <p>
	 * When {@code streamIndex <= 0}: op1=0 clears the stream ID and the display flag (subtitles off). Otherwise: bit 15
	 * = PG TextST enable; bit 14 = display flag; bits [11:0] = stream ID.
	 */
	public static List<NavigationCommand> setSubtitle(int streamIndex) {
		long op1 = streamIndex > 0 ? (1L << 15) | (1L << 14) | (streamIndex & 0xFFFL) : 0;
		return cmdList(NavigationCommandMnemonic.SET_STREAM, op1, true, 0, false);
	}

	/** JUMP_TITLE command targeting the given title number (immediate operand). */
	public static List<NavigationCommand> jumpTitle(int titleNumber) {
		return cmdList(NavigationCommandMnemonic.JUMP_TITLE, titleNumber, true, 0, false);
	}

	/** PLAY_PL command targeting the given playlist number (immediate operand). */
	public static List<NavigationCommand> playPlaylist(int playlistNumber) {
		return cmdList(NavigationCommandMnemonic.PLAY_PL, playlistNumber, true, 0, false);
	}

	/** RESUME command (no operands). */
	public static List<NavigationCommand> resume() {
		return cmdList(NavigationCommandMnemonic.RESUME, 0, false, 0, false);
	}

	/** Writes the chosen 1-based audio stream index to {@link #GPR_AUDIO_CHOICE} (read later by title MovieObjects). */
	public static List<NavigationCommand> setAudioChoice(int streamIndex) {
		return cmdList(NavigationCommandMnemonic.MOVE, GPR_AUDIO_CHOICE, false, streamIndex, true);
	}

	/** Writes the chosen subtitle stream index (0 = off) to {@link #GPR_SUB_CHOICE}. */
	public static List<NavigationCommand> setSubtitleChoice(int streamIndex) {
		return cmdList(NavigationCommandMnemonic.MOVE, GPR_SUB_CHOICE, false, streamIndex, true);
	}

	/**
	 * Builds a branch program on a single GPR: for each candidate value in {@code [minChoice, maxChoiceInclusive]}, if
	 * the GPR equals that value the corresponding single-command action executes, otherwise the next candidate is
	 * tried; if none match, execution falls through with no side effect.
	 * <p>
	 * Relies on the EQ "skip next instruction if false" HDMV semantics (see {@code NavigationCommandSimulator}), so the
	 * whole program can be assembled in one forward pass with no backpatching: for {@code count} candidates the compare
	 * chain occupies {@code 2*count} instructions, followed by one unconditional GOTO guarding the "no match"
	 * fallthrough (otherwise falling out of the chain would run straight into the first action block), then
	 * {@code count} action blocks of 2 instructions each (action + GOTO to the continuation point), for a total length
	 * of {@code 4*count+1}.
	 *
	 * @param startOffset absolute instruction index (in the enclosing command list) where this program begins
	 */
	public static List<NavigationCommand> buildGprBranchProgram(int startOffset, int gprIndex, int minChoice,
			int maxChoiceInclusive, IntFunction<List<NavigationCommand>> actionForChoice) {
		int count = maxChoiceInclusive - minChoice + 1;
		if (count <= 0) {
			return List.of();
		}
		List<NavigationCommand> program = new ArrayList<>(count * 4 + 1);
		int actionsStart = startOffset + count * 2 + 1; // +1 for the no-match guard GOTO added below
		int after = actionsStart + count * 2;
		for (int i = 0; i < count; i++) {
			int choice = minChoice + i;
			int actionStart = actionsStart + i * 2;
			program.add(NavigationCommand.fromParsed(
					ParsedNavigationCommand.compile(NavigationCommandMnemonic.EQ, gprIndex, false, choice, true)));
			program.add(NavigationCommand.fromParsed(
					ParsedNavigationCommand.compile(NavigationCommandMnemonic.GOTO, actionStart, true, 0, false)));
		}
		// no candidate matched: skip past the action blocks instead of falling into the first one
		program.add(NavigationCommand
				.fromParsed(ParsedNavigationCommand.compile(NavigationCommandMnemonic.GOTO, after, true, 0, false)));
		for (int i = 0; i < count; i++) {
			int choice = minChoice + i;
			List<NavigationCommand> action = actionForChoice.apply(choice);
			if (action.size() != 1) {
				throw new IllegalArgumentException(
						"GPR branch action for choice " + choice + " must be a single command");
			}
			program.add(action.get(0));
			program.add(NavigationCommand.fromParsed(
					ParsedNavigationCommand.compile(NavigationCommandMnemonic.GOTO, after, true, 0, false)));
		}
		return program;
	}

	/**
	 * Builds the combined per-title stream-selection program: applies {@link #setAudio} for the audio choice stored in
	 * {@link #GPR_AUDIO_CHOICE} (1..audioTrackCount) and {@link #setSubtitle} for the subtitle choice stored in
	 * {@link #GPR_SUB_CHOICE} (0..subtitleTrackCount, 0 = off). A track count of 0 skips that program entirely (default
	 * stream stays in effect).
	 */
	public static List<NavigationCommand> buildAudioSubtitleSelectionProgram(int audioTrackCount,
			int subtitleTrackCount) {
		List<NavigationCommand> program = new ArrayList<>();
		if (audioTrackCount > 0) {
			program.addAll(buildGprBranchProgram(program.size(), GPR_AUDIO_CHOICE, 1, audioTrackCount,
					NavigationCommandUtils::setAudio));
		}
		if (subtitleTrackCount > 0) {
			program.addAll(buildGprBranchProgram(program.size(), GPR_SUB_CHOICE, 0, subtitleTrackCount,
					NavigationCommandUtils::setSubtitle));
		}
		return program;
	}
}
