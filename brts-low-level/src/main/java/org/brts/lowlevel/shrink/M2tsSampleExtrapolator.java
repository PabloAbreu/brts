/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsSampleExtrapolator.java' is part of BRTS.
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

import java.util.List;

/** Extrapolates several sample outputs to full-duration bytes; result is explicitly an estimate, not a guarantee. */
public final class M2tsSampleExtrapolator {

	public Estimate extrapolate(List<M2tsSampleEncoder.SampleResult> samples, long fullDuration90k) {
		if (samples == null || samples.isEmpty() || fullDuration90k <= 0) {
			throw new IllegalArgumentException("At least one sample and a positive full duration are required");
		}
		long duration = 0;
		long bytes = 0;
		for (var sample : samples) {
			duration = Math.addExact(duration, sample.sourceDuration90k());
			bytes = Math.addExact(bytes, sample.encodedBytes());
		}
		if (duration > fullDuration90k) {
			throw new IllegalArgumentException("Sample durations exceed full clip duration");
		}
		long projected = Math.multiplyExact(bytes, fullDuration90k) / duration;
		return new Estimate(projected, duration, samples.size(), false);
	}

	public record Estimate(long projectedVideoBytes, long sampledDuration90k, int sampleCount,
			boolean guaranteedUpperBound) {
	}
}
