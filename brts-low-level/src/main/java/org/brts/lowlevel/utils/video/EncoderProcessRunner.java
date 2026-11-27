/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/EncoderProcessRunner.java' is part of BRTS.
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
package org.brts.lowlevel.utils.video;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shell-free encoder execution with a shared 32 KiB byte tail, 4 KiB line excerpts and at most 100 warnings. A
 * successful exit returns immutable, best-effort warnings, not a bitstream compliance certificate. Keyword detection is
 * conservative: ignored/unsupported/fallback/downgraded/disabled settings (including uhd-bd/bluray) and VBV underflow
 * are reported, regardless of encoder-specific warning prefixes. Timeout covers execution and output draining; even
 * without a timeout, post-exit draining and failure cleanup have a two-second budget each. UTF-8 excerpts can contain
 * replacement characters at truncation boundaries. Descendant termination is best-effort: Java cannot guarantee capture
 * of detached/reparented children or reap grandchildren. An uncooperative stream may leave a daemon virtual reader
 * behind after bounded cleanup.
 */
final class EncoderProcessRunner {
	private static final int TAIL_BYTES = 32 * 1024;
	private static final int LINE_BYTES = 4 * 1024;
	private static final int MAX_WARNINGS = 100;
	private static final long CLEANUP_NANOS = TimeUnit.SECONDS.toNanos(2);
	private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

	@FunctionalInterface
	interface Launcher {
		Process start(List<String> command) throws IOException;
	}

	private final Launcher launcher;

	EncoderProcessRunner() {
		this(command -> new ProcessBuilder(command).start());
	}

	EncoderProcessRunner(Launcher launcher) {
		this.launcher = Objects.requireNonNull(launcher, "launcher");
	}

	/** Runs tokenized arguments without shell expansion. Null timeout means no execution time limit. */
	List<String> run(List<String> command, Duration timeout) throws IOException {
		List<String> arguments = List.copyOf(Objects.requireNonNull(command, "command"));
		if (arguments.isEmpty() || arguments.getFirst().isBlank()) {
			throw new IllegalArgumentException("Encoder command must contain a nonblank executable");
		}
		if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
			throw new IllegalArgumentException("Encoder timeout must be positive");
		}
		long budget = timeoutNanos(timeout);
		long started = System.nanoTime();
		Diagnostics diagnostics = new Diagnostics();
		AtomicReference<IOException> readerError = new AtomicReference<>();
		Set<ProcessHandle> descendants = new LinkedHashSet<>();
		List<Thread> readers = new ArrayList<>(2);
		Process process = null;
		IOException failure = null;
		boolean interrupted = false;
		try {
			if (Thread.currentThread().isInterrupted()) {
				throw new InterruptedException("Interrupted before encoder launch");
			}
			process = Objects.requireNonNull(launcher.start(arguments), "Launcher returned null process");
			collectDescendants(process, descendants);
			readers.add(startReader(process.getInputStream(), "stdout", diagnostics, readerError));
			readers.add(startReader(process.getErrorStream(), "stderr", diagnostics, readerError));
			process.getOutputStream().close();
			while (true) {
				collectDescendants(process, descendants);
				checkReaderError(readerError);
				long remaining = remaining(started, budget);
				if (remaining <= 0) {
					throw new IOException("Timed out after " + timeout);
				}
				if (process.waitFor(Math.min(remaining, POLL_NANOS), TimeUnit.NANOSECONDS)) {
					break;
				}
			}
			long drainingStarted = System.nanoTime();
			for (Thread reader : readers) {
				long remaining = Math.min(remaining(drainingStarted, CLEANUP_NANOS), remaining(started, budget));
				if (remaining <= 0 || !reader.join(Duration.ofNanos(remaining))) {
					throw new IOException("Encoder output readers did not finish within the drain budget");
				}
			}
			checkReaderError(readerError);
			int exit = process.exitValue();
			if (exit != 0) {
				throw new IOException("Exited with status " + exit);
			}
		} catch (InterruptedException e) {
			interrupted = true;
			failure = new IOException("Interrupted while running encoder", e);
		} catch (IOException e) {
			failure = e;
		} catch (RuntimeException e) {
			failure = new IOException("Encoder startup or process handling failed", e);
		} finally {
			if (process != null && failure != null) {
				interrupted |= cleanup(process, descendants, readers, failure);
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		if (failure != null) {
			throw new IOException("Encoder " + arguments.getFirst() + " failed: " + failure.getMessage()
					+ "\nDiagnostic tail (stdout/stderr, last 32 KiB):\n" + diagnostics.tail(), failure);
		}
		return diagnostics.warnings();
	}

	private static long timeoutNanos(Duration timeout) {
		if (timeout == null) {
			return Long.MAX_VALUE;
		}
		try {
			return timeout.toNanos();
		} catch (ArithmeticException e) {
			return Long.MAX_VALUE;
		}
	}

	private static long remaining(long started, long budget) {
		return budget - (System.nanoTime() - started);
	}

	private static void checkReaderError(AtomicReference<IOException> error) throws IOException {
		IOException failure = error.get();
		if (failure != null) {
			throw new IOException("Cannot read encoder output", failure);
		}
	}

	private static Thread startReader(InputStream stream, String channel, Diagnostics diagnostics,
			AtomicReference<IOException> error) {
		return Thread.ofVirtual().name("brts-encoder-" + channel).start(() -> {
			LineCapture line = new LineCapture(channel, diagnostics);
			try (InputStream input = stream) {
				byte[] chunk = new byte[2048];
				int length;
				while ((length = input.read(chunk)) != -1) {
					diagnostics.append(chunk, length);
					for (int i = 0; i < length; i++) {
						line.accept(chunk[i]);
					}
				}
				line.finish();
			} catch (IOException | RuntimeException e) {
				error.compareAndSet(null, new IOException("Failed reading " + channel, e));
			}
		});
	}

	private static void collectDescendants(Process process, Set<ProcessHandle> descendants) {
		// Injected test processes need not implement ProcessHandle support.
		try (var children = process.descendants()) {
			children.forEach(descendants::add);
		} catch (UnsupportedOperationException ignored) {
			// Direct process termination still works.
		}
	}

	private static boolean cleanup(Process process, Set<ProcessHandle> descendants, List<Thread> readers,
			IOException failure) {
		boolean interrupted = Thread.interrupted();
		long started = System.nanoTime();
		try {
			collectDescendants(process, descendants);
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
		for (ProcessHandle child : descendants) {
			try {
				child.destroyForcibly();
			} catch (RuntimeException e) {
				failure.addSuppressed(e);
			}
		}
		try {
			process.destroyForcibly();
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
		// Close asynchronously: an injected/uncooperative stream must not block cleanup indefinitely.
		List<Thread> finishing = new ArrayList<>(readers);
		try {
			finishing.add(closeAsync(process.getOutputStream(), failure));
			finishing.add(closeAsync(process.getInputStream(), failure));
			finishing.add(closeAsync(process.getErrorStream(), failure));
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
		for (Thread reader : readers) {
			reader.interrupt();
		}
		long left;
		while ((left = remaining(started, CLEANUP_NANOS)) > 0) {
			try {
				if (process.waitFor(Math.min(POLL_NANOS, left), TimeUnit.NANOSECONDS)) {
					break; // waitFor reaps the directly launched process.
				}
			} catch (InterruptedException e) {
				interrupted = true;
			} catch (RuntimeException e) {
				failure.addSuppressed(e);
				break;
			}
		}
		for (Thread thread : finishing) {
			while (thread.isAlive() && (left = remaining(started, CLEANUP_NANOS)) > 0) {
				try {
					thread.join(Duration.ofNanos(left));
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
		}
		try {
			if (process.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)
					|| finishing.stream().anyMatch(Thread::isAlive)) {
				failure.addSuppressed(new IOException("Encoder cleanup exceeded its two-second budget"));
			}
		} catch (RuntimeException e) {
			failure.addSuppressed(e);
		}
		return interrupted;
	}

	private static Thread closeAsync(Closeable stream, IOException failure) {
		return Thread.ofVirtual().name("brts-encoder-close").start(() -> {
			try {
				stream.close();
			} catch (IOException | RuntimeException e) {
				failure.addSuppressed(e);
			}
		});
	}

	private static final class Diagnostics {
		private final byte[] tail = new byte[TAIL_BYTES];
		private final List<String> warnings = new ArrayList<>();
		private int next;
		private int size;

		synchronized void append(byte[] bytes, int length) {
			for (int i = 0; i < length; i++) {
				tail[next] = bytes[i];
				next = (next + 1) % tail.length;
				size = Math.min(size + 1, tail.length);
			}
		}

		synchronized String tail() {
			byte[] ordered = new byte[size];
			int first = (next - size + tail.length) % tail.length;
			for (int i = 0; i < size; i++) {
				ordered[i] = tail[(first + i) % tail.length];
			}
			return new String(ordered, StandardCharsets.UTF_8);
		}

		synchronized void warn(String channel, byte[] line, int length, boolean truncated) {
			if (warnings.size() < MAX_WARNINGS) {
				warnings.add("Encoder reported ignored/unsupported/fallback/downgraded/disabled settings or "
						+ "VBV underflow; output is best-effort, not certified Blu-ray/UHD Blu-ray compliant. "
						+ channel + ": " + new String(line, 0, length, StandardCharsets.UTF_8)
						+ (truncated ? " [line truncated]" : ""));
			}
		}

		synchronized List<String> warnings() {
			return List.copyOf(warnings);
		}
	}

	private static final class LineCapture {
		private final String channel;
		private final Diagnostics diagnostics;
		private final byte[] excerpt = new byte[LINE_BYTES];
		private final StringBuilder window = new StringBuilder(64);
		private int length;
		private boolean truncated;
		private boolean changed;
		private boolean vbv;
		private boolean underflow;

		LineCapture(String channel, Diagnostics diagnostics) {
			this.channel = channel;
			this.diagnostics = diagnostics;
		}

		void accept(byte value) {
			int unsigned = value & 0xff;
			if (unsigned == '\n' || unsigned == '\r') {
				finish();
				return;
			}
			if (length < excerpt.length) {
				excerpt[length++] = value;
			} else {
				truncated = true;
			}
			// ASCII encoder keywords are scanned over the entire line, even beyond the bounded excerpt.
			window.append((char) (unsigned >= 'A' && unsigned <= 'Z' ? unsigned + 32 : unsigned));
			if (window.length() > 64) {
				window.deleteCharAt(0);
			}
			changed |= window.indexOf("ignor") >= 0 || window.indexOf("unsupport") >= 0
					|| window.indexOf("fallback") >= 0 || window.indexOf("fall back") >= 0
					|| window.indexOf("downgrad") >= 0 || window.indexOf("disabl") >= 0
					|| window.indexOf("error parsing option") >= 0 || window.indexOf("unknown option") >= 0;
			vbv |= window.indexOf("vbv") >= 0;
			underflow |= window.indexOf("underflow") >= 0;
		}

		void finish() {
			if (changed || (vbv && underflow)) {
				diagnostics.warn(channel, excerpt, length, truncated);
			}
			length = 0;
			truncated = false;
			changed = false;
			vbv = false;
			underflow = false;
			window.setLength(0);
		}
	}
}
