/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/m2ts/M2tsVideoEpScan.java' is part of BRTS.
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

package org.brts.common.m2ts;

import java.util.ArrayList;
import java.util.List;

import org.brts.common.m2ts.model.M2tsTimingManifest.EntryPoint;
import org.brts.common.m2ts.model.M2tsTimingManifest.VideoScan;
import org.brts.common.model.StreamCodingType;

/** Shared collector for the CLPI-compatible video entry-point scan. */
public final class M2tsVideoEpScan {
	private final VideoEntryPointScanner scanner;
	private final List<EntryPoint> entries = new ArrayList<>();
	private long firstPts = -1;
	private long lastPts = -1;
	private long wraps;
	private long previousRaw = -1;

	public M2tsVideoEpScan(StreamCodingType codec) {
		scanner = new VideoEntryPointScanner(codec, this::unwrap, this::entry);
	}

	public void accept(byte[] data, int offset, int length, boolean start, long spn) {
		scanner.accept(data, offset, length, start, spn);
	}

	public void reset() {
		scanner.reset();
	}

	public VideoScan result() {
		return new VideoScan(firstPts < 0 ? null : firstPts, lastPts < 0 ? null : lastPts, entries,
				scanner.malformedHeaders(), scanner.untimestampedPictures());
	}

	private long unwrap(long raw) {
		if (previousRaw >= 0 && raw < previousRaw && previousRaw - raw > M2tsTiming.TIMESTAMP_WRAP / 2)
			wraps++;
		previousRaw = raw;
		long pts = Math.addExact(raw, Math.multiplyExact(wraps, M2tsTiming.TIMESTAMP_WRAP));
		if (firstPts < 0 || pts < firstPts)
			firstPts = pts;
		if (pts > lastPts)
			lastPts = pts;
		return pts;
	}

	private void entry(long pts, long spn) {
		if (!entries.isEmpty()) {
			EntryPoint previous = entries.getLast();
			if (previous.pts90k() == pts && previous.spn() == spn)
				return;
		}
		entries.add(new EntryPoint(pts, spn));
	}
}
