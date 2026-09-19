package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/bdmv/NavigationCommandDecompiler.java' is part of BRTS.
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
