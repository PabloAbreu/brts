/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/WholeM2tsConverter.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;

/**
 * Container-aware conversion boundary; intentionally distinct from the raw elementary-stream {@code VideoConverter}.
 */
public interface WholeM2tsConverter {

	Engine engine();

	/** Builds a parameterized conversion preview without launching an encoder. */
	M2tsConversionPlan prepare(M2tsConversionRequest request) throws IOException;

	/** Future implementation encodes video, preserves/remuxes selected streams, and writes a complete M2TS. */
	default M2tsConversionResult convert(M2tsConversionRequest request) throws IOException {
		throw new UnsupportedOperationException("Whole-M2TS conversion is not implemented for " + engine());
	}

	record M2tsConversionRequest(Path source, Path destination, long targetBytes, int safetyReservePercent,
			List<Long> sampleStartPackets, long samplePacketCount, int minimumQualityValue, int maximumQualityValue,
			int maxSearchIterations) {
		public M2tsConversionRequest {
			sampleStartPackets = sampleStartPackets == null ? List.of() : List.copyOf(sampleStartPackets);
		}
	}

	record M2tsConversionPlan(Engine engine, List<Long> sampleStartPackets, long samplePacketCount,
			int selectedQualityValue, long estimatedOutputBytes, long targetBytes, int searchIterations,
			List<String> warnings) {
		public M2tsConversionPlan {
			sampleStartPackets = List.copyOf(sampleStartPackets);
			warnings = List.copyOf(warnings);
		}
	}

	record M2tsConversionResult(Path outputPath, long outputBytes, M2tsConversionPlan plan) {
	}
}
