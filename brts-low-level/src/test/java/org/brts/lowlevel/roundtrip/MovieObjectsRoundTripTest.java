package org.brts.lowlevel.roundtrip;

import org.brts.lowlevel.bdmv.MovieObjectsWriter;
import org.brts.lowlevel.bdmv.NavigationCommandCompiler;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.parser.MovieObjectsParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test for {@code MovieObject.bdmv}. Writes via {@link MovieObjectsWriter},
 * re-parses via {@link MovieObjectsParser}, and verifies the model is preserved.
 */
class MovieObjectsRoundTripTest {

	private final MovieObjectsWriter writer = new MovieObjectsWriter();

	private final MovieObjectsParser parser = new MovieObjectsParser();

	// -------------------------------------------------------------------------
	// Tests
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_magicAndVersionCorrect() throws Exception {
		byte[] bytes = writeToBytes(buildObjects(1));
		assertThat(new String(bytes, 0, 4)).isEqualTo("MOBJ");
		assertThat(new String(bytes, 4, 4)).isEqualTo("0300");
	}

	@Test
	void roundTrip_singleObject_flagsPreserved() throws Exception {
		MovieObjects original = new MovieObjects();
		MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
		obj.setResumeIntentionFlag(true);
		obj.setMenuCallMask(true);
		obj.setTitleSearchMask(false);
		obj.setNavigationCommands(List.of());
		original.setMovieObjects(List.of(obj));

		MovieObjects reparsed = parse(writeToBytes(original));

		assertThat(reparsed.getMovieObjects()).hasSize(1);
		MovieObjects.MovieObject out = reparsed.getMovieObjects().get(0);
		assertThat(out.isResumeIntentionFlag()).isTrue();
		assertThat(out.isMenuCallMask()).isTrue();
		assertThat(out.isTitleSearchMask()).isFalse();
	}

	@Test
	void roundTrip_navigationCommands_rawOpcodeAndOperandsPreserved() throws Exception {
		// PLAY_PL: grp=0, sub=2, branch_opt=0, op_cnt=1 → 0x2200_0000 + imm_op1
		MovieObjects.NavigationCommand cmd1 = rawCmd(0x22800000L, 1L, 0L);
		// JUMP_TITLE: grp=0, sub=1, branch_opt=1, op_cnt=1 → 0x2101_0000 + imm_op1
		MovieObjects.NavigationCommand cmd2 = rawCmd(0x21810000L, 2L, 0L);

		MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
		obj.setNavigationCommands(List.of(cmd1, cmd2));

		MovieObjects original = new MovieObjects();
		original.setMovieObjects(List.of(obj));

		MovieObjects reparsed = parse(writeToBytes(original));

		List<MovieObjects.NavigationCommand> cmds = reparsed.getMovieObjects().get(0).getNavigationCommands();
		assertThat(cmds).hasSize(2);

		assertThat(cmds.get(0).getRawOpcode()).isEqualTo(0x22800000L);
		assertThat(cmds.get(0).getOperand1()).isEqualTo(1L);
		assertThat(cmds.get(0).getOperand2()).isEqualTo(0L);

		assertThat(cmds.get(1).getRawOpcode()).isEqualTo(0x21810000L);
		assertThat(cmds.get(1).getOperand1()).isEqualTo(2L);
	}

	@Test
	void roundTrip_multipleObjects_countAndOrderPreserved() throws Exception {
		MovieObjects original = buildObjects(3);

		MovieObjects reparsed = parse(writeToBytes(original));

		assertThat(reparsed.getMovieObjects()).hasSize(3);
		// Each object has 1 command whose operand1 encodes its index
		for (int i = 0; i < 3; i++) {
			List<MovieObjects.NavigationCommand> cmds = reparsed.getMovieObjects().get(i).getNavigationCommands();
			assertThat(cmds).hasSize(1);
			assertThat(cmds.get(0).getOperand1()).isEqualTo(i + 1L);
		}
	}

	@Test
	void roundTrip_emptyCommandList_noExceptions() throws Exception {
		MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
		obj.setNavigationCommands(List.of());
		MovieObjects original = new MovieObjects();
		original.setMovieObjects(List.of(obj));

		MovieObjects reparsed = parse(writeToBytes(original));

		assertThat(reparsed.getMovieObjects()).hasSize(1);
		assertThat(reparsed.getMovieObjects().get(0).getNavigationCommands()).isEmpty();
	}

	@Test
	void roundTrip_allFlagCombinations() throws Exception {
		// Test all 8 combinations of the three mask flags
		List<MovieObjects.MovieObject> objs = new ArrayList<>();
		for (int mask = 0; mask < 8; mask++) {
			MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
			obj.setResumeIntentionFlag((mask & 0x4) != 0);
			obj.setMenuCallMask((mask & 0x2) != 0);
			obj.setTitleSearchMask((mask & 0x1) != 0);
			obj.setNavigationCommands(List.of());
			objs.add(obj);
		}
		MovieObjects original = new MovieObjects();
		original.setMovieObjects(objs);

		MovieObjects reparsed = parse(writeToBytes(original));

		for (int mask = 0; mask < 8; mask++) {
			MovieObjects.MovieObject out = reparsed.getMovieObjects().get(mask);
			assertThat(out.isResumeIntentionFlag()).isEqualTo((mask & 0x4) != 0);
			assertThat(out.isMenuCallMask()).isEqualTo((mask & 0x2) != 0);
			assertThat(out.isTitleSearchMask()).isEqualTo((mask & 0x1) != 0);
		}
	}

	@Test
	void roundTrip_mnemonicCompile_immediateOperandsSetImmFlags() throws Exception {
		// PLAY_PL with immediate playlist id should have IMM_OP1 set
		MovieObjects.NavigationCommand cmd = mnemonicCmd("PLAY_PL", 1000L, true, 0L, false);

		MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
		obj.setNavigationCommands(List.of(cmd));

		MovieObjects original = new MovieObjects();
		original.setMovieObjects(List.of(obj));

		MovieObjects reparsed = parse(writeToBytes(original));

		MovieObjects.NavigationCommand out = reparsed.getMovieObjects().get(0).getNavigationCommands().get(0);
		long expectedOpcode = NavigationCommandCompiler.compile("PLAY_PL", true, false);
		assertThat(out.getRawOpcode()).isEqualTo(expectedOpcode);
		assertThat(out.getOperand1()).isEqualTo(1000L);
		assertThat(out.getOperand2()).isEqualTo(0L);
	}

	@Test
	void roundTrip_mnemonicCompile_registerOperandsNoImmFlags() throws Exception {
		// MOVE with GPR operands (both registers) should have no IMM flags
		MovieObjects.NavigationCommand cmd = mnemonicCmd("MOVE", 5L, false, 10L, false);

		MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
		obj.setNavigationCommands(List.of(cmd));

		MovieObjects original = new MovieObjects();
		original.setMovieObjects(List.of(obj));

		MovieObjects reparsed = parse(writeToBytes(original));

		MovieObjects.NavigationCommand out = reparsed.getMovieObjects().get(0).getNavigationCommands().get(0);
		long expectedOpcode = NavigationCommandCompiler.compile("MOVE", false, false);
		assertThat(out.getRawOpcode()).isEqualTo(expectedOpcode);
		// Base opcode with no imm flags
		assertThat(out.getRawOpcode() & 0x00C0_0000L).isEqualTo(0L);
		assertThat(out.getOperand1()).isEqualTo(5L);
		assertThat(out.getOperand2()).isEqualTo(10L);
	}

	@Test
	void roundTrip_mnemonicCompile_mixedOperands() throws Exception {
		// ADD: GPR destination (register), immediate source
		MovieObjects.NavigationCommand cmd = mnemonicCmd("ADD", 3L, false, 50000L, true);

		MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
		obj.setNavigationCommands(List.of(cmd));

		MovieObjects original = new MovieObjects();
		original.setMovieObjects(List.of(obj));

		MovieObjects reparsed = parse(writeToBytes(original));

		MovieObjects.NavigationCommand out = reparsed.getMovieObjects().get(0).getNavigationCommands().get(0);
		long opcode = out.getRawOpcode();
		// IMM_OP1 should be clear (GPR destination), IMM_OP2 should be set (immediate
		// source)
		assertThat(opcode & NavigationCommandCompiler.IMM_OP1).isEqualTo(0L);
		assertThat(opcode & NavigationCommandCompiler.IMM_OP2).isNotEqualTo(0L);
		assertThat(out.getOperand1()).isEqualTo(3L);
		assertThat(out.getOperand2()).isEqualTo(50000L);
	}

	@Test
	void compile_immFlags_psrOperandIsNotImmediate() {
		// PSR reference — caller explicitly marks as register (not immediate)
		long opcode = NavigationCommandCompiler.compile("MOVE", false, false);
		assertThat(opcode & NavigationCommandCompiler.IMM_OP1).isEqualTo(0L);
	}

	@Test
	void compile_immFlags_gprOperandIsNotImmediate() {
		// GPR references — caller explicitly marks both as register (not immediate)
		long opcode = NavigationCommandCompiler.compile("ADD", false, false);
		assertThat(opcode & NavigationCommandCompiler.IMM_OP1).isEqualTo(0L);
		assertThat(opcode & NavigationCommandCompiler.IMM_OP2).isEqualTo(0L);
	}

	@Test
	void compile_immFlags_immediateOperandsAreFlagged() {
		// Both operands marked as immediate should set the corresponding flags
		long opcode = NavigationCommandCompiler.compile("PLAY_PL_PI", true, true);
		assertThat(opcode & NavigationCommandCompiler.IMM_OP1).isNotEqualTo(0L);
		assertThat(opcode & NavigationCommandCompiler.IMM_OP2).isNotEqualTo(0L);
	}

	// -------------------------------------------------------------------------
	// Builders
	// -------------------------------------------------------------------------

	private MovieObjects buildObjects(int count) {
		List<MovieObjects.MovieObject> objs = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			// PLAY_PL command pointing to playlist (i+1)
			// op_cnt=1, grp=0, sub=2, imm_op1=1, branch_opt=0 → 0x2280_0000
			MovieObjects.NavigationCommand cmd = rawCmd(0x22800000L, i + 1L, 0L);
			MovieObjects.MovieObject obj = new MovieObjects.MovieObject();
			obj.setNavigationCommands(List.of(cmd));
			objs.add(obj);
		}
		MovieObjects mo = new MovieObjects();
		mo.setMovieObjects(objs);
		return mo;
	}

	private MovieObjects.NavigationCommand rawCmd(long opcode, long op1, long op2) {
		MovieObjects.NavigationCommand cmd = new MovieObjects.NavigationCommand();
		cmd.setRawOpcode(opcode);
		cmd.setOperand1(op1);
		cmd.setOperand2(op2);
		return cmd;
	}

	private MovieObjects.NavigationCommand mnemonicCmd(String mnemonic, long op1, boolean op1Imm, long op2,
			boolean op2Imm) {
		MovieObjects.NavigationCommand cmd = new MovieObjects.NavigationCommand();
		cmd.setMnemonic(mnemonic);
		cmd.setOperand1(op1);
		cmd.setOperand2(op2);
		cmd.setRawOpcode(NavigationCommandCompiler.compile(mnemonic, op1Imm, op2Imm));
		return cmd;
	}

	private MovieObjects parse(byte[] bytes) throws Exception {
		return parser.parse(new ByteArrayInputStream(bytes));
	}

	// -------------------------------------------------------------------------

	private byte[] writeToBytes(MovieObjects mo) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(mo, out);
		return out.toByteArray();
	}

}
