package org.brts.lowlevel.bdmv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/bdmv/GprState.java' is part of BRTS.
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
import java.util.TreeMap;

/**
 * Mutable bank of 4096 General Purpose Registers, tracking which registers have been explicitly set so that a value of
 * {@code 0} can be distinguished from "never written". Not thread-safe.
 */
public class GprState {

	private static final int REGISTER_COUNT = 4096;

	private final long[] values = new long[REGISTER_COUNT];

	private final boolean[] explicitlySet = new boolean[REGISTER_COUNT];

	public GprState() {
	}

	/**
	 * Seeds this state from a map of register index → value, marking every entry as explicitly set.
	 */
	public GprState(Map<Integer, Long> init) {
		if (init != null) {
			init.forEach(this::set);
		}
	}

	/**
	 * Returns the value of register {@code index}, or {@code 0} if it was never explicitly set.
	 */
	public long get(int index) {
		return values[index];
	}

	public boolean isExplicitlySet(int index) {
		return explicitlySet[index];
	}

	public final void set(int index, long value) {
		values[index] = value;
		explicitlySet[index] = true;
	}

	/**
	 * Returns an unmodifiable snapshot of only the explicitly-set registers (unlike a raw zero-check, this correctly
	 * includes registers that were explicitly set to {@code 0}).
	 */
	public Map<Integer, Long> asMap() {
		Map<Integer, Long> result = new TreeMap<>();
		for (int i = 0; i < REGISTER_COUNT; i++) {
			if (explicitlySet[i]) {
				result.put(i, values[i]);
			}
		}
		return result;
	}

}
