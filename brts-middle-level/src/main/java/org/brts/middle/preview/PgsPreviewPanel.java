package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/PgsPreviewPanel.java' is part of BRTS.
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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import org.brts.lowlevel.igs.model.CompositionObject;

/**
 * Swing panel that renders the current state of a {@link PgsPreviewModel}: a checkerboard or video-snapshot background,
 * the current subtitle item's bitmaps, a bottom horizontal ruler (one tick per item, click-to-seek), and a status bar.
 */
public class PgsPreviewPanel extends JPanel {

	public static final int STATUS_BAR_HEIGHT = 24;

	public static final int RULER_HEIGHT = 28;

	private static final Color RULER_BG = new Color(0x15, 0x15, 0x1C);

	private static final Color RULER_TICK = new Color(0x80, 0xA0, 0xC0);

	private static final Color RULER_TICK_CURRENT = new Color(0x00, 0xD0, 0xFF);

	private final PgsPreviewModel model;

	private final BufferedImage checkerboardTile = CheckerboardRenderer.buildTile();

	public PgsPreviewPanel(PgsPreviewModel model) {
		this.model = model;
		setBackground(Color.BLACK);
		setDoubleBuffered(true);
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(model.getScreenWidth(), model.getScreenHeight() + RULER_HEIGHT + STATUS_BAR_HEIGHT);
	}

	/**
	 * Computes the viewport rectangle (video area) that fits the stream's aspect ratio inside the panel, above the
	 * ruler and status bar.
	 */
	private Rectangle computeViewport() {
		int pw = getWidth();
		int ph = Math.max(1, getHeight() - RULER_HEIGHT - STATUS_BAR_HEIGHT);
		double nativeW = model.getScreenWidth();
		double nativeH = model.getScreenHeight();
		double aspect = nativeW / nativeH;

		int viewW, viewH;
		if ((double) pw / ph > aspect) {
			viewH = ph;
			viewW = (int) Math.round(ph * aspect);
		} else {
			viewW = pw;
			viewH = (int) Math.round(pw / aspect);
		}
		int x = (pw - viewW) / 2;
		return new Rectangle(x, 0, viewW, viewH);
	}

	private Rectangle computeRulerBounds(Rectangle vp) {
		return new Rectangle(0, vp.y + vp.height, getWidth(), RULER_HEIGHT);
	}

	/**
	 * Maps a mouse click to the nearest subtitle item index, or {@code null} if the click is outside the ruler.
	 */
	public Integer hitTestRuler(int mouseX, int mouseY) {
		int itemCount = model.getItems().size();
		if (itemCount == 0) {
			return null;
		}
		Rectangle ruler = computeRulerBounds(computeViewport());
		if (!ruler.contains(mouseX, mouseY)) {
			return null;
		}
		int index = (int) Math.round((mouseX - ruler.x) / (double) ruler.width * (itemCount - 1));
		return Math.max(0, Math.min(itemCount - 1, index));
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			Rectangle vp = computeViewport();
			g2.setClip(vp.x, vp.y, vp.width, vp.height);
			drawBackground(g2, vp);

			double scaleX = vp.width / (double) model.getScreenWidth();
			double scaleY = vp.height / (double) model.getScreenHeight();
			AffineTransform xf = new AffineTransform();
			xf.translate(vp.x, vp.y);
			xf.scale(scaleX, scaleY);
			g2.setTransform(xf);
			drawCurrentItem(g2);
			g2.setTransform(new AffineTransform());

			g2.setClip(null);
			drawRuler(g2, vp);
			drawStatusBar(g2, vp);
		} finally {
			g2.dispose();
		}
	}

	private void drawBackground(Graphics2D g2, Rectangle vp) {
		BufferedImage snapshot = model.getBackgroundMode() == PgsPreviewModel.BackgroundMode.VIDEO_SNAPSHOT
				? model.getSnapshotCache().get(model.getCurrentIndex())
				: null;
		if (snapshot != null) {
			g2.drawImage(snapshot, vp.x, vp.y, vp.width, vp.height, null);
		} else {
			CheckerboardRenderer.paint(g2, vp, checkerboardTile);
		}
	}

	private void drawCurrentItem(Graphics2D g2) {
		PgsSubtitleItem item = model.getCurrentItem();
		if (item == null) {
			return;
		}
		for (PgsSubtitleItem.RenderedObject ro : item.getRenderedObjects()) {
			CompositionObject co = ro.getCompositionObject();
			BufferedImage image = ro.getImage();
			if (image == null) {
				continue;
			}
			g2.drawImage(image, co.getX(), co.getY(), null);
		}
	}

	private void drawRuler(Graphics2D g2, Rectangle vp) {
		Rectangle ruler = computeRulerBounds(vp);
		g2.setColor(RULER_BG);
		g2.fillRect(ruler.x, ruler.y, ruler.width, ruler.height);

		int itemCount = model.getItems().size();
		if (itemCount == 0) {
			return;
		}
		int currentIndex = model.getCurrentIndex();
		for (int i = 0; i < itemCount; i++) {
			int x = ruler.x + (int) Math.round(i / (double) Math.max(1, itemCount - 1) * (ruler.width - 1));
			g2.setColor(i == currentIndex ? RULER_TICK_CURRENT : RULER_TICK);
			int tickH = i == currentIndex ? ruler.height : ruler.height / 2;
			g2.drawLine(x, ruler.y + ruler.height - tickH, x, ruler.y + ruler.height);
		}
	}

	private void drawStatusBar(Graphics2D g2, Rectangle vp) {
		int itemCount = model.getItems().size();
		PgsSubtitleItem item = model.getCurrentItem();
		String status;
		if (item != null) {
			status = String.format("Item %d/%d  |  PTS %s  |  %d×%d  |  ←→ Navigate  Click ruler to seek",
					item.getIndex() + 1, itemCount, formatPts(item.getShowPtsTicks() - model.getBasePtsTicks()),
					model.getScreenWidth(), model.getScreenHeight());
		} else {
			status = "No subtitle items found";
		}

		int barY = vp.y + vp.height + RULER_HEIGHT;
		g2.setColor(new Color(0x20, 0x20, 0x30));
		g2.fillRect(0, barY, getWidth(), STATUS_BAR_HEIGHT);
		g2.setColor(new Color(0xDD, 0xAA, 0xAA));
		g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
		g2.drawString(status, 8, barY + (STATUS_BAR_HEIGHT * 2 / 3));
	}

	private String formatPts(long ptsTicks) {
		long totalMs = ptsTicks / 90;
		long ms = totalMs % 1000;
		long totalSeconds = totalMs / 1000;
		long s = totalSeconds % 60;
		long m = (totalSeconds / 60) % 60;
		long h = totalSeconds / 3600;
		return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
	}

}
