package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/DisplaySetPreviewFrame.java' is part of BRTS.
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.brts.common.utils.StopWatch;

import lombok.extern.slf4j.Slf4j;

/**
 * Interactive Swing window for previewing an IGS display set.
 * <p>
 * The window is freely resizable but enforces the original aspect ratio (e.g. 16:9 for 1920×1080). Arrow keys, Enter,
 * and Page Up/Down are used to navigate the menu, mimicking a Blu-ray remote control.
 * <p>
 * When a button is activated, the navigation commands associated with it are briefly displayed as a semi-transparent
 * overlay at the bottom of the screen.
 *
 * <h2>Key bindings</h2>
 * <table>
 * <tr>
 * <td>↑ / ↓ / ← / →</td>
 * <td>Move selection</td>
 * </tr>
 * <tr>
 * <td>Enter / Space</td>
 * <td>Activate (confirm) selected button</td>
 * </tr>
 * <tr>
 * <td>Page Up / Page Down</td>
 * <td>Switch page</td>
 * </tr>
 * <tr>
 * <td>Escape</td>
 * <td>Close window</td>
 * </tr>
 * </table>
 */
@Slf4j
public class DisplaySetPreviewFrame extends JFrame {

	private final DisplaySetPreviewModel model;

	private final NavigationController nav;

	private final DisplaySetPanel panel;

	/** Timer to auto-clear command overlays and refresh for activation animation. */
	private final Timer overlayTimer;

	/** Aspect ratio (width / height). */
	private final double aspectRatio;

	/** Guard to prevent recursive resize events. */
	private boolean resizing = false;

	private static final long CLEAR_ACTIVATION_OVERLAY_DELAY_MS = 2500;

	private static final int OVERLAY_TIMER_PERIOD_MS = 100;

	public DisplaySetPreviewFrame(DisplaySetPreviewModel model) {
		super("BRT — Display Set Preview");
		this.model = model;
		this.nav = new NavigationController(model);
		this.panel = new DisplaySetPanel(model);
		this.aspectRatio = (double) model.getScreenWidth() / model.getScreenHeight();
		StopWatch sw = new StopWatch(log::debug);
		sw.start("Initialising UI");
		initUI();
		sw.start("Initialising key bindings");
		initKeyBindings();
		nav.activateInitialAutoAction();
		sw.stop();

		// Overlay timer: fires every 100 ms to repaint and auto-clear activation
		overlayTimer = new Timer(OVERLAY_TIMER_PERIOD_MS, e -> {
			// Auto-clear activation highlight after overlay expires
			if (model.getActivatedButtonId() >= 0) {
				long elapsed = System.currentTimeMillis() - model.getCommandOverlayTimestamp();
				if (elapsed > CLEAR_ACTIVATION_OVERLAY_DELAY_MS) {
					nav.clearActivation();
				}
				panel.repaint();
			}
		});
		overlayTimer.start();
		sw.close();
	}

	// ── UI initialisation ───────────────────────────────────────────────────

	private void initUI() {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		add(panel, BorderLayout.CENTER);

		// Initial size: fit the native resolution or scale down for the screen
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int initW = Math.min(model.getScreenWidth(), screen.width - 100);
		int initH = (int) Math.round(initW / aspectRatio);
		if (initH > screen.height - 100) {
			initH = screen.height - 100;
			initW = (int) Math.round(initH * aspectRatio);
		}
		// Add some slack for window decorations & status bar
		setSize(initW, initH + DisplaySetPanel.STATUS_BAR_HEIGHT);
		setLocationRelativeTo(null);

		// Enforce aspect ratio on resize
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

		// Stop timer on close
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				overlayTimer.stop();
			}
		});
	}

	private void enforceAspectRatio() {
		Insets insets = getInsets();
		int extraH = DisplaySetPanel.STATUS_BAR_HEIGHT;
		int contentW = getWidth() - insets.left - insets.right;
		int contentH = getHeight() - insets.top - insets.bottom - extraH;

		// Determine which dimension is the "driver"
		double currentAspect = (double) contentW / contentH;
		int newW, newH;
		if (currentAspect > aspectRatio) {
			// Too wide — shrink width to match height
			newH = contentH;
			newW = (int) Math.round(contentH * aspectRatio);
		} else {
			// Too tall — shrink height to match width
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

		bindKey(im, am, "UP", KeyEvent.VK_UP, this::onUp);
		bindKey(im, am, "DOWN", KeyEvent.VK_DOWN, this::onDown);
		bindKey(im, am, "LEFT", KeyEvent.VK_LEFT, this::onLeft);
		bindKey(im, am, "RIGHT", KeyEvent.VK_RIGHT, this::onRight);
		bindKey(im, am, "ENTER", KeyEvent.VK_ENTER, this::onActivate);
		bindKey(im, am, "SPACE", KeyEvent.VK_SPACE, this::onToggleHints);
		bindKey(im, am, "PAGE_UP", KeyEvent.VK_PAGE_UP, this::onPrevPage);
		bindKey(im, am, "PAGE_DOWN", KeyEvent.VK_PAGE_DOWN, this::onNextPage);
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

	private void onUp() {
		nav.moveUp();
		panel.repaint();
	}

	private void onDown() {
		nav.moveDown();
		panel.repaint();
	}

	private void onLeft() {
		nav.moveLeft();
		panel.repaint();
	}

	private void onRight() {
		nav.moveRight();
		panel.repaint();
	}

	private void onActivate() {
		nav.activate();
		panel.repaint();
	}

	private void onToggleHints() {
		model.setDisplayHints(!model.isDisplayHints());
		panel.repaint();
	}

	private void onPrevPage() {
		int idx = model.getCurrentPageIndex();
		if (idx > 0) {
			nav.goToPage(model.getPages().get(idx - 1).getId());
			panel.repaint();
		}
	}

	private void onNextPage() {
		int idx = model.getCurrentPageIndex();
		if (idx < model.getPages().size() - 1) {
			nav.goToPage(model.getPages().get(idx + 1).getId());
			panel.repaint();
		}
	}

	private void onEscape() {
		dispose();
	}

	// ── Public launch helper ────────────────────────────────────────────────

	/**
	 * Opens the preview window on the EDT. This method blocks the calling thread until the window is closed.
	 *
	 * @param model the fully loaded preview model
	 */
	public static void showAndWait(DisplaySetPreviewModel model) {
		log.info("Opening DS Preview: {}×{}, {} pages", model.getScreenWidth(), model.getScreenHeight(),
				model.getPages().size());

		final Object lock = new Object();

		SwingUtilities.invokeLater(() -> {
			DisplaySetPreviewFrame frame = new DisplaySetPreviewFrame(model);
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

		log.info("DS Preview window closed");
	}

}
