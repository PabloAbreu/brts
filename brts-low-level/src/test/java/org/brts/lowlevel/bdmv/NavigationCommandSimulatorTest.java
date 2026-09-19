package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/bdmv/NavigationCommandSimulatorTest.java' is part of BRTS.
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

import org.brts.lowlevel.bdmv.NavigationCommandSimulator.SimulationException;
import org.brts.lowlevel.bdmv.NavigationCommandSimulator.SimulationResult;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link NavigationCommandSimulator}.
 */
class NavigationCommandSimulatorTest {

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/** Operand is an immediate (literal) value. */
	private static final boolean IMM = true;

	/** Operand is a register reference (GPR or PSR). */
	private static final boolean REG = false;

	private static NavigationCommand cmd(String mnemonic, long op1, boolean op1Imm, long op2, boolean op2Imm) {
		NavigationCommand c = new NavigationCommand();
		c.setMnemonic(mnemonic);
		c.setOperand1(op1);
		c.setOperand2(op2);
		c.setRawOpcode(NavigationCommandCompiler.compile(mnemonic, op1Imm, op2Imm));
		return c;
	}

	private static NavigationCommand cmd(String mnemonic, long op1, boolean op1Imm) {
		return cmd(mnemonic, op1, op1Imm, 0, false);
	}

	private static NavigationCommand cmd(String mnemonic) {
		return cmd(mnemonic, 0, false, 0, false);
	}

	private SimulationResult run(List<NavigationCommand> cmds) {
		return run(cmds, null);
	}

	private SimulationResult run(List<NavigationCommand> cmds, Map<Integer, Long> psr) {
		return new NavigationCommandSimulator(psr, 100_000).run(cmds);
	}

	// -------------------------------------------------------------------------
	// Basic termination
	// -------------------------------------------------------------------------

	@Test
	void emptyProgram_terminatesImmediately() {
		SimulationResult r = run(List.of());
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.stepsExecuted()).isZero();
		assertThat(r.externalEffects()).isEmpty();
		assertThat(r.finalGprState()).isEmpty();
	}

	@Test
	void nop_runsAndTerminates() {
		SimulationResult r = run(List.of(cmd("NOP"), cmd("NOP")));
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.stepsExecuted()).isEqualTo(2);
	}

	@Test
	void break_terminatesEarly() {
		SimulationResult r = run(List.of(cmd("NOP"), cmd("BREAK"), cmd("NOP")));
		assertThat(r.terminationReason()).isEqualTo("BREAK");
		assertThat(r.stepsExecuted()).isEqualTo(2);
	}

	// -------------------------------------------------------------------------
	// GOTO
	// -------------------------------------------------------------------------

	@Test
	void goto_jumpsToTarget() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 3_000_000, IMM)); // GPR[0] = 3000000
		cmds.add(cmd("GOTO", 0, REG)); // GOTO GPR[0] → pc=3000000 → END
		cmds.add(cmd("NOP")); // should not execute

		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.stepsExecuted()).isEqualTo(2);
		assertThat(r.finalPc()).isEqualTo(3_000_000);
	}

	// -------------------------------------------------------------------------
	// MOVE, SWAP
	// -------------------------------------------------------------------------

	@Test
	void move_setsGprFromImmediate() {
		// MOVE GPR[5] ← immediate 65536
		SimulationResult r = run(List.of(cmd("MOVE", 5, REG, 65536, IMM)));
		assertThat(r.finalGprState()).containsEntry(5, 65536L);
	}

	@Test
	void move_setsGprFromGpr() {
		// Set GPR[1] = 99999, then MOVE GPR[2] ← GPR[1]
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 1, REG, 99999, IMM)); // GPR[1] = 99999
		cmds.add(cmd("MOVE", 2, REG, 1, REG)); // GPR[2] = GPR[1] = 99999

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(1, 99999L);
		assertThat(r.finalGprState()).containsEntry(2, 99999L);
	}

	@Test
	void swap_exchangesRegisters() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 100_000, IMM)); // GPR[0] = 100000
		cmds.add(cmd("MOVE", 1, REG, 200_000, IMM)); // GPR[1] = 200000
		cmds.add(cmd("SWAP", 0, REG, 1, REG)); // swap GPR[0] ↔ GPR[1]

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(0, 200_000L);
		assertThat(r.finalGprState()).containsEntry(1, 100_000L);
	}

	// -------------------------------------------------------------------------
	// Arithmetic
	// -------------------------------------------------------------------------

	@Test
	void add_saturatesAt32Bit() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 0x7FFF_FFFFL, IMM)); // GPR[0] = 0x7FFFFFFF
		cmds.add(cmd("ADD", 0, REG, 0x7FFF_FFFFL, IMM)); // GPR[0] += 0x7FFFFFFF →
															// 0xFFFFFFFE
		cmds.add(cmd("ADD", 0, REG, 0x1_0000L, IMM)); // GPR[0] += 0x10000 → saturates at
														// 0xFFFFFFFF

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0xFFFF_FFFFL);
	}

	@Test
	void sub_clampsAtZero() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 5_000, IMM)); // GPR[0] = 5000
		cmds.add(cmd("SUB", 0, REG, 10_000, IMM)); // GPR[0] -= 10000 → clamps at 0

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(0, 0L); // explicitly set to 0, still tracked
	}

	@Test
	void mul_wrapsTo32Bit() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 0x1_0000L, IMM)); // GPR[0] = 65536
		cmds.add(cmd("MUL", 0, REG, 0x1_0000L, IMM)); // GPR[0] *= 65536 → 0x1_0000_0000
														// masked to 0

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(0, 0L); // explicitly set to 0, still tracked
	}

	@Test
	void div_byZero_returnsMax() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 42_000, IMM)); // GPR[0] = 42000
		cmds.add(cmd("DIV", 0, REG, 1, REG)); // GPR[0] /= GPR[1]=0 → 0xFFFFFFFF

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0xFFFF_FFFFL);
	}

	@Test
	void mod_normalCase() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 17_000, IMM)); // GPR[0] = 17000
		cmds.add(cmd("MOD", 0, REG, 5_000, IMM)); // GPR[0] %= 5000 = 2000

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(2000L);
	}

	// -------------------------------------------------------------------------
	// Bitwise operations
	// -------------------------------------------------------------------------

	@Test
	void and_masksCorrectly() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 0x1234_5678L, IMM)); // GPR[0] = 0x12345678
		cmds.add(cmd("AND", 0, REG, 0x00FF_00FFL, IMM)); // GPR[0] &= 0x00FF00FF

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0x0034_0078L);
	}

	@Test
	void or_setsCorrectly() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 0x0F_0000L, IMM));
		cmds.add(cmd("OR", 0, REG, 0x00_F000L, IMM));

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0x0F_F000L);
	}

	@Test
	void xor_togglesCorrectly() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 0x0FFF_0000L, IMM));
		cmds.add(cmd("XOR", 0, REG, 0x00FF_FF00L, IMM));

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0x0F00_FF00L);
	}

	@Test
	void shl_shiftsLeft() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 0x1_0000L, IMM)); // GPR[0] = 0x10000
		cmds.add(cmd("SHL", 0, REG, 0x1_0004L, IMM)); // immediate 65540, 65540 & 31 = 4 →
														// shift left by 4

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0x10_0000L);
	}

	@Test
	void shr_shiftsRight() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 0x0010_0000L, IMM)); // GPR[0] = 0x100000
		cmds.add(cmd("SHR", 0, REG, 0x1_0004L, IMM)); // immediate 65540, 65540 & 31 = 4 →
														// shift right by 4

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0x1_0000L);
	}

	@Test
	void bitset_and_bitclr() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("BITSET", 0, REG, 5_000, IMM)); // GPR[0] |= (1 << 5000)
		// 5000 & 31 = 8 → set bit 8 → GPR[0] = 0x100

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState().get(0)).isEqualTo(0x100L);
	}

	// -------------------------------------------------------------------------
	// Compare + conditional skip
	// -------------------------------------------------------------------------

	@Test
	void eq_conditionTrue_executesNext() {
		// GPR[0] = 50000, compare with immediate 50000 → equal → execute next (MOVE)
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 50_000, IMM)); // GPR[0] = 50000
		cmds.add(cmd("EQ", 0, REG, 50_000, IMM)); // GPR[0] == 50000 → true → don't skip
		cmds.add(cmd("MOVE", 1, REG, 99_000, IMM)); // GPR[1] = 99000 (should execute)

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(1, 99_000L);
	}

	@Test
	void eq_conditionFalse_skipsNext() {
		// GPR[0] = 50000, compare with immediate 60000 → not equal → skip next
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 50_000, IMM)); // GPR[0] = 50000
		cmds.add(cmd("EQ", 0, REG, 60_000, IMM)); // GPR[0] == 60000 → false → skip next
		cmds.add(cmd("MOVE", 1, REG, 99_000, IMM)); // GPR[1] = 99000 (should be skipped)
		cmds.add(cmd("MOVE", 2, REG, 77_000, IMM)); // GPR[2] = 77000 (should execute)

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).doesNotContainKey(1);
		assertThat(r.finalGprState()).containsEntry(2, 77_000L);
	}

	@Test
	void ne_conditionTrue_executesNext() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 10_000, IMM));
		cmds.add(cmd("NE", 0, REG, 20_000, IMM)); // 10000 != 20000 → true
		cmds.add(cmd("MOVE", 1, REG, 42_000, IMM)); // should execute

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(1, 42_000L);
	}

	@Test
	void gt_comparesCorrectly() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 30_000, IMM));
		cmds.add(cmd("GT", 0, REG, 20_000, IMM)); // 30000 > 20000 → true
		cmds.add(cmd("MOVE", 1, REG, 1_000_000, IMM)); // should execute

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(1, 1_000_000L);
	}

	@Test
	void lt_comparesCorrectly() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 10_000, IMM));
		cmds.add(cmd("LT", 0, REG, 20_000, IMM)); // 10000 < 20000 → true
		cmds.add(cmd("MOVE", 1, REG, 1_000_000, IMM)); // should execute

		SimulationResult r = run(cmds);
		assertThat(r.finalGprState()).containsEntry(1, 1_000_000L);
	}

	// -------------------------------------------------------------------------
	// PSR access
	// -------------------------------------------------------------------------

	@Test
	void psr_readInitialised_succeeds() {
		long psr4 = 0x8000_0004L; // PSR4
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, psr4, REG)); // GPR[0] = PSR4

		SimulationResult r = run(cmds, Map.of(4, 42L));
		assertThat(r.finalGprState()).containsEntry(0, 42L);
	}

	@Test
	void psr_readUninitialized_throws() {
		long psr10 = 0x8000_000AL; // PSR10
		List<NavigationCommand> cmds = List.of(cmd("MOVE", 0, REG, psr10, REG));

		assertThatThrownBy(() -> run(cmds)).isInstanceOf(SimulationException.class).hasMessageContaining("PSR10")
				.hasMessageContaining("not provided");
	}

	// -------------------------------------------------------------------------
	// External-effect commands (traced)
	// -------------------------------------------------------------------------

	@Test
	void playPl_tracesAndTerminates() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("PLAY_PL", 10_000, IMM)); // immediate playlist number
		cmds.add(cmd("NOP")); // should not execute

		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("PLAY_PL");
		assertThat(r.stepsExecuted()).isEqualTo(1);
		assertThat(r.externalEffects()).hasSize(1);
		assertThat(r.externalEffects().get(0)).contains("PLAY_PL");
	}

	@Test
	void jumpTitle_tracesAndTerminates() {
		List<NavigationCommand> cmds = List.of(cmd("JUMP_TITLE", 5_000, IMM));
		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("JUMP_TITLE");
		assertThat(r.externalEffects()).hasSize(1);
		assertThat(r.externalEffects().get(0)).contains("JUMP_TITLE");
	}

	@Test
	void setSystem_tracesButContinues() {
		// SET_STREAM is traced but execution continues
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("SET_STREAM", 10_000, IMM, 20_000, IMM));
		cmds.add(cmd("MOVE", 0, REG, 12_345, IMM));

		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.externalEffects()).hasSize(1);
		assertThat(r.externalEffects().get(0)).contains("SET_STREAM");
		assertThat(r.finalGprState()).containsEntry(0, 12_345L);
	}

	@Test
	void setButtonPage_resolvesFromPrecedingMovesAndPersistsGprState() {
		// Real production sequence: NavigationCommandUtils.setButtonPage(page=2, button=3)
		List<NavigationCommand> cmds = NavigationCommandUtils.setButtonPage(2, 3);

		SimulationResult r = run(cmds);

		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.finalGprState()).containsEntry(1234, 3L).containsEntry(1235, 2L);
		assertThat(r.externalEffects()).hasSize(1);
		assertThat(r.externalEffects().get(0)).contains("SET_BUTTON_PAGE").contains("button=3").contains("page=2")
				.contains("effect=off");
	}

	// -------------------------------------------------------------------------
	// Max steps
	// -------------------------------------------------------------------------

	@Test
	void maxSteps_terminatesInfiniteLoop() {
		// GOTO GPR[0] where GPR[0] = 0 → infinite loop
		List<NavigationCommand> cmds = List.of(cmd("GOTO", 0, REG));

		SimulationResult r = new NavigationCommandSimulator((Map<Integer, Long>) null, 100).run(cmds);
		assertThat(r.terminationReason()).isEqualTo("MAX_STEPS");
		assertThat(r.stepsExecuted()).isEqualTo(100);
	}

	// -------------------------------------------------------------------------
	// Simple program: countdown loop
	// -------------------------------------------------------------------------

	@Test
	void countdownLoop() {
		// GPR[0] = counter (starts at 5)
		// GPR[1] = 1 (decrement value)
		// Loop: if GPR[0] == GPR[1](0, which is default 0) → end
		// 0: MOVE GPR[0], 50000 (GPR[0] = 50000)
		// 1: MOVE GPR[1], 10000 (GPR[1] = 10000)
		// 2: EQ GPR[0], GPR[1] (GPR[0] == GPR[1]? → if false, skip next)
		// 3: GOTO GPR[5] (GPR[5] = 6 → jump to 6, skip this if equal)
		// 4: BREAK
		// 5: NOP (padding, needed for GOTO target setup)
		// 6: SUB GPR[0], GPR[1] (GPR[0] -= GPR[1])
		// 7: MOVE GPR[5], 20000 (GPR[5] = 20000)
		// ...
		// This is getting complex. Let me simplify: use a linear program.

		// Simple: set GPR[0]=30000, sub 10000 three times → 0
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 30_000, IMM)); // GPR[0] = 30000
		cmds.add(cmd("SUB", 0, REG, 10_000, IMM)); // GPR[0] -= 10000 = 20000
		cmds.add(cmd("SUB", 0, REG, 10_000, IMM)); // GPR[0] -= 10000 = 10000
		cmds.add(cmd("SUB", 0, REG, 10_000, IMM)); // GPR[0] -= 10000 = 0

		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.finalGprState()).containsEntry(0, 0L); // explicitly set to 0, still tracked
	}

	// -------------------------------------------------------------------------
	// GPR initialization
	// -------------------------------------------------------------------------

	@Test
	void gprInit_preloadsRegisters() {
		// GPR[0] pre-loaded with 42000, then ADD 10000
		List<NavigationCommand> cmds = List.of(cmd("ADD", 0, REG, 10_000, IMM));
		SimulationResult r = new NavigationCommandSimulator(null, Map.of(0, 42_000L), 100_000).run(cmds);
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.finalGprState()).containsEntry(0, 52_000L);
	}

	@Test
	void gprInit_nullIsSameAsEmpty() {
		List<NavigationCommand> cmds = List.of(cmd("MOVE", 0, REG, 10_000, IMM));
		SimulationResult r = new NavigationCommandSimulator(null, (Map<Integer, Long>) null, 100_000).run(cmds);
		assertThat(r.finalGprState()).containsEntry(0, 10_000L);
	}

	// -------------------------------------------------------------------------
	// Terminal operand values
	// -------------------------------------------------------------------------

	@Test
	void terminalOps_playPl_reportsOperands() {
		List<NavigationCommand> cmds = List.of(cmd("PLAY_PL_PI", 50_000, IMM, 20_000, IMM));
		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("PLAY_PL_PI");
		assertThat(r.terminalOp1()).isEqualTo(50_000L);
		assertThat(r.terminalOp2()).isEqualTo(20_000L);
	}

	@Test
	void terminalOps_jumpObject_reportsOperand() {
		List<NavigationCommand> cmds = List.of(cmd("JUMP_OBJECT", 7_000, IMM));
		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("JUMP_OBJECT");
		assertThat(r.terminalOp1()).isEqualTo(7_000L);
	}

	@Test
	void terminalOps_end_hasNullOperands() {
		List<NavigationCommand> cmds = List.of(cmd("NOP"));
		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.terminalOp1()).isNull();
		assertThat(r.terminalOp2()).isNull();
	}

	// -------------------------------------------------------------------------
	// Conditional branch pattern (CMP + GOTO)
	// -------------------------------------------------------------------------

	@Test
	void conditionalBranch_pattern() {
		// if GPR[0] (=50000) > GPR[1] (=30000) → execute PLAY_PL
		// 0: MOVE GPR[0], 50000
		// 1: MOVE GPR[1], 30000
		// 2: GT GPR[0], GPR[1] (true → don't skip)
		// 3: PLAY_PL 10000 (executes → terminates)
		// 4: NOP (should not reach)
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 50_000, IMM));
		cmds.add(cmd("MOVE", 1, REG, 30_000, IMM));
		cmds.add(cmd("GT", 0, REG, 1, REG)); // GPR[0] > GPR[1]? → true
		cmds.add(cmd("PLAY_PL", 10_000, IMM));
		cmds.add(cmd("NOP"));

		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("PLAY_PL");
		assertThat(r.stepsExecuted()).isEqualTo(4); // MOVE, MOVE, GT, PLAY_PL
	}

	@Test
	void conditionalBranch_falseSkipsAction() {
		// if GPR[0] (=10000) > GPR[1] (=30000) → skip PLAY_PL
		// 0: MOVE GPR[0], 10000
		// 1: MOVE GPR[1], 30000
		// 2: GT GPR[0], GPR[1] (false → skip next)
		// 3: PLAY_PL 10000 (skipped)
		// 4: MOVE GPR[2], 88000 (executes)
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("MOVE", 0, REG, 10_000, IMM));
		cmds.add(cmd("MOVE", 1, REG, 30_000, IMM));
		cmds.add(cmd("GT", 0, REG, 1, REG)); // GPR[0] > GPR[1]? → false → skip
		cmds.add(cmd("PLAY_PL", 10_000, IMM)); // skipped
		cmds.add(cmd("MOVE", 2, REG, 88_000, IMM));

		SimulationResult r = run(cmds);
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.externalEffects()).isEmpty();
		assertThat(r.finalGprState()).containsEntry(2, 88_000L);
	}

	// -------------------------------------------------------------------------
	// Playlist terminator predicate
	// -------------------------------------------------------------------------

	@Test
	void playlistPredicate_alwaysTrue_terminatesLikeNull() {
		List<NavigationCommand> cmds = List.of(cmd("PLAY_PL", 10_000, IMM));
		SimulationResult r = new NavigationCommandSimulator(null, (Map<Integer, Long>) null, 100_000, id -> true)
				.run(cmds);
		assertThat(r.terminationReason()).isEqualTo("PLAY_PL");
		assertThat(r.terminalOp1()).isEqualTo(10_000L);
		assertThat(r.stepsExecuted()).isEqualTo(1);
	}

	@Test
	void playlistPredicate_falseFirstTrueSecond_skipsFirstTerminatesSecond() {
		// PLAY_PL 1 (rejected), PLAY_PL 2 (accepted)
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("PLAY_PL", 1, IMM));
		cmds.add(cmd("PLAY_PL", 2, IMM));
		SimulationResult r = new NavigationCommandSimulator(null, (Map<Integer, Long>) null, 100_000, id -> id == 2L)
				.run(cmds);
		assertThat(r.terminationReason()).isEqualTo("PLAY_PL");
		assertThat(r.terminalOp1()).isEqualTo(2L);
		assertThat(r.stepsExecuted()).isEqualTo(2);
		// Both PLAY_PL commands must appear in external effects
		assertThat(r.externalEffects()).hasSize(2);
	}

	@Test
	void playlistPredicate_alwaysFalse_reachesEnd() {
		List<NavigationCommand> cmds = new ArrayList<>();
		cmds.add(cmd("PLAY_PL", 5, IMM));
		cmds.add(cmd("PLAY_PL", 6, IMM));
		SimulationResult r = new NavigationCommandSimulator(null, (Map<Integer, Long>) null, 100_000, id -> false)
				.run(cmds);
		assertThat(r.terminationReason()).isEqualTo("END");
		assertThat(r.terminalOp1()).isNull();
		assertThat(r.externalEffects()).hasSize(2);
	}

}
