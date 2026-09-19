package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/bdmv/OperandKind.java' is part of BRTS.
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
 * Describes the kind of a 32-bit operand in an HDMV navigation command.
 * <p>
 * When the opcode's immediate flag for an operand is set, the operand is an {@link #IMMEDIATE} literal value. When the
 * flag is clear, the operand is a register reference — either a {@link #GPR} (General Purpose Register) or a
 * {@link #PSR} (Player Status Register), distinguished by bit 31 of the operand value.
 */
public enum OperandKind {

	/** The operand is a literal (immediate) 32-bit value. */
	IMMEDIATE,
	/** The operand is a reference to a General Purpose Register (index in bits 11:0). */
	GPR,
	/** The operand is a reference to a Player Status Register (index in bits 6:0). */
	PSR

}
