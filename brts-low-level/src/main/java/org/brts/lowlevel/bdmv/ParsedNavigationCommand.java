package org.brts.lowlevel.bdmv;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import lombok.extern.slf4j.Slf4j;

/**
 * Immutable parsed representation of a single HDMV navigation command.
 * <p>
 * Stores the canonical three 32-bit words (opcode + two operands) and provides rich accessors for the instruction
 * fields, operand types, and human-friendly descriptions.
 * <p>
 * This is the single authoritative representation for navigation commands, bridging raw binary (12 bytes), symbolic
 * form (mnemonic + operands), and display.
 * <p>
 * Unlike the heuristic-based approach formerly used in {@link NavigationCommandDecompiler}, operand types are
 * determined from the opcode's immediate flags (bits 23 and 22), which is the correct and authoritative source.
 * <p>
 * 32-bit instruction word layout:
 *
 * <pre>
 * Bits 31-29: OperandCount  (3 bits)
 * Bits 28-27: CommandGroup   (2 bits)  — 0=BRANCH, 1=COMPARE, 2=SET
 * Bits 26-24: CommandSubGroup(3 bits)
 * Bit  23:    ImmediateFlag for Destination (operand 1)
 * Bit  22:    ImmediateFlag for Source (operand 2)
 * Bits 21-20: reserved
 * Bits 19-16: BranchOption   (4 bits)
 * Bits 15-12: reserved
 * Bits 11-8:  CompareOption  (4 bits)
 * Bits  7-5:  reserved
 * Bits  4-0:  SetOption      (5 bits)
 * </pre>
 *
 * @see NavigationCommandMnemonic
 * @see NavigationCommandCompiler
 */
@Slf4j
public final class ParsedNavigationCommand {

	private static final long IMM_OP1 = NavigationCommandCompiler.IMM_OP1; // bit 23

	private static final long IMM_OP2 = NavigationCommandCompiler.IMM_OP2; // bit 22

	private static final long PSR_BIT = 0x8000_0000L;
	private static final long BUTTON_ENABLED_BIT = PSR_BIT;// for button/page
	private static final long EFFECT_OFF_BIT = 0x4000_0000L;

	private final long opcode;

	private final long operand1;

	private final long operand2;

	private ParsedNavigationCommand(long opcode, long operand1, long operand2) {
		this.opcode = opcode;
		this.operand1 = operand1;
		this.operand2 = operand2;
	}

	// ── Factory methods ─────────────────────────────────────────────────────

	/**
	 * Creates a parsed command from the three raw 32-bit words.
	 */
	public static ParsedNavigationCommand of(long opcode, long operand1, long operand2) {
		return new ParsedNavigationCommand(opcode, operand1, operand2);
	}

	/**
	 * Creates a parsed command from a 12-byte raw command.
	 *
	 * @throws IllegalArgumentException if {@code raw} is null or shorter than 12 bytes
	 */
	public static ParsedNavigationCommand fromRaw(byte[] raw) {
		if (raw == null || raw.length < 12) {
			throw new IllegalArgumentException(
					"Navigation command must be at least 12 bytes, got " + (raw == null ? 0 : raw.length));
		}
		long opcode = readU32(raw, 0);
		long op1 = readU32(raw, 4);
		long op2 = readU32(raw, 8);
		return new ParsedNavigationCommand(opcode, op1, op2);
	}

	/**
	 * Compiles a command from a symbolic mnemonic and operands.
	 *
	 * @param mnemonic     symbolic name (case-insensitive), e.g. "PLAY_PL"
	 * @param op1          first operand value
	 * @param op1Immediate true if operand 1 is a literal value, false if register reference
	 * @param op2          second operand value
	 * @param op2Immediate true if operand 2 is a literal value, false if register reference
	 */
	public static ParsedNavigationCommand compile(String mnemonic, long op1, boolean op1Immediate, long op2,
			boolean op2Immediate) {
		long opcode = NavigationCommandCompiler.compile(mnemonic, op1Immediate, op2Immediate);
		return new ParsedNavigationCommand(opcode, op1, op2);
	}

	/**
	 * Compiles a command from an enum mnemonic and operands.
	 */
	public static ParsedNavigationCommand compile(NavigationCommandMnemonic mnemonic, long op1, boolean op1Immediate,
			long op2, boolean op2Immediate) {
		return compile(mnemonic.getMnemonic(), op1, op1Immediate, op2, op2Immediate);
	}

	// ── Raw data accessors ──────────────────────────────────────────────────

	/**
	 * Returns the full 32-bit opcode word (including OperandCount and immediate flags).
	 */
	public long getOpcode() {
		return opcode;
	}

	/** Returns the first operand (destination). */
	public long getOperand1() {
		return operand1;
	}

	/** Returns the second operand (source). */
	public long getOperand2() {
		return operand2;
	}

	// ── Instruction field accessors ─────────────────────────────────────────

	/**
	 * Returns the {@link NavigationCommandMnemonic} for this command, or null if the opcode is unknown.
	 */
	public NavigationCommandMnemonic getMnemonic() {
		return NavigationCommandMnemonic.fromOpcode(opcode);
	}

	/**
	 * Returns the mnemonic string, or a fallback encoding for unknown opcodes.
	 */
	public String getMnemonicString() {
		NavigationCommandMnemonic m = getMnemonic();
		if (m != null)
			return m.getMnemonic();

		int group = (int) ((opcode >> 27) & 0x03);
		int subGroup = (int) ((opcode >> 24) & 0x07);
		int branchOpt = (int) ((opcode >> 16) & 0x0F);
		int cmpOpt = (int) ((opcode >> 8) & 0x0F);
		int setOpt = (int) (opcode & 0x1F);
		return String.format("GRP%d_SUB%d_B%X_C%X_S%02X", group, subGroup, branchOpt, cmpOpt, setOpt);
	}

	/** Returns the operand count from bits 31-29 of the opcode. */
	public int getOperandCount() {
		return (int) ((opcode >> 29) & 0x07);
	}

	/** Returns the command group from bits 28-27 of the opcode. */
	public int getCommandGroup() {
		return (int) ((opcode >> 27) & 0x03);
	}

	// ── Operand type accessors ──────────────────────────────────────────────

	/** Returns true if operand 1 is an immediate (literal) value. */
	public boolean isOp1Immediate() {
		return (opcode & IMM_OP1) != 0;
	}

	/** Returns true if operand 2 is an immediate (literal) value. */
	public boolean isOp2Immediate() {
		return (opcode & IMM_OP2) != 0;
	}

	/**
	 * Returns the {@link OperandKind} of operand 1 by combining the immediate flag (bit 23) with the operand value's
	 * PSR/GPR encoding.
	 */
	public OperandKind getOp1Kind() {
		return operandKind(isOp1Immediate(), operand1);
	}

	/**
	 * Returns the {@link OperandKind} of operand 2 by combining the immediate flag (bit 22) with the operand value's
	 * PSR/GPR encoding.
	 */
	public OperandKind getOp2Kind() {
		return operandKind(isOp2Immediate(), operand2);
	}

	/**
	 * Returns the register index for operand 1 when it is a register reference. For PSR: bits 6:0. For GPR: bits 11:0.
	 *
	 * @throws IllegalStateException if operand 1 is an immediate value
	 */
	public int computeOp1RegisterIndex() {
		return registerIndex(getOp1Kind(), operand1, 1);
	}

	/**
	 * Returns the register index for operand 2 when it is a register reference. For PSR: bits 6:0. For GPR: bits 11:0.
	 *
	 * @throws IllegalStateException if operand 2 is an immediate value
	 */
	public int computeOp2RegisterIndex() {
		return registerIndex(getOp2Kind(), operand2, 2);
	}

	// ── Serialization ───────────────────────────────────────────────────────

	/**
	 * Serializes this command to a 12-byte big-endian array.
	 */
	public byte[] toRaw() {
		byte[] raw = new byte[12];
		writeU32(raw, 0, opcode);
		writeU32(raw, 4, operand1);
		writeU32(raw, 8, operand2);
		return raw;
	}

	// ── Human-friendly display ──────────────────────────────────────────────

	/**
	 * Returns a human-friendly description of this command, using the correct immediate flags from the opcode (not
	 * heuristic-based).
	 */
	public String describe() {
		NavigationCommandMnemonic cmd = getMnemonic();
		if (cmd == null) {
			return String.format("Unknown command (opcode=0x%08X, op1=%d, op2=%d)", opcode, operand1, operand2);
		}
		return formatCommand(cmd);
	}

	/**
	 * Returns a human-friendly description of operand 1, using the immediate flag from the opcode.
	 */
	public String describeOperand1() {
		return describeOperandValue(isOp1Immediate(), operand1);
	}

	/**
	 * Returns a human-friendly description of operand 2, using the immediate flag from the opcode.
	 */
	public String describeOperand2() {
		return describeOperandValue(isOp2Immediate(), operand2);
	}

	@Override
	public String toString() {
		return describe();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof ParsedNavigationCommand that))
			return false;
		return opcode == that.opcode && operand1 == that.operand1 && operand2 == that.operand2;
	}

	@Override
	public int hashCode() {
		return Objects.hash(opcode, operand1, operand2);
	}

	// ── Private helpers ─────────────────────────────────────────────────────

	private static OperandKind operandKind(boolean immediate, long operandValue) {
		if (immediate)
			return OperandKind.IMMEDIATE;
		if ((operandValue & PSR_BIT) != 0)
			return OperandKind.PSR;
		return OperandKind.GPR;
	}

	private static int registerIndex(OperandKind kind, long operandValue, int operandNumber) {
		return switch (kind) {
		case PSR -> (int) (operandValue & 0x7F);
		case GPR -> (int) (operandValue & 0xFFF);
		case IMMEDIATE -> throw new IllegalStateException(
				"Operand " + operandNumber + " is an immediate value, not a register reference");
		};
	}

	/**
	 * Describes a 32-bit operand value using the correct immediate flag.
	 */
	private static String describeOperandValue(boolean immediate, long operand) {
		if (immediate) {
			return String.valueOf(operand);
		}
		// Register reference — determine GPR vs PSR from the operand value
		if ((operand & PSR_BIT) != 0) {
			int psr = (int) (operand & 0x7F);
			return describePsr(psr);
		}
		return "GPR[" + (operand & 0xFFF) + "]";
	}

	/**
	 * Formats a known command using the mnemonic definition and operands.
	 */
	private String formatCommand(NavigationCommandMnemonic cmd) {
		return switch (cmd) {
		// No-operand commands
		case NOP, BREAK, RESUME, TERMINATE_PL, POPUP_OFF, STILL_ON, STILL_OFF -> cmd.getDescription();

		// Single operand: destination or value
		case GOTO -> "Goto instruction " + describeOperand1();
		case JUMP_OBJECT -> "Jump to object " + describeOperand1();
		case JUMP_TITLE -> "Jump to title " + describeOperand1();
		case LINK_PI -> "Link to play item " + describeOperand1();
		case LINK_MK -> "Link to playlist mark " + describeOperand1();
		case ENABLE_BUTTON -> "Enable button " + describeOperand1();
		case DISABLE_BUTTON -> "Disable button " + describeOperand1();
		case SET_OUTPUT_MODE -> "Set output mode to " + describeOperand1();

		// Two operand branch operations
		case CALL_OBJECT -> "Call object " + describeOperand1() + " (resume → object " + describeOperand2() + ")";
		case CALL_TITLE -> "Call title " + describeOperand1() + " (resume → object " + describeOperand2() + ")";

		// Two operand register operations
		case MOVE, SWAP, ADD, SUB, MUL, DIV, MOD, RND, AND, OR, XOR, BITSET, BITCLR, SHL, SHR ->
			formatBinaryOperation(cmd, describeOperand1(), describeOperand2());

		// Comparison operations
		case BC, EQ, NE, GE, GT, LE, LT -> formatComparison(cmd, describeOperand1(), describeOperand2());

		// Play operations
		case PLAY_PL -> "Play playlist " + describeOperand1();
		case PLAY_PL_PI -> "Play playlist " + describeOperand1() + ", play item " + describeOperand2();
		case PLAY_PL_PM -> "Play playlist " + describeOperand1() + ", mark " + describeOperand2();

		// Stream/system operations
		case SET_STREAM -> describeSetStream(operand1, operand2);
		case SET_NV_TIMER -> "Set navigation timer to " + describeOperand1();
		// SET_BUTTON_PAGE reverse-engineered from trial/compile with free version of igeditor
		case SET_BUTTON_PAGE -> "Set button/page ("
				+ (buttonOrPageEnabledFlag(operand1)
						? ("button=" + describeOperandValue(isOp1Immediate(), operand1 & ~PSR_BIT))
						: "no-button")
				+ (buttonOrPageEnabledFlag(operand2)
						? (", page=" + describeOperandValue(isOp2Immediate(), operand2 & ~PSR_BIT) + "(Effect "
								+ (effectOffFlag(operand2) ? "off" : "on"))
						: ", no-page")
				+ ")";
		case SET_SEC_STREAM -> "Set secondary stream (param=0x" + Long.toHexString(operand1) + ")";
		case SET_STREAM_SS -> "Set stereoscopic stream (param=0x" + Long.toHexString(operand1) + ")";
		};
	}

	/**
	 * Decodes and describes the SET_STREAM operands field by field, following libbluray _set_stream():
	 * <ul>
	 * <li>operand1 bit 31: primary audio enable; bits [27:16]: primary audio stream ID</li>
	 * <li>operand1 bit 15: PG TextST enable; bit 14: display flag; bits [11:0]: PG TextST stream ID</li>
	 * <li>operand2 bit 31: IG stream enable; bits [23:16]: IG stream ID</li>
	 * <li>operand2 bit 15: angle enable; bits [7:0]: angle number</li>
	 * </ul>
	 */
	private static String describeSetStream(long dst, long src) {
		StringBuilder sb = new StringBuilder("SET_STREAM");
		if ((dst & 0x8000_0000L) != 0) {
			sb.append(" audio=").append((dst >> 16) & 0xFFF);
		}
		if ((dst & 0x8000L) != 0) {
			sb.append(" pg=").append(dst & 0xFFF);
			sb.append(" display=").append((dst & 0x4000L) != 0 ? "on" : "off");
		} else if ((dst & 0x4000L) != 0) {
			// display flag written unconditionally even when enable=0
			sb.append(" display=off");
		}
		if ((src & 0x8000_0000L) != 0) {
			sb.append(" IG=").append((src >> 16) & 0xFF);
		}
		if ((src & 0x8000L) != 0) {
			sb.append(" angle=").append(src & 0xFF);
		}
		if (sb.length() == "SET_STREAM".length()) {
			sb.append(" (no-op: op1=0x").append(Long.toHexString(dst)).append(" op2=0x").append(Long.toHexString(src))
					.append(")");
		}
		return sb.toString();
	}

	private static boolean buttonOrPageEnabledFlag(long operand) {
		return (operand & BUTTON_ENABLED_BIT) != 0;
	}

	private static boolean effectOffFlag(long opcode) {
		return (opcode & EFFECT_OFF_BIT) != 0;
	}

	public static ParsedNavigationCommand generateSetButtonPageCommand(long buttonId, long pageId, boolean effectOff) {
		long op1 = buttonId | BUTTON_ENABLED_BIT;
		long op2 = pageId | BUTTON_ENABLED_BIT;
		if (effectOff) {
			op2 |= EFFECT_OFF_BIT;
		}
		return compile(NavigationCommandMnemonic.SET_BUTTON_PAGE, op1, false, op2, false);
	}

	/** Decoded {@code SET_BUTTON_PAGE} effect: which button/page (if any) to switch to. */
	public record ButtonPageTarget(OptionalInt buttonId, OptionalInt pageId, boolean effectOff) {
	}

	/**
	 * Decodes this {@code SET_BUTTON_PAGE} command's button/page targets, resolving register operands against
	 * {@code gprState}. Mirrors the bit layout used by {@link #describe()} (button/page enabled flag reuses the PSR
	 * discriminator bit, so it must be cleared before generic register-vs-immediate resolution).
	 *
	 * @throws IllegalStateException if this command's mnemonic is not {@code SET_BUTTON_PAGE}, or a referenced GPR is
	 *                               missing from {@code gprState}
	 */
	public ButtonPageTarget resolveButtonPageTarget(Map<Integer, Long> gprState) {
		if (getMnemonic() != NavigationCommandMnemonic.SET_BUTTON_PAGE) {
			throw new IllegalStateException("Not a SET_BUTTON_PAGE command: " + getMnemonicString());
		}
		OptionalInt buttonId = buttonOrPageEnabledFlag(operand1)
				? OptionalInt.of((int) resolveButtonPageOperand(isOp1Immediate(), operand1, gprState))
				: OptionalInt.empty();
		OptionalInt pageId = buttonOrPageEnabledFlag(operand2)
				? OptionalInt.of((int) resolveButtonPageOperand(isOp2Immediate(), operand2, gprState))
				: OptionalInt.empty();
		return new ButtonPageTarget(buttonId, pageId, effectOffFlag(operand2));
	}

	private static long resolveButtonPageOperand(boolean immediate, long operand, Map<Integer, Long> gprState) {
		long masked = operand & ~PSR_BIT;
		if (immediate) {
			return masked;
		}
		int gprIndex = (int) (masked & 0xFFF);
		Long value = gprState.get(gprIndex);
		if (value == null) {
			log.debug("GPR Contents : {}", gprState);
			throw new IllegalStateException("GPR[" + gprIndex + "] required by SET_BUTTON_PAGE was not set");
		}
		return value;
	}

	private static String formatBinaryOperation(NavigationCommandMnemonic mnemonic, String dst, String src) {
		return switch (mnemonic) {
		case MOVE -> dst + " ← " + src;
		case SWAP -> dst + " ↔ " + src;
		case ADD -> dst + " += " + src;
		case SUB -> dst + " -= " + src;
		case MUL -> dst + " *= " + src;
		case DIV -> dst + " /= " + src;
		case MOD -> dst + " %= " + src;
		case RND -> dst + " = random(" + src + ")";
		case AND -> dst + " &= " + src;
		case OR -> dst + " |= " + src;
		case XOR -> dst + " ^= " + src;
		case BITSET -> dst + " bit-set " + src;
		case BITCLR -> dst + " bit-clear " + src;
		case SHL -> dst + " <<= " + src;
		case SHR -> dst + " >>= " + src;
		default -> dst + " OP " + src;
		};
	}

	private static String formatComparison(NavigationCommandMnemonic mnemonic, String lhs, String rhs) {
		return switch (mnemonic) {
		case BC -> "If " + lhs + " bit-check " + rhs;
		case EQ -> "If " + lhs + " == " + rhs;
		case NE -> "If " + lhs + " != " + rhs;
		case GE -> "If " + lhs + " >= " + rhs;
		case GT -> "If " + lhs + " > " + rhs;
		case LE -> "If " + lhs + " <= " + rhs;
		case LT -> "If " + lhs + " < " + rhs;
		default -> "If " + lhs + " ? " + rhs;
		};
	}

	/**
	 * Returns a friendly name for well-known Player Status Registers. Reference: libbluray mobj_print.c psr_info[].
	 */
	static String describePsr(int psr) {
		return switch (psr) {
		case 0 -> "PSR0(IG stream)";
		case 1 -> "PSR1(primary audio stream)";
		case 2 -> "PSR2(PG/subtitle stream)";
		case 3 -> "PSR3(angle number)";
		case 4 -> "PSR4(title number)";
		case 5 -> "PSR5(chapter number)";
		case 6 -> "PSR6(playlist id)";
		case 7 -> "PSR7(play item id)";
		case 8 -> "PSR8(presentation time)";
		case 9 -> "PSR9(navigation timer)";
		case 10 -> "PSR10(selected button id)";
		case 11 -> "PSR11(page id)";
		case 12 -> "PSR12(user style number)";
		case 13 -> "PSR13(user age)";
		case 14 -> "PSR14(secondary audio/video stream)";
		case 15 -> "PSR15(audio capability)";
		case 16 -> "PSR16(audio language)";
		case 17 -> "PSR17(PG/subtitle language)";
		case 18 -> "PSR18(menu language)";
		case 19 -> "PSR19(country code)";
		case 20 -> "PSR20(region code)";
		case 21 -> "PSR21(output mode preference)";
		case 22 -> "PSR22(stereoscopic status)";
		case 23 -> "PSR23(display capability)";
		case 24 -> "PSR24(3D capability)";
		case 25 -> "PSR25(UHD capability)";
		case 26 -> "PSR26(UHD display capability)";
		case 27 -> "PSR27(HDR preference)";
		case 28 -> "PSR28(SDR conversion preference)";
		case 29 -> "PSR29(video capability)";
		case 30 -> "PSR30(text subtitle capability)";
		case 31 -> "PSR31(player profile/version)";
		default -> "PSR" + psr;
		};
	}

	private static long readU32(byte[] data, int offset) {
		return ((long) (data[offset] & 0xFF) << 24) | ((long) (data[offset + 1] & 0xFF) << 16)
				| ((long) (data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
	}

	private static void writeU32(byte[] data, int offset, long value) {
		data[offset] = (byte) ((value >> 24) & 0xFF);
		data[offset + 1] = (byte) ((value >> 16) & 0xFF);
		data[offset + 2] = (byte) ((value >> 8) & 0xFF);
		data[offset + 3] = (byte) (value & 0xFF);
	}

}
