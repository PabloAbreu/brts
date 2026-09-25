package org.brts.middle.preview;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/preview/UIUtils.java' is part of BRTS.
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

import java.util.function.Supplier;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UIUtils {

	public static void showAndWait(ScreenModel model, Supplier<JFrame> frameSupplier) {
		log.info("Opening Preview: {}×{}", model.getScreenWidth(), model.getScreenHeight());

		final Object lock = new Object();

		SwingUtilities.invokeLater(() -> {
			JFrame frame = frameSupplier.get();
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

		log.info("Preview window closed");
	}
}
