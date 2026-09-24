package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/CheckerboardRenderer.java' is part of BRTS.
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Programmatically generated checkerboard pattern used as a "video plane" stand-in background, shared by the display
 * set and PGS preview panels.
 */
public final class CheckerboardRenderer {

	private static final int CHECKER_SIZE = 32;

	private static final Color CHECKER_LIGHT = new Color(0x55, 0x55, 0x55);

	private static final Color CHECKER_DARK = new Color(0x33, 0x33, 0x33);

	private CheckerboardRenderer() {
	}

	/**
	 * Builds a pre-rendered {@code 2*CHECKER_SIZE} square tile that can be repeated to fill any area.
	 */
	public static BufferedImage buildTile() {
		int tileSize = CHECKER_SIZE * 2;
		BufferedImage tile = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_RGB);
		Graphics2D tg = tile.createGraphics();
		try {
			tg.setColor(CHECKER_LIGHT);
			tg.fillRect(0, 0, tileSize, tileSize);
			tg.setColor(CHECKER_DARK);
			tg.fillRect(0, CHECKER_SIZE, CHECKER_SIZE, CHECKER_SIZE);
			tg.fillRect(CHECKER_SIZE, 0, CHECKER_SIZE, CHECKER_SIZE);
		} finally {
			tg.dispose();
		}
		return tile;
	}

	/**
	 * Paints {@code tile} tiled across {@code viewport} in device (untransformed) coordinates.
	 */
	public static void paint(Graphics2D g2, Rectangle viewport, BufferedImage tile) {
		AffineTransform saved = g2.getTransform();
		g2.setTransform(new AffineTransform());
		try {
			int tileSize = tile.getWidth();
			for (int y = viewport.y; y < viewport.y + viewport.height; y += tileSize) {
				for (int x = viewport.x; x < viewport.x + viewport.width; x += tileSize) {
					g2.drawImage(tile, x, y, null);
				}
			}
		} finally {
			g2.setTransform(saved);
		}
	}

}
