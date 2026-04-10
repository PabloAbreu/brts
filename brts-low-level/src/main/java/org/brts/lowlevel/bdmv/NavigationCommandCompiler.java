package org.brts.lowlevel.bdmv;

/**
 * Compiles symbolic HDMV navigation command mnemonics to their 32-bit opcode values.
 * <p>
 * Reference: Blu-ray Disc Read-Only Format Part 3, Section 10 (HDMV Navigation Command Set).
 * Verified against libbluray (hdmv_insn.h, mobj_parse.c, mobj_data.h).
 * <p>
 * 32-bit instruction word layout:
 * <pre>
 * Bits 31-29: OperandCount       (from {@link NavigationCommandMnemonic#getOperandCount()})
 * Bits 28-27: CommandGroup        ─┐
 * Bits 26-24: CommandSubGroup     ─┤ encoded in {@link NavigationCommandMnemonic#getOpcode()}
 * Bits 19-16: BranchOption        ─┤
 * Bits 11-8:  CompareOption       ─┤
 * Bits  4-0:  SetOption           ─┘
 * Bit  23:    ImmediateFlag Dest  (set when operand 1 is an immediate value)
 * Bit  22:    ImmediateFlag Src   (set when operand 2 is an immediate value)
 * </pre>
 * All supported mnemonics are defined in {@link NavigationCommandMnemonic}.
 */
public final class NavigationCommandCompiler {

    private NavigationCommandCompiler() {}

    /** Bit 23 — set when operand 1 is an immediate value (not a register reference). */
    public static final long IMM_OP1 = 0x0080_0000L;
    /** Bit 22 — set when operand 2 is an immediate value (not a register reference). */
    public static final long IMM_OP2 = 0x0040_0000L;
    /** Bits 31-29 — OperandCount field position. */
    private static final int OP_CNT_SHIFT = 29;

    /**
     * Returns the 32-bit opcode word for the given mnemonic, with the OperandCount
     * (bits 31-29) and immediate-operand flags (bits 23 and 22) set according to the
     * caller-specified operand kinds.
     *
     * @param mnemonic     symbolic name (case-insensitive), e.g. "PLAY_PL"
     * @param op1Immediate {@code true} when operand 1 is an immediate (literal) value,
     *                     {@code false} when it is a register reference (GPR/PSR)
     * @param op2Immediate {@code true} when operand 2 is an immediate (literal) value,
     *                     {@code false} when it is a register reference (GPR/PSR)
     * @return 32-bit opcode with OperandCount and immediate flags applied
     * @throws IllegalArgumentException for unknown mnemonics
     */
    public static long compile(String mnemonic, boolean op1Immediate, boolean op2Immediate) {
        NavigationCommandMnemonic cmd = NavigationCommandMnemonic.fromString(mnemonic);
        long base = cmd.getOpcode();

        // Set OperandCount in bits 31-29
        base |= ((long) cmd.getOperandCount()) << OP_CNT_SHIFT;

        // Set immediate flags from explicit caller intent
        if (cmd.getOperandCount() >= 1 && op1Immediate) base |= IMM_OP1;
        if (cmd.getOperandCount() >= 2 && op2Immediate) base |= IMM_OP2;
        return base;
    }
}
