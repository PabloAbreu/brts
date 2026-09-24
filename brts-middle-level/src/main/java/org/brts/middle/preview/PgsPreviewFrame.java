package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/PgsPreviewFrame.java' is part of BRTS.
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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.brts.lowlevel.thumbnail.VideoFrameGrabber;

import lombok.extern.slf4j.Slf4j;

/**
 * Interactive Swing window for previewing a PGS subtitle stream.
 * <p>
 * The window is freely resizable but enforces the stream's native aspect ratio. LEFT/RIGHT arrow keys jump to the
 * previous/next subtitle item; clicking on the bottom ruler jumps directly to the corresponding item. When the
 * background mode is {@link PgsPreviewModel.BackgroundMode#VIDEO_SNAPSHOT}, video frames are grabbed lazily on a
 * background thread and cached in the model.
 */
@Slf4j
public class PgsPreviewFrame extends JFrame {

	private final PgsPreviewModel model;

	private final PgsPreviewPanel panel;

	/** Aspect ratio (width / height), including the ruler and status bar bands. */
	private final double aspectRatio;

	private boolean resizing = false;

	private VideoFrameGrabber frameGrabber;

	public PgsPreviewFrame(PgsPreviewModel model) {
		super("BRTS — PGS Preview");
		this.model = model;
		this.panel = new PgsPreviewPanel(model);
		this.aspectRatio = (double) model.getScreenWidth() / model.getScreenHeight();

		if (model.getBackgroundMode() == PgsPreviewModel.BackgroundMode.VIDEO_SNAPSHOT) {
			try {
				frameGrabber = new VideoFrameGrabber(model.getVideoSource());
			} catch (IOException e) {
				log.warn("Could not open video snapshot source {}, falling back to checkerboard",
						model.getVideoSource(), e);
				model.setBackgroundMode(PgsPreviewModel.BackgroundMode.CHECKERBOARD);
			}
		}

		initUI();
		initKeyBindings();
		requestSnapshotForCurrentItem();
	}

	// ── UI initialisation ───────────────────────────────────────────────────

	private void initUI() {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		add(panel, BorderLayout.CENTER);

		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int initW = Math.min(model.getScreenWidth(), screen.width - 100);
		int initH = (int) Math.round(initW / aspectRatio) + PgsPreviewPanel.RULER_HEIGHT
				+ PgsPreviewPanel.STATUS_BAR_HEIGHT;
		if (initH > screen.height - 100) {
			initH = screen.height - 100;
			initW = (int) Math
					.round((initH - PgsPreviewPanel.RULER_HEIGHT - PgsPreviewPanel.STATUS_BAR_HEIGHT) * aspectRatio);
		}
		setSize(initW, initH);
		setLocationRelativeTo(null);

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (resizing)
					return;
				resizing = true;
				try {
					enforceAspectRatio();
				} finally {
					resizing = false;
				}
			}
		});

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				if (frameGrabber != null) {
					frameGrabber.close();
				}
			}
		});

		panel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				Integer index = panel.hitTestRuler(e.getX(), e.getY());
				if (index != null) {
					goToItem(index);
				}
			}
		});
	}

	private void enforceAspectRatio() {
		Insets insets = getInsets();
		int extraH = PgsPreviewPanel.RULER_HEIGHT + PgsPreviewPanel.STATUS_BAR_HEIGHT;
		int contentW = getWidth() - insets.left - insets.right;
		int contentH = getHeight() - insets.top - insets.bottom - extraH;

		double currentAspect = (double) contentW / contentH;
		int newW, newH;
		if (currentAspect > aspectRatio) {
			newH = contentH;
			newW = (int) Math.round(contentH * aspectRatio);
		} else {
			newW = contentW;
			newH = (int) Math.round(contentW / aspectRatio);
		}

		int frameW = newW + insets.left + insets.right;
		int frameH = newH + insets.top + insets.bottom + extraH;

		if (frameW != getWidth() || frameH != getHeight()) {
			setSize(frameW, frameH);
		}
	}

	// ── Key bindings ────────────────────────────────────────────────────────

	private void initKeyBindings() {
		InputMap im = panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = panel.getActionMap();

		bindKey(im, am, "LEFT", KeyEvent.VK_LEFT, this::onPrevious);
		bindKey(im, am, "RIGHT", KeyEvent.VK_RIGHT, this::onNext);
		bindKey(im, am, "ESCAPE", KeyEvent.VK_ESCAPE, this::onEscape);
	}

	private void bindKey(InputMap im, ActionMap am, String name, int keyCode, Runnable action) {
		im.put(KeyStroke.getKeyStroke(keyCode, 0), name);
		am.put(name, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		});
	}

	private void onPrevious() {
		goToItem(model.getCurrentIndex() - 1);
	}

	private void onNext() {
		goToItem(model.getCurrentIndex() + 1);
	}

	private void onEscape() {
		dispose();
	}

	// ── Navigation ──────────────────────────────────────────────────────────

	private void goToItem(int index) {
		model.goToItem(index);
		panel.repaint();
		requestSnapshotForCurrentItem();
	}

	/**
	 * Grabs (or reuses the cached) video snapshot for the current item on a background thread, then repaints.
	 */
	private void requestSnapshotForCurrentItem() {
		if (frameGrabber == null) {
			return;
		}
		PgsSubtitleItem item = model.getCurrentItem();
		if (item == null || model.getSnapshotCache().containsKey(item.getIndex())) {
			return;
		}
		int index = item.getIndex();
		double seconds = item.getShowPtsTicks() / 90000.0;

		new SwingWorker<BufferedImage, Void>() {
			@Override
			protected BufferedImage doInBackground() throws Exception {
				return frameGrabber.grabFrameAt(seconds);
			}

			@Override
			protected void done() {
				try {
					BufferedImage snapshot = get();
					if (snapshot != null) {
						model.getSnapshotCache().put(index, snapshot);
						if (model.getCurrentIndex() == index) {
							panel.repaint();
						}
					}
				} catch (Exception e) {
					log.warn("Failed to grab video snapshot at {}s for item {}", seconds, index, e);
				}
			}
		}.execute();
	}

	// ── Public launch helper ────────────────────────────────────────────────

	/**
	 * Opens the preview window on the EDT. This method blocks the calling thread until the window is closed.
	 *
	 * @param model the fully loaded preview model
	 */
	public static void showAndWait(PgsPreviewModel model) {
		log.info("Opening PGS Preview: {}×{}, {} item(s)", model.getScreenWidth(), model.getScreenHeight(),
				model.getItems().size());

		final Object lock = new Object();

		SwingUtilities.invokeLater(() -> {
			PgsPreviewFrame frame = new PgsPreviewFrame(model);
			frame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosed(WindowEvent e) {
					synchronized (lock) {
						lock.notifyAll();
					}
				}
			});
			frame.setVisible(true);
		});

		synchronized (lock) {
			try {
				lock.wait();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		log.info("PGS Preview window closed");
	}

}
