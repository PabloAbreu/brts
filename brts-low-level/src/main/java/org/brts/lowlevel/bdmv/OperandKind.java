package org.brts.lowlevel.bdmv;

/**
 * Describes the kind of a 32-bit operand in an HDMV navigation command.
 * <p>
 * When the opcode's immediate flag for an operand is set, the operand is an
 * {@link #IMMEDIATE} literal value. When the flag is clear, the operand is a
 * register reference — either a {@link #GPR} (General Purpose Register) or a
 * {@link #PSR} (Player Status Register), distinguished by bit 31 of the
 * operand value.
 */
public enum OperandKind {
    /** The operand is a literal (immediate) 32-bit value. */
    IMMEDIATE,
    /** The operand is a reference to a General Purpose Register (index in bits 11:0). */
    GPR,
    /** The operand is a reference to a Player Status Register (index in bits 6:0). */
    PSR
}
