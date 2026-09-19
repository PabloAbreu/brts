package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/bdmv/ParsedNavigationCommandTest.java' is part of BRTS.
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

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ParsedNavigationCommand}.
 */
class ParsedNavigationCommandTest {

	// ── Factory: fromRaw ↔ toRaw roundtrip ──────────────────────────────────

	@Test
	void fromRaw_toRaw_roundtrip() {
		// PLAY_PL with IMM_OP1: opcode=0x22800000, op1=5, op2=0
		byte[] original = new byte[12];
		writeU32(original, 0, 0x22800000L);
		writeU32(original, 4, 5L);
		writeU32(original, 8, 0L);

		ParsedNavigationCommand parsed = ParsedNavigationCommand.fromRaw(original);
		byte[] roundTripped = parsed.toRaw();

		assertThat(roundTripped).isEqualTo(original);
	}

	@Test
	void fromRaw_rejectsShortArray() {
		assertThatThrownBy(() -> ParsedNavigationCommand.fromRaw(new byte[8]))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("12 bytes");
	}

	@Test
	void fromRaw_rejectsNull() {
		assertThatThrownBy(() -> ParsedNavigationCommand.fromRaw(null)).isInstanceOf(IllegalArgumentException.class);
	}

	// ── Factory: of ─────────────────────────────────────────────────────────

	@Test
	void of_storesFieldsCorrectly() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.of(0x22800000L, 42L, 7L);
		assertThat(cmd.getOpcode()).isEqualTo(0x22800000L);
		assertThat(cmd.getOperand1()).isEqualTo(42L);
		assertThat(cmd.getOperand2()).isEqualTo(7L);
	}

	// ── Factory: compile ────────────────────────────────────────────────────

	@Test
	void compile_setsCorrectOpcode() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL", 100, true, 0, false);
		long expected = NavigationCommandCompiler.compile("PLAY_PL", true, false);
		assertThat(cmd.getOpcode()).isEqualTo(expected);
		assertThat(cmd.getOperand1()).isEqualTo(100L);
		assertThat(cmd.getOperand2()).isEqualTo(0L);
	}

	@Test
	void compile_withEnum_setsCorrectOpcode() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile(NavigationCommandMnemonic.MOVE, 5, false, 42,
				true);
		assertThat(cmd.getMnemonic()).isEqualTo(NavigationCommandMnemonic.MOVE);
		assertThat(cmd.isOp1Immediate()).isFalse();
		assertThat(cmd.isOp2Immediate()).isTrue();
	}

	// ── Mnemonic accessors ──────────────────────────────────────────────────

	@Test
	void getMnemonic_knownOpcode() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("JUMP_TITLE", 1, true, 0, false);
		assertThat(cmd.getMnemonic()).isEqualTo(NavigationCommandMnemonic.JUMP_TITLE);
		assertThat(cmd.getMnemonicString()).isEqualTo("JUMP_TITLE");
	}

	@Test
	void getMnemonic_unknownOpcode_returnsNull() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.of(0xFFFF_FFFFL, 0, 0);
		assertThat(cmd.getMnemonic()).isNull();
		assertThat(cmd.getMnemonicString()).startsWith("GRP");
	}

	// ── Operand count ───────────────────────────────────────────────────────

	@Test
	void getOperandCount_fromCompiledOpcode() {
		ParsedNavigationCommand cmd0 = ParsedNavigationCommand.compile("NOP", 0, false, 0, false);
		ParsedNavigationCommand cmd1 = ParsedNavigationCommand.compile("PLAY_PL", 1, true, 0, false);
		ParsedNavigationCommand cmd2 = ParsedNavigationCommand.compile("MOVE", 0, false, 0, false);

		assertThat(cmd0.getOperandCount()).isEqualTo(0);
		assertThat(cmd1.getOperandCount()).isEqualTo(1);
		assertThat(cmd2.getOperandCount()).isEqualTo(2);
	}

	// ── Immediate flag accessors ────────────────────────────────────────────

	@Test
	void isOp1Immediate_trueWhenFlagSet() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL", 5, true, 0, false);
		assertThat(cmd.isOp1Immediate()).isTrue();
		assertThat(cmd.isOp2Immediate()).isFalse();
	}

	@Test
	void isOp2Immediate_trueWhenFlagSet() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 0, false, 42, true);
		assertThat(cmd.isOp1Immediate()).isFalse();
		assertThat(cmd.isOp2Immediate()).isTrue();
	}

	@Test
	void bothImmediate() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("ADD", 100, true, 200, true);
		assertThat(cmd.isOp1Immediate()).isTrue();
		assertThat(cmd.isOp2Immediate()).isTrue();
	}

	@Test
	void neitherImmediate() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 5, false, 10, false);
		assertThat(cmd.isOp1Immediate()).isFalse();
		assertThat(cmd.isOp2Immediate()).isFalse();
	}

	// ── OperandKind ─────────────────────────────────────────────────────────

	@Test
	void operandKind_immediate() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 42, true, 99, true);
		assertThat(cmd.getOp1Kind()).isEqualTo(OperandKind.IMMEDIATE);
		assertThat(cmd.getOp2Kind()).isEqualTo(OperandKind.IMMEDIATE);
	}

	@Test
	void operandKind_gpr() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 5, false, 10, false);
		assertThat(cmd.getOp1Kind()).isEqualTo(OperandKind.GPR);
		assertThat(cmd.getOp2Kind()).isEqualTo(OperandKind.GPR);
	}

	@Test
	void operandKind_psr() {
		long psr4 = 0x80000004L;
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 0, false, psr4, false);
		assertThat(cmd.getOp1Kind()).isEqualTo(OperandKind.GPR);
		assertThat(cmd.getOp2Kind()).isEqualTo(OperandKind.PSR);
	}

	// ── Register index ──────────────────────────────────────────────────────

	@Test
	void registerIndex_gpr() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 123, false, 456, false);
		assertThat(cmd.computeOp1RegisterIndex()).isEqualTo(123);
		assertThat(cmd.computeOp2RegisterIndex()).isEqualTo(456);
	}

	@Test
	void registerIndex_psr() {
		long psr31 = 0x8000001FL;
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", psr31, false, 0, false);
		assertThat(cmd.computeOp1RegisterIndex()).isEqualTo(31);
	}

	@Test
	void registerIndex_throwsForImmediate() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL", 42, true, 0, false);
		assertThatThrownBy(cmd::computeOp1RegisterIndex).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("immediate");
	}

	// ── Heuristic bug fix: describe uses opcode flags, not value heuristics ─

	@Test
	void describe_immediateValueLessThanFFF_notMisidentifiedAsGPR() {
		// This was the heuristic bug: MOVE GPR[0], 42 where op2=42 is immediate
		// Old decompiler would show "GPR[0] ← GPR[42]"
		// New behavior should show "GPR[0] ← 42"
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 0, false, 42, true);
		assertThat(cmd.describe()).isEqualTo("GPR[0] ← 42");
	}

	@Test
	void describe_immediateValueWithBit31Set_notMisidentifiedAsPSR() {
		// Immediate value 0x80000004 should show as literal, not PSR4
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 0, false, 0x80000004L, true);
		assertThat(cmd.describe()).isEqualTo("GPR[0] ← " + 0x80000004L);
	}

	@Test
	void describe_registerValueLessThanFFF_correctlyIdentifiedAsGPR() {
		// When NOT immediate, value 42 should be GPR[42]
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 0, false, 42, false);
		assertThat(cmd.describe()).isEqualTo("GPR[0] ← GPR[42]");
	}

	@Test
	void describe_registerValueWithBit31Set_correctlyIdentifiedAsPSR() {
		// When NOT immediate, value 0x80000004 should be PSR4
		long psr4 = 0x80000004L;
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 0, false, psr4, false);
		assertThat(cmd.describe()).contains("PSR4");
	}

	// ── describe() various command types ────────────────────────────────────

	@Test
	void describe_noOperandCommands() {
		assertThat(ParsedNavigationCommand.compile("NOP", 0, false, 0, false).describe()).isEqualTo("No operation");
		assertThat(ParsedNavigationCommand.compile("BREAK", 0, false, 0, false).describe())
				.isEqualTo("Break out of program");
	}

	@Test
	void describe_playPl() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL", 500, true, 0, false);
		assertThat(cmd.describe()).isEqualTo("Play playlist 500");
	}

	@Test
	void describe_playPlPi() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL_PI", 100, true, 3, true);
		assertThat(cmd.describe()).isEqualTo("Play playlist 100, play item 3");
	}

	@Test
	void describe_jumpTitle() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("JUMP_TITLE", 2, true, 0, false);
		assertThat(cmd.describe()).isEqualTo("Jump to title 2");
	}

	@Test
	void describe_comparison() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("EQ", 5, false, 100, true);
		assertThat(cmd.describe()).isEqualTo("If GPR[5] == 100");
	}

	@Test
	void describe_unknownOpcode() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.of(0xFFFF_FFFFL, 42, 7);
		assertThat(cmd.describe()).contains("Unknown command");
		assertThat(cmd.describe()).contains("0x");
	}

	// ── describeOperand ─────────────────────────────────────────────────────

	@Test
	void describeOperand1_immediate() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL", 999, true, 0, false);
		assertThat(cmd.describeOperand1()).isEqualTo("999");
	}

	@Test
	void describeOperand2_gpr() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", 0, false, 77, false);
		assertThat(cmd.describeOperand2()).isEqualTo("GPR[77]");
	}

	@Test
	void describeOperand1_psr() {
		long psr1 = 0x80000001L;
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("MOVE", psr1, false, 0, false);
		assertThat(cmd.describeOperand1()).contains("PSR1");
	}

	// ── equals / hashCode ───────────────────────────────────────────────────

	@Test
	void equals_sameValues() {
		ParsedNavigationCommand a = ParsedNavigationCommand.of(0x22800000L, 5, 0);
		ParsedNavigationCommand b = ParsedNavigationCommand.of(0x22800000L, 5, 0);
		assertThat(a).isEqualTo(b);
		assertThat(a.hashCode()).isEqualTo(b.hashCode());
	}

	@Test
	void equals_differentOpcode() {
		ParsedNavigationCommand a = ParsedNavigationCommand.of(0x22800000L, 5, 0);
		ParsedNavigationCommand b = ParsedNavigationCommand.of(0x22800001L, 5, 0);
		assertThat(a).isNotEqualTo(b);
	}

	// ── toString ────────────────────────────────────────────────────────────

	@Test
	void toString_matchesDescribe() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL", 1, true, 0, false);
		assertThat(cmd.toString()).isEqualTo(cmd.describe());
	}

	// ── Helper ──────────────────────────────────────────────────────────────

	private static void writeU32(byte[] data, int offset, long value) {
		data[offset] = (byte) ((value >> 24) & 0xFF);
		data[offset + 1] = (byte) ((value >> 16) & 0xFF);
		data[offset + 2] = (byte) ((value >> 8) & 0xFF);
		data[offset + 3] = (byte) (value & 0xFF);
	}

	// ── resolveButtonPageTarget ─────────────────────────────────────────────

	@Test
	void resolveButtonPageTarget_resolvesBothFromGpr() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.generateSetButtonPageCommand(1234, 1235, true);
		Map<Integer, Long> gpr = Map.of(1234, 3L, 1235, 2L);

		ParsedNavigationCommand.ButtonPageTarget target = cmd.resolveButtonPageTarget(gpr);

		assertThat(target.buttonId()).hasValue(3);
		assertThat(target.pageId()).hasValue(2);
		assertThat(target.effectOff()).isTrue();
	}

	@Test
	void resolveButtonPageTarget_effectOnWhenNotRequested() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.generateSetButtonPageCommand(1234, 1235, false);
		Map<Integer, Long> gpr = Map.of(1234, 1L, 1235, 0L);

		assertThat(cmd.resolveButtonPageTarget(gpr).effectOff()).isFalse();
	}

	@Test
	void resolveButtonPageTarget_missingGprThrows() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.generateSetButtonPageCommand(1234, 1235, true);

		assertThatThrownBy(() -> cmd.resolveButtonPageTarget(Map.of())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("GPR[1234]");
	}

	@Test
	void resolveButtonPageTarget_wrongMnemonicThrows() {
		ParsedNavigationCommand cmd = ParsedNavigationCommand.compile("PLAY_PL", 1, true, 0, false);

		assertThatThrownBy(() -> cmd.resolveButtonPageTarget(Map.of())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Not a SET_BUTTON_PAGE");
	}

}
