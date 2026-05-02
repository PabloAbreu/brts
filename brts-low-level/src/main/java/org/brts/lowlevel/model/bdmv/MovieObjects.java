package org.brts.lowlevel.model.bdmv;

import java.util.List;

import org.brts.lowlevel.bdmv.NavigationCommandMnemonic;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand;

import lombok.Getter;
import lombok.Setter;

/**
 * Model for {@code BDMV/MovieObject.bdmv}.
 * <p>
 * Contains an ordered list of {@link MovieObject}s, each consisting of a navigation command program. Objects are
 * referenced from {@code index.bdmv} by their zero-based index.
 */
@Getter
@Setter
public class MovieObjects {

	private List<MovieObject> movieObjects;

	// -------------------------------------------------------------------------

	/**
	 * A single HDMV movie object — a program of navigation commands executed when a title is entered.
	 */
	@Getter
	@Setter
	public static class MovieObject {

		/**
		 * If true, the player resumes to this object after a pop-up menu is dismissed.
		 */
		private boolean resumeIntentionFlag = false;

		/** If true, menu call is prohibited while this object plays. */
		private boolean menuCallMask = false;

		/** If true, title search is prohibited while this object plays. */
		private boolean titleSearchMask = false;

		/** The navigation commands to execute for this object. */
		private List<NavigationCommand> navigationCommands;

	}

	// -------------------------------------------------------------------------

	/**
	 * A single HDMV navigation command (32-bit opcode + two 32-bit operands).
	 * <p>
	 * This is a JSON-friendly DTO. For rich operand-type analysis and display, convert to
	 * {@link ParsedNavigationCommand} via {@link #toParsed()}.
	 */
	@Getter
	@Setter
	public static class NavigationCommand {

		/**
		 * Symbolic command mnemonic, e.g.: "PLAY_PL", "PLAY_PL_PI", "PLAY_PL_PM", "TERMINATE_PL", "LINK_PI", "LINK_MK",
		 * "JUMP_TITLE", "JUMP_OBJECT", "MOVE".
		 */
		private String mnemonic;

		/** First operand value (playlist/title number, register index, etc.). */
		private long operand1;

		/** Second operand value (play item, play mark, etc.). */
		private long operand2;

		/** Optional: raw 32-bit opcode override. Set to -1 to use mnemonic. */
		private long rawOpcode = -1;

		/** Optional: human readable description of the command. */
		private String description;

		/**
		 * Converts this DTO to a {@link ParsedNavigationCommand} for rich analysis.
		 * <p>
		 * If {@code rawOpcode} is set (≥ 0), it is used directly. Otherwise the opcode is compiled from the mnemonic.
		 * When compiling from a mnemonic without a rawOpcode, operands are assumed immediate (since we have no other
		 * information).
		 *
		 * @return a parsed command with correct immediate flags
		 * @throws IllegalStateException if neither rawOpcode nor mnemonic is available
		 */
		public ParsedNavigationCommand toParsed() {
			if (rawOpcode >= 0) {
				return ParsedNavigationCommand.of(rawOpcode, operand1, operand2);
			}
			if (mnemonic != null) {
				// Without rawOpcode we cannot know the original immediate flags.
				// Default: treat both operands as immediate (safe for BRANCH commands
				// like PLAY_PL, JUMP_TITLE; callers needing register semantics should
				// always have rawOpcode set).
				return ParsedNavigationCommand.compile(mnemonic, operand1, true, operand2, true);
			}
			throw new IllegalStateException("NavigationCommand has neither rawOpcode nor mnemonic");
		}

		/**
		 * Creates a DTO from a {@link ParsedNavigationCommand}.
		 */
		public static NavigationCommand fromParsed(ParsedNavigationCommand parsed) {
			NavigationCommand cmd = new NavigationCommand();
			cmd.setRawOpcode(parsed.getOpcode());
			cmd.setOperand1(parsed.getOperand1());
			cmd.setOperand2(parsed.getOperand2());
			cmd.setMnemonic(parsed.getMnemonicString());
			cmd.setDescription(parsed.describe());
			return cmd;
		}

		public static NavigationCommand compile(NavigationCommandMnemonic mnemonic, long op1, boolean op1Immediate,
				long op2, boolean op2Immediate) {
			return fromParsed(ParsedNavigationCommand.compile(mnemonic, op1, op1Immediate, op2, op2Immediate));
		}
	}

}
