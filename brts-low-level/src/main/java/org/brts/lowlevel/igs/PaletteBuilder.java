package org.brts.lowlevel.igs;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/igs/PaletteBuilder.java' is part of BRTS.
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

import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.PaletteEntry;

import java.awt.image.BufferedImage;
import java.util.*;

/**
 * Builds an {@link IgsPalette} from one or more ARGB {@link BufferedImage}s by collecting unique colours and quantising
 * down to ≤ 256 entries.
 * <p>
 * Also provides colour-space conversion utilities (ARGB ↔ YCbCr BT.709).
 */
public final class PaletteBuilder {

	/** Maximum palette entries in a PG/IG palette. */
	// found on commercial blu-rays : 1..254, not 0..255
	private static final int MAX_ENTRIES = 255;// 256;

	private PaletteBuilder() {
	}

	// ── Colour-space conversion ─────────────────────────────────────────────

	/**
	 * Converts ARGB to YCbCr + alpha (BT.709), the inverse of {@link RleConverter#ycbcrToArgb}.
	 *
	 * @return int[4] = { Y, Cb, Cr, alpha }
	 */
	public static int[] argbToYcbcr(int argb) {
		int a = (argb >> 24) & 0xFF;
		int r = (argb >> 16) & 0xFF;
		int g = (argb >> 8) & 0xFF;
		int b = argb & 0xFF;

		// BT.709 inverse of the decoding in RleConverter:
		// R = Y + 1.5748 * Cr'
		// G = Y - 0.1873 * Cb' - 0.4681 * Cr'
		// B = Y + 1.8556 * Cb'
		// where Cb' = Cb - 128, Cr' = Cr - 128.
		//
		// Forward transform:
		// Y = 0.2126 R + 0.7152 G + 0.0722 B
		// Cb = -0.1146 R - 0.3854 G + 0.5 B + 128
		// Cr = 0.5 R - 0.4542 G - 0.0458 B + 128
		int y = clamp((int) Math.round(0.2126 * r + 0.7152 * g + 0.0722 * b));
		int cb = clamp((int) Math.round(-0.1146 * r - 0.3854 * g + 0.5 * b + 128));
		int cr = clamp((int) Math.round(0.5 * r - 0.4542 * g - 0.0458 * b + 128));

		return new int[] { y, cb, cr, a };
	}

	/**
	 * Creates a {@link PaletteEntry} from an ARGB colour.
	 */
	public static PaletteEntry entryFromArgb(int argb, int entryId) {
		int[] ycbcra = argbToYcbcr(argb);
		PaletteEntry pe = new PaletteEntry();
		pe.setEntryId(entryId);
		pe.setY(ycbcra[0]);
		pe.setCb(ycbcra[1]);
		pe.setCr(ycbcra[2]);
		pe.setAlpha(ycbcra[3]);
		return pe;
	}

	// ── Palette building ────────────────────────────────────────────────────

	/**
	 * Builds a palette from the unique colours found across one or more images.
	 * <p>
	 * If there are more than 255 unique colours (index 0 is reserved for transparent), a simple popularity-based
	 * quantisation is used.
	 *
	 * @param paletteId the palette id to assign
	 * @param images    one or more ARGB images
	 * @return the palette (with index 0 = fully transparent)
	 */
	public static IgsPalette buildFromImages(int paletteId, BufferedImage... images) {
		// Count colour frequency across all images
		Map<Integer, Integer> frequency = new LinkedHashMap<>();
		for (BufferedImage img : images) {
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					int argb = img.getRGB(x, y);
					frequency.merge(argb, 1, Integer::sum);
				}
			}
		}

		// Always reserve index 0 for fully transparent
		int transparent = 0x00000000;
		frequency.remove(transparent);

		// Select up to 255 most-frequent colours (index 0 reserved)
		List<Map.Entry<Integer, Integer>> sorted = new ArrayList<>(frequency.entrySet());
		sorted.sort(Map.Entry.<Integer, Integer>comparingByValue().reversed());

		IgsPalette palette = new IgsPalette();
		palette.setId(paletteId);
		palette.setVersion(0);

		int nextId = 1;
		// Index 1 = transparent
		PaletteEntry transparentEntry = new PaletteEntry();
		transparentEntry.setEntryId(nextId++);
		transparentEntry.setY(16);
		transparentEntry.setCb(128);
		transparentEntry.setCr(128);
		transparentEntry.setAlpha(0);
		palette.getEntries().add(transparentEntry);
		for (Map.Entry<Integer, Integer> e : sorted) {
			if (nextId >= MAX_ENTRIES)
				break;
			palette.getEntries().add(entryFromArgb(e.getKey(), nextId));
			nextId++;
		}

		return palette;
	}

	/**
	 * Builds a palette from an explicit list of ARGB colours. Index 0 is always transparent. The colours are assigned
	 * indices 1..N.
	 *
	 * @param paletteId the palette id to assign
	 * @param colors    ARGB colour values (duplicates are ignored)
	 * @return the palette
	 */
	// AI generated, unused
	public static IgsPalette buildFromColors(int paletteId, int... colors) {
		IgsPalette palette = new IgsPalette();
		palette.setId(paletteId);
		palette.setVersion(0);

		// Index 0 = transparent
		PaletteEntry transparentEntry = new PaletteEntry();
		transparentEntry.setEntryId(0);
		transparentEntry.setY(16);
		transparentEntry.setCb(128);
		transparentEntry.setCr(128);
		transparentEntry.setAlpha(0);
		palette.getEntries().add(transparentEntry);

		Set<Integer> seen = new LinkedHashSet<>();
		int nextId = 1;
		for (int argb : colors) {
			if (nextId >= MAX_ENTRIES)
				break;
			if (argb == 0x00000000)
				continue; // skip transparent, already added
			if (!seen.add(argb))
				continue; // skip duplicates
			palette.getEntries().add(entryFromArgb(argb, nextId));
			nextId++;
		}

		return palette;
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

}
