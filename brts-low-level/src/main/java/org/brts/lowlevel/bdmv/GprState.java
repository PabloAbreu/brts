package org.brts.lowlevel.bdmv;

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
