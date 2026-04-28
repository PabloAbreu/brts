package org.brts.lowlevel.bdmv;

/**
 * Decompiles 32-bit HDMV navigation command opcodes (plus their two 32-bit operands) into human-readable mnemonics and
 * descriptions.
 * <p>
 * This is the inverse of {@link NavigationCommandCompiler}.
 * <p>
 * Reference: Blu-ray Disc Read-Only Format Part 3, Section 10 (HDMV Navigation Command Set). Verified against libbluray
 * (hdmv_insn.h, mobj_parse.c, mobj_data.h).
 * <p>
 * All supported mnemonics are defined in {@link NavigationCommandMnemonic}.
 * <p>
 * This class now delegates to {@link ParsedNavigationCommand} for all formatting and operand-type analysis, which uses
 * the opcode's immediate flags (bits 23/22) instead of the heuristic-based approach.
 */
public final class NavigationCommandDecompiler {

	private NavigationCommandDecompiler() {
	}

	/**
	 * Result of decompiling a single navigation command.
	 *
	 * @param mnemonic    the symbolic mnemonic (e.g. "PLAY_PL", "JUMP_TITLE")
	 * @param description a human-friendly description including operand details
	 */
	public record Result(String mnemonic, String description) {
		@Override
		public String toString() {
			return description;
		}
	}

	/**
	 * Decompile a full 12-byte navigation command (opcode + two operands).
	 *
	 * @param raw the 12-byte raw command
	 * @return decompiled result with mnemonic and human-friendly description
	 */
	public static Result decompile(byte[] raw) {
		if (raw == null || raw.length < 12) {
			return new Result("???", "Malformed command (" + (raw == null ? 0 : raw.length) + " bytes)");
		}
		ParsedNavigationCommand parsed = ParsedNavigationCommand.fromRaw(raw);
		return new Result(parsed.getMnemonicString(), parsed.describe());
	}

	/**
	 * Decompile a navigation command from its individual components.
	 *
	 * @param opcode 32-bit opcode word (including OperandCount, immediate flags, and command fields)
	 * @param op1    first operand (destination)
	 * @param op2    second operand (source)
	 * @return decompiled result with mnemonic and human-friendly description
	 */
	public static Result decompile(long opcode, long op1, long op2) {
		ParsedNavigationCommand parsed = ParsedNavigationCommand.of(opcode, op1, op2);
		return new Result(parsed.getMnemonicString(), parsed.describe());
	}

	/**
	 * Convenience method: decompile and return only the human-friendly description string.
	 */
	public static String describeCommand(byte[] raw) {
		return decompile(raw).description();
	}

	/**
	 * Convenience method: decompile and return only the human-friendly description string.
	 */
	public static String describeCommand(long opcode, long op1, long op2) {
		return decompile(opcode, op1, op2).description();
	}

}
