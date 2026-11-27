/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/ShrinkBitrateBudget.java' is part of BRTS.
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

import java.math.BigInteger;
import java.util.Set;

/** Conservative transport budget; the final writer's byte limit remains authoritative. */
public final class ShrinkBitrateBudget {
	private ShrinkBitrateBudget() {
	}

	public static int calculate(M2tsShrinkInventory inventory, M2tsShrinkPlan plan, Set<Integer> retainedPids) {
		if (inventory.duration90k() <= 0 || plan.targetBytes() <= 0 || plan.safetyReserveBytes() < 0) {
			throw new IllegalArgumentException("Positive duration/target and nonnegative safety reserve are required");
		}
		BigInteger target = BigInteger.valueOf(plan.targetBytes());
		BigInteger retained = BigInteger.ZERO;
		for (var track : inventory.tracks()) {
			if (track.sourcePacketCount() < 0 || track.codingType() == null) {
				throw new IllegalArgumentException("Invalid packet count/codec for PID " + track.pid());
			}
			if (retainedPids.contains(track.pid()) && !track.codingType().isVideo()) {
				retained = retained
						.add(BigInteger.valueOf(track.sourcePacketCount()).multiply(BigInteger.valueOf(192)));
			}
		}
		// In addition to planner safety, reserve 2% of target for fresh PES/PSI/adaptation overhead.
		BigInteger muxReserve = target.add(BigInteger.valueOf(49)).divide(BigInteger.valueOf(50));
		BigInteger available = target.subtract(BigInteger.valueOf(plan.safetyReserveBytes())).subtract(retained)
				.subtract(muxReserve);
		BigInteger rate = available.multiply(BigInteger.valueOf(184)).multiply(BigInteger.valueOf(8 * 90_000L))
				.divide(BigInteger.valueOf(192)).divide(BigInteger.valueOf(inventory.duration90k()))
				.divide(BigInteger.valueOf(1000));
		if (available.signum() <= 0 || rate.signum() <= 0) {
			throw new IllegalArgumentException("Target cannot accommodate retained streams, overhead and 1 kbps video");
		}
		return rate.min(BigInteger.valueOf(40_000)).intValueExact();
	}
}
