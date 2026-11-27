/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/shrink/M2tsSampleEncoder.java' is part of BRTS.
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

import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;

/** Injectable boundary for one short, representative source sample encoded at one engine quality value. */
@FunctionalInterface
public interface M2tsSampleEncoder {

	SampleResult encode(Path sourceM2ts, long startPacket, long packetCount, Engine engine, int qualityValue)
			throws IOException;

	record SampleResult(long sourceDuration90k, long encodedBytes) {
		public SampleResult {
			if (sourceDuration90k <= 0 || encodedBytes < 0) {
				throw new IllegalArgumentException("A sample needs positive duration and nonnegative encoded bytes");
			}
		}
	}
}
