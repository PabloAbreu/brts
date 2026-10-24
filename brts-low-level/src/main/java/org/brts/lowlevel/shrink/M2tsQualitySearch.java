/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsQualitySearch.java' is part of BRTS.
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

package org.brts.lowlevel.shrink;

import java.util.Objects;
import java.util.TreeMap;
import java.util.function.IntToLongFunction;

/** Bracketed search over a monotonic quality parameter using full-clip byte estimates from sample encodes. */
public final class M2tsQualitySearch {

	public Result search(int minimumValue, int maximumValue, long maximumBytes, int maxIterations,
			boolean increasingValueShrinksOutput, IntToLongFunction estimatedFullOutputBytes) {
		Objects.requireNonNull(estimatedFullOutputBytes, "estimatedFullOutputBytes");
		if (minimumValue >= maximumValue || maximumBytes <= 0 || maxIterations <= 0) {
			throw new IllegalArgumentException(
					"Search bounds, byte limit, and iteration limit must be positive and ordered");
		}
		TreeMap<Integer, Long> observations = new TreeMap<>();
		long minSize = estimate(estimatedFullOutputBytes, minimumValue, observations, increasingValueShrinksOutput);
		long maxSize = estimate(estimatedFullOutputBytes, maximumValue, observations, increasingValueShrinksOutput);
		boolean minFits = minSize <= maximumBytes;
		boolean maxFits = maxSize <= maximumBytes;
		if (increasingValueShrinksOutput ? !maxFits : !minFits) {
			throw new IllegalArgumentException("No feasible quality value within the requested search bounds");
		}
		if (increasingValueShrinksOutput ? minFits : maxFits) {
			int best = increasingValueShrinksOutput ? minimumValue : maximumValue;
			long bytes = increasingValueShrinksOutput ? minSize : maxSize;
			return new Result(best, bytes, 0, true);
		}
		int low = minimumValue;
		int high = maximumValue;
		int selected = increasingValueShrinksOutput ? maximumValue : minimumValue;
		long selectedBytes = increasingValueShrinksOutput ? maxSize : minSize;
		int iterations = 0;
		while (high - low > 1 && iterations < maxIterations) {
			int middle = low + (high - low) / 2;
			long bytes = estimate(estimatedFullOutputBytes, middle, observations, increasingValueShrinksOutput);
			boolean fits = bytes <= maximumBytes;
			if (increasingValueShrinksOutput) {
				if (fits) {
					high = middle;
					selected = middle;
					selectedBytes = bytes;
				} else {
					low = middle;
				}
			} else if (fits) {
				low = middle;
				selected = middle;
				selectedBytes = bytes;
			} else {
				high = middle;
			}
			iterations++;
		}
		return new Result(selected, selectedBytes, iterations, high - low <= 1);
	}

	private static long estimate(IntToLongFunction estimator, int value, TreeMap<Integer, Long> observations,
			boolean increasingValueShrinksOutput) {
		long size = estimator.applyAsLong(value);
		if (size < 0) {
			throw new IllegalArgumentException("Estimated output size must not be negative at quality value " + value);
		}
		var lower = observations.lowerEntry(value);
		var higher = observations.higherEntry(value);
		if (lower != null && (increasingValueShrinksOutput ? lower.getValue() < size : lower.getValue() > size)
				|| higher != null
						&& (increasingValueShrinksOutput ? higher.getValue() > size : higher.getValue() < size)) {
			throw new IllegalArgumentException(
					"Sample size estimates violate the declared monotonic direction near quality value " + value);
		}
		observations.put(value, size);
		return size;
	}

	public record Result(int qualityValue, long estimatedBytes, int iterations, boolean converged) {
	}
}
