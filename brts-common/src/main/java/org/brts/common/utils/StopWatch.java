package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/StopWatch.java' is part of BRTS.
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

import java.util.Arrays;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StopWatch {

	private long creationTimeNs = System.nanoTime();

	private long startTimeNs = -1;

	private long endTimeNs = -1;

	private final StopWatchTracer tracer;

	private String message = null;

	private Object[] args = null;

	public void start(String message, Object... args) {
		autoStop();
		startTimeNs = System.nanoTime();
		this.message = message;
		this.args = args;
	}

	public void stop() {
		endTimeNs = System.nanoTime();
		if (tracer != null) {
			long elapsedMs = (endTimeNs - startTimeNs) / 1_000_000;
			Object[] params = null;
			if (args == null || args.length == 0) {
				params = new Object[] { elapsedMs };
			} else {
				params = Arrays.copyOf(args, args.length + 1);
				params[args.length] = elapsedMs;
			}
			tracer.trace(message + " ({} ms)", params);
		}
		message = null;
		args = null;
	}

	private void autoStop() {
		if (message != null) {
			stop();
		}
	}

	public void close() {
		autoStop();
		startTimeNs = creationTimeNs;
		message = "Total time";
		args = null;
		stop();
	}

	public interface StopWatchTracer {

		void trace(String message, Object... args);

	}

}
