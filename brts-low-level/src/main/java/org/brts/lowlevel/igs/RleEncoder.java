package org.brts.lowlevel.igs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/RleEncoder.java' is part of BRTS.
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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.PaletteEntry;

/**
 * Encodes an ARGB {@link BufferedImage} into PG/IG RLE-compressed bytes suitable for an Object Definition Segment
 * (ODS).
 * <p>
 * This is the inverse of {@link RleConverter#decodeRle}.
 *
 * <h2>RLE encoding (PG/IG Blu-ray spec)</h2>
 * <ul>
 * <li>Non-zero byte → single pixel of that colour index</li>
 * <li>{@code 0x00 0x00} → end-of-line marker</li>
 * <li>{@code 0x00 0b00LLLLLL} → L pixels of colour 0 (L ≤ 63)</li>
 * <li>{@code 0x00 0b01LLLLLL LLLLLLLL} → 14-bit L pixels of colour 0</li>
 * <li>{@code 0x00 0b10LLLLLL CC} → L pixels of colour C (L ≤ 63)</li>
 * <li>{@code 0x00 0b11LLLLLL LLLLLLLL CC} → 14-bit L pixels of colour C</li>
 * </ul>
 */
public final class RleEncoder {

	private RleEncoder() {
	}

	/**
	 * Encodes an ARGB image to RLE bytes using the given palette. Each pixel in the image is matched to its nearest
	 * palette entry index.
	 *
	 * @param image   the source ARGB image
	 * @param palette the palette to quantise against
	 * @return RLE-encoded bytes
	 */
	public static byte[] encode(BufferedImage image, IgsPalette palette) {
		int[] argbPalette = RleConverter.buildArgbPalette(palette);
		int width = image.getWidth();
		int height = image.getHeight();

		// Build a reverse lookup: ARGB → palette index (for fast matching)
		Map<Integer, Integer> argbToIndex = new HashMap<>();
		for (PaletteEntry e : palette.getEntries()) {
			int idx = e.getEntryId() & 0xFF;
			argbToIndex.put(argbPalette[idx], idx);
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();

		for (int y = 0; y < height; y++) {
			int x = 0;
			while (x < width) {
				int argb = image.getRGB(x, y);
				int colorIdx = resolveIndex(argb, argbToIndex, argbPalette);

				// Count run length
				int runLen = 1;
				while (x + runLen < width
						&& resolveIndex(image.getRGB(x + runLen, y), argbToIndex, argbPalette) == colorIdx) {
					runLen++;
				}

				encodeRun(out, colorIdx, runLen);
				x += runLen;
			}
			// End-of-line marker
			out.write(0x00);
			out.write(0x00);
		}

		return out.toByteArray();
	}

	/**
	 * Encodes an indexed image (pixel values are palette indices) to RLE bytes.
	 *
	 * @param indexedPixels 2D array [height][width] of palette indices
	 * @param width         image width
	 * @param height        image height
	 * @return RLE-encoded bytes
	 */
	public static byte[] encodeIndexed(int[][] indexedPixels, int width, int height) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		for (int y = 0; y < height; y++) {
			int x = 0;
			while (x < width) {
				int colorIdx = indexedPixels[y][x] & 0xFF;
				int runLen = 1;
				while (x + runLen < width && (indexedPixels[y][x + runLen] & 0xFF) == colorIdx) {
					runLen++;
				}
				encodeRun(out, colorIdx, runLen);
				x += runLen;
			}
			// End-of-line
			out.write(0x00);
			out.write(0x00);
		}

		return out.toByteArray();
	}

	// ── Internals ───────────────────────────────────────────────────────────

	private static void encodeRun(ByteArrayOutputStream out, int colorIdx, int runLen) {
		if (colorIdx != 0 && runLen == 1) {
			// Single pixel of non-zero colour — just write the index
			out.write(colorIdx);
			return;
		}

		if (colorIdx == 0) {
			// Runs of colour 0 (transparent)
			if (runLen <= 63) {
				// 0x00 0b00LLLLLL
				out.write(0x00);
				out.write(runLen & 0x3F);
			} else {
				// 0x00 0b01LLLLLL LLLLLLLL (14-bit length)
				out.write(0x00);
				out.write(0x40 | ((runLen >> 8) & 0x3F));
				out.write(runLen & 0xFF);
			}
		} else {
			// Runs of non-zero colour
			if (runLen <= 63) {
				// 0x00 0b10LLLLLL CC
				out.write(0x00);
				out.write(0x80 | (runLen & 0x3F));
				out.write(colorIdx);
			} else {
				// 0x00 0b11LLLLLL LLLLLLLL CC (14-bit length)
				out.write(0x00);
				out.write(0xC0 | ((runLen >> 8) & 0x3F));
				out.write(runLen & 0xFF);
				out.write(colorIdx);
			}
		}
	}

	/**
	 * Resolve an ARGB pixel to its palette index. Uses exact match first, then falls back to nearest-colour search.
	 */
	private static int resolveIndex(int argb, Map<Integer, Integer> argbToIndex, int[] argbPalette) {
		Integer idx = argbToIndex.get(argb);
		if (idx != null)
			return idx;

		// Fallback: nearest colour (Euclidean distance in ARGB space)
		int bestIdx = 0;
		long bestDist = Long.MAX_VALUE;
		for (int i = 0; i < 256; i++) {
			long dist = colorDistance(argb, argbPalette[i]);
			if (dist < bestDist) {
				bestDist = dist;
				bestIdx = i;
			}
		}
		// Cache for future lookups
		argbToIndex.put(argb, bestIdx);
		return bestIdx;
	}

	/**
	 * Squared Euclidean distance between two ARGB colours (includes alpha).
	 */
	private static long colorDistance(int c1, int c2) {
		int da = ((c1 >> 24) & 0xFF) - ((c2 >> 24) & 0xFF);
		int dr = ((c1 >> 16) & 0xFF) - ((c2 >> 16) & 0xFF);
		int dg = ((c1 >> 8) & 0xFF) - ((c2 >> 8) & 0xFF);
		int db = (c1 & 0xFF) - (c2 & 0xFF);
		return (long) da * da + (long) dr * dr + (long) dg * dg + (long) db * db;
	}

}
