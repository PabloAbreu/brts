/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-common/src/main/java/org/brts/common/video/FrameRate.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

package org.brts.common.video;

import java.util.List;

/** Exact frames per second; fractional broadcast rates must not be rounded to integers. */
public record FrameRate(int numerator, int denominator, int frameRateCode) {

	public static final FrameRate FPS_23976 = new FrameRate(24000, 1001, 1);
	public static final FrameRate FPS_24 = new FrameRate(24, 1, 2);
	public static final FrameRate FPS_25 = new FrameRate(25, 1, 3);
	public static final FrameRate FPS_2997 = new FrameRate(30000, 1001, 4);
	public static final FrameRate FPS_30 = new FrameRate(30, 1, -1);
	public static final FrameRate FPS_50 = new FrameRate(50, 1, 6);
	public static final FrameRate FPS_5994 = new FrameRate(60000, 1001, 7);
	public static final FrameRate FPS_60 = new FrameRate(60, 1, -1);

	private static final List<FrameRate> supportedRates = List.of(FPS_23976, FPS_24, FPS_25, FPS_2997, FPS_30, FPS_50,
			FPS_5994, FPS_60);

	public FrameRate {
		if (numerator <= 0 || denominator <= 0)
			throw new IllegalArgumentException("Frame-rate numerator and denominator must be positive");
		int a = numerator, b = denominator;
		while (b != 0) {
			int remainder = a % b;
			a = b;
			b = remainder;
		}
		numerator /= a;
		denominator /= a;
	}

	public FrameRate(int numerator, int denominator) {
		this(numerator, denominator, of(numerator, denominator).frameRateCode());
	}

	public static FrameRate of(int numerator, int denominator) {
		FrameRate normalized = new FrameRate(numerator, denominator, -1);
		for (FrameRate rate : supportedRates())
			if (rate.numerator == normalized.numerator && rate.denominator == normalized.denominator)
				return rate;
		return normalized;
	}

	public static FrameRate fromCode(int frameRateCode) {
		for (FrameRate rate : supportedRates())
			if (frameRateCode >= 0 && rate.frameRateCode == frameRateCode)
				return rate;
		throw new IllegalArgumentException("Unsupported frame rate code: " + frameRateCode);
	}

	/** Accepts exact fractions and customary decimal aliases such as 23.976. */
	public static FrameRate parse(String value) {
		if (value == null || value.isBlank())
			throw new IllegalArgumentException("Frame rate must not be blank");
		String[] parts = value.trim().split("/", -1);
		if (parts.length == 2)
			return of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
		double fps = Double.parseDouble(value.trim());
		for (FrameRate rate : supportedRates())
			if (Math.abs(rate.asDouble() - fps) < 0.0006)
				return rate;
		throw new IllegalArgumentException("Unsupported frame rate: " + value);
	}

	public static List<FrameRate> supportedRates() {
		return supportedRates;
	}

	public double asDouble() {
		return (double) numerator / denominator;
	}

	public int nominalGopFrames() {
		return (int) Math.round(asDouble());
	}

	@Override
	public String toString() {
		return numerator + "/" + denominator;
	}
}
