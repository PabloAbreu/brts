package org.brts.lowlevel.bdmv;

import java.util.ArrayList;
import java.util.List;

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
}
