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
		List<NavigationCommand> result = new ArrayList<>();
		result.add(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.MOVE, GPR_BUTTON, false, button, true)));
		result.add(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.MOVE, GPR_PAGE, false, page, true)));
		result.add(NavigationCommand
				.fromParsed(ParsedNavigationCommand.generateSetButtonPageCommand(GPR_BUTTON, GPR_PAGE, true)));
		return result;
	}

	public static List<NavigationCommand> popupOff() {
		return List.of(NavigationCommand.fromParsed(ParsedNavigationCommand.compile("POPUP_OFF", 0, false, 0, false)));
	}

	/**
	 * SET_STREAM command that selects a primary audio stream.
	 * <p>
	 * Operand1 layout: bit 31 = primary audio enable; bits [27:16] = stream ID.
	 */
	public static List<NavigationCommand> setAudio(int streamIndex) {
		long op1 = (1L << 31) | ((long) (streamIndex & 0xFFF) << 16);
		return List.of(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.SET_STREAM, op1, true, 0, false)));
	}

	/**
	 * SET_STREAM command that selects a PG/subtitle stream and enables display.
	 * <p>
	 * When {@code streamIndex <= 0}: op1=0 clears the stream ID and the display flag (subtitles off). Otherwise: bit 15
	 * = PG TextST enable; bit 14 = display flag; bits [11:0] = stream ID.
	 */
	public static List<NavigationCommand> setSubtitle(int streamIndex) {
		long op1 = streamIndex > 0 ? (1L << 15) | (1L << 14) | (streamIndex & 0xFFFL) : 0;
		return List.of(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.SET_STREAM, op1, true, 0, false)));
	}

	/** JUMP_TITLE command targeting the given title number (immediate operand). */
	public static List<NavigationCommand> jumpTitle(int titleNumber) {
		return List.of(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.JUMP_TITLE, titleNumber, true, 0, false)));
	}

	/** PLAY_PL command targeting the given playlist number (immediate operand). */
	public static List<NavigationCommand> playPlaylist(int playlistNumber) {
		return List.of(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.PLAY_PL, playlistNumber, true, 0, false)));
	}

	/** RESUME command (no operands). */
	public static List<NavigationCommand> resume() {
		return List.of(NavigationCommand.fromParsed(ParsedNavigationCommand.compile("RESUME", 0, false, 0, false)));
	}
}
