package org.brts.common.utils;

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
			}
			else {
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
