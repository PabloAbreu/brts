package org.brts.lowlevel.bdmv;

import lombok.Getter;

/**
 * Enumeration of all HDMV navigation command mnemonics and their 32-bit opcode values.
 * <p>
 * Reference: Blu-ray Disc Read-Only Format Part 3, Section 10 (HDMV Navigation Command Set). Verified against libbluray
 * (hdmv_insn.h, mobj_parse.c, mobj_data.h).
 * <p>
 * This enum serves as the single source of truth for all supported navigation commands.
 * <p>
 * 32-bit instruction word layout (MSB first):
 *
 * <pre>
 * Bits 31-29: OperandCount  (3 bits)  — number of operands (0, 1, or 2)
 * Bits 28-27: CommandGroup   (2 bits)  — 0=BRANCH, 1=COMPARE, 2=SET
 * Bits 26-24: CommandSubGroup(3 bits)
 * Bit  23:    ImmediateFlag for Destination operand
 * Bit  22:    ImmediateFlag for Source operand
 * Bits 21-20: reserved
 * Bits 19-16: BranchOption   (4 bits)
 * Bits 15-12: reserved
 * Bits 11-8:  CompareOption  (4 bits)
 * Bits  7-5:  reserved
 * Bits  4-0:  SetOption      (5 bits)
 * </pre>
 *
 * The {@link #opcode} field stores the base opcode <em>without</em> OperandCount (bits 31-29) and <em>without</em>
 * immediate flags (bits 23-22). These are added by the compiler and stripped by the decompiler.
 */
@Getter
public enum NavigationCommandMnemonic {

	// ────────────────────────────────────────────────────────────────────────
	// BRANCH group (grp=0) / GOTO sub-group (sub=0)
	// ────────────────────────────────────────────────────────────────────────
	NOP("NOP", 0x0000_0000L, 0, "No operation"), //
	GOTO("GOTO", 0x0001_0000L, 1, "Goto(instruction)"), //
	BREAK("BREAK", 0x0002_0000L, 0, "Break out of program"),

	// ────────────────────────────────────────────────────────────────────────
	// BRANCH group (grp=0) / JUMP sub-group (sub=1)
	// ────────────────────────────────────────────────────────────────────────
	// a title on blu-ray is defined in index.bdmv
	// the title_id operand refers to the index of the title in index.bdmv (0-based)
	// "jump" plays the given title and does not return, while "call" plays the title and
	// returns to the next command when done
	JUMP_OBJECT("JUMP_OBJECT", 0x0100_0000L, 1, "JumpObject(object_id)"),
	JUMP_TITLE("JUMP_TITLE", 0x0101_0000L, 1, "JumpTitle(title_id)"),
	CALL_OBJECT("CALL_OBJECT", 0x0102_0000L, 2, "CallObject(object_id, resume_object_id)"),
	CALL_TITLE("CALL_TITLE", 0x0103_0000L, 2, "CallTitle(title_id, resume_object_id)"),
	RESUME("RESUME", 0x0104_0000L, 0, "Resume()"),

	// ────────────────────────────────────────────────────────────────────────
	// BRANCH group (grp=0) / PLAY sub-group (sub=2)
	// ────────────────────────────────────────────────────────────────────────
	// play a playlist by id (name of the .mpls file without the extension, e.g. 1 for
	// "00001.mpls")
	PLAY_PL("PLAY_PL", 0x0200_0000L, 1, "PlayPL(pl)"),
	// play a playlist by id and a specific play item (0-based index in the playlist)
	PLAY_PL_PI("PLAY_PL_PI", 0x0201_0000L, 2, "PlayPL(pl, pi)"),
	// play a playlist by id and a specific play mark (0-based index in the playlist)
	PLAY_PL_PM("PLAY_PL_PM", 0x0202_0000L, 2, "PlayPL(pl, pm)"),
	TERMINATE_PL("TERMINATE_PL", 0x0203_0000L, 0, "TerminatePL()"), //
	LINK_PI("LINK_PI", 0x0204_0000L, 1, "LinkPI(pi)"), //
	LINK_MK("LINK_MK", 0x0205_0000L, 1, "LinkMK(mk)"),

	// ────────────────────────────────────────────────────────────────────────
	// COMPARE group (grp=1)
	// CompareOption is in bits 11-8
	// ────────────────────────────────────────────────────────────────────────
	BC("BC", 0x0800_0100L, 2, "bit-check"), //
	EQ("EQ", 0x0800_0200L, 2, "equals"), //
	NE("NE", 0x0800_0300L, 2, "not equals"), //
	GE("GE", 0x0800_0400L, 2, "greater or equal"), //
	GT("GT", 0x0800_0500L, 2, "greater than"), //
	LE("LE", 0x0800_0600L, 2, "less or equal"), //
	LT("LT", 0x0800_0700L, 2, "less than"),

	// ────────────────────────────────────────────────────────────────────────
	// SET group (grp=2) / SET sub-group (sub=0)
	// SetOption is in bits 4-0
	// ────────────────────────────────────────────────────────────────────────
	MOVE("MOVE", 0x1000_0001L, 2, "dst ← src"), //
	SWAP("SWAP", 0x1000_0002L, 2, "dst ↔ src"), //
	ADD("ADD", 0x1000_0003L, 2, "dst += src"), //
	SUB("SUB", 0x1000_0004L, 2, "dst -= src"), //
	MUL("MUL", 0x1000_0005L, 2, "dst *= src"), //
	DIV("DIV", 0x1000_0006L, 2, "dst /= src"), //
	MOD("MOD", 0x1000_0007L, 2, "dst %= src"), //
	RND("RND", 0x1000_0008L, 2, "dst = random(src)"), //
	AND("AND", 0x1000_0009L, 2, "dst &= src"), //
	OR("OR", 0x1000_000AL, 2, "dst |= src"), //
	XOR("XOR", 0x1000_000BL, 2, "dst ^= src"), //
	BITSET("BITSET", 0x1000_000CL, 2, "dst bit-set src"), //
	BITCLR("BITCLR", 0x1000_000DL, 2, "dst bit-clear src"), //
	SHL("SHL", 0x1000_000EL, 2, "dst <<= src"), //
	SHR("SHR", 0x1000_000FL, 2, "dst >>= src"),

	// ────────────────────────────────────────────────────────────────────────
	// SET group (grp=2) / SET_SYSTEM sub-group (sub=1)
	// SetOption is in bits 4-0
	// ────────────────────────────────────────────────────────────────────────
	SET_STREAM("SET_STREAM", 0x1100_0001L, 2, "Set stream"),
	SET_NV_TIMER("SET_NV_TIMER", 0x1100_0002L, 2, "Set navigation timer"),
	SET_BUTTON_PAGE("SET_BUTTON_PAGE", 0x1100_0003L, 2, "Set button/page"),
	ENABLE_BUTTON("ENABLE_BUTTON", 0x1100_0004L, 1, "Enable button"),
	DISABLE_BUTTON("DISABLE_BUTTON", 0x1100_0005L, 1, "Disable button"),
	SET_SEC_STREAM("SET_SEC_STREAM", 0x1100_0006L, 2, "Set secondary stream"),
	POPUP_OFF("POPUP_OFF", 0x1100_0007L, 0, "Dismiss pop-up menu"),
	STILL_ON("STILL_ON", 0x1100_0008L, 0, "Enable still mode"),
	STILL_OFF("STILL_OFF", 0x1100_0009L, 0, "Disable still mode"),
	SET_OUTPUT_MODE("SET_OUTPUT_MODE", 0x1100_000AL, 1, "Set output mode"),
	SET_STREAM_SS("SET_STREAM_SS", 0x1100_000BL, 2, "Set stereoscopic stream");

	private final String mnemonic;

	/**
	 * Base opcode value (without OperandCount in bits 31-29 and without ImmediateFlags in bits 23-22). Encodes
	 * CommandGroup, CommandSubGroup, and the relevant option field (BranchOption, CompareOption, or SetOption).
	 */
	private final long opcode;

	/** Number of operands: 0, 1 (destination only), or 2 (destination + source). */
	private final int operandCount;

	private final String description;

	/** Mask covering OperandCount (bits 31-29) and ImmediateFlags (bits 23-22). */
	public static final long VARIABLE_BITS_MASK = 0xE0C0_0000L;

	NavigationCommandMnemonic(String mnemonic, long opcode, int operandCount, String description) {
		this.mnemonic = mnemonic;
		this.opcode = opcode;
		this.operandCount = operandCount;
		this.description = description;
	}

	/**
	 * Looks up a mnemonic by its string value (case-insensitive).
	 *
	 * @param mnemonicStr the mnemonic string to look up
	 * @return the corresponding NavigationCommandMnemonic enum constant
	 * @throws IllegalArgumentException if the mnemonic is not found
	 */
	public static NavigationCommandMnemonic fromString(String mnemonicStr) {
		if (mnemonicStr == null) {
			throw new IllegalArgumentException("null mnemonic");
		}
		for (NavigationCommandMnemonic cmd : values()) {
			if (cmd.mnemonic.equalsIgnoreCase(mnemonicStr)) {
				return cmd;
			}
		}
		throw new IllegalArgumentException("Unknown navigation command mnemonic: " + mnemonicStr);
	}

	/**
	 * Looks up a mnemonic by its 32-bit opcode word. The OperandCount (bits 31-29) and ImmediateFlags (bits 23-22) are
	 * stripped before matching against the base opcode. Returns null if no match is found (for unknown opcodes).
	 *
	 * @param opcode the full 32-bit opcode (as read from binary)
	 * @return the corresponding NavigationCommandMnemonic enum constant, or null if not found
	 */
	public static NavigationCommandMnemonic fromOpcode(long opcode) {
		long baseOpcode = opcode & ~VARIABLE_BITS_MASK;
		for (NavigationCommandMnemonic cmd : values()) {
			if (cmd.opcode == baseOpcode) {
				return cmd;
			}
		}
		return null;
	}

}
