/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/utils/video/EncoderProcessRunnerTest.java' is part of BRTS.
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

import static org.brts.lowlevel.utils.video.VideoTestSupport.completed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EncoderProcessRunnerTest {

	@Test
	void successUsesImmutableTokenizedArgumentsAndDrainsBothChannels() throws Exception {
		Process process = completed(0, "unsupported stdout setting\n", "VBV underflow on stderr\n");
		AtomicReference<List<String>> launched = new AtomicReference<>();
		var runner = new EncoderProcessRunner(command -> {
			launched.set(command);
			return process;
		});
		List<String> command = new ArrayList<>(List.of("fake encoder", "-i", "a file;not a shell.264"));
		List<String> warnings = runner.run(command, Duration.ofSeconds(5));
		assertEquals(command, launched.get());
		command.clear();
		assertEquals(3, launched.get().size());
		assertThrows(UnsupportedOperationException.class, () -> launched.get().clear());
		assertEquals(2, warnings.size());
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("stdout:")));
		assertTrue(warnings.stream().anyMatch(warning -> warning.contains("stderr:")));
		assertThrows(UnsupportedOperationException.class, () -> warnings.clear());
		verify(process).exitValue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "setting IGNORED", "unsupported option", "fall back to default", "fallback active",
			"preset downgraded", "uhd-bd disabled", "VBV buffer underflow", "bluray disabled" })
	void conservativeKeywordDetectionDoesNotRequireEncoderWarningPrefix(String text) throws Exception {
		Process process = completed(0, "", text);
		List<String> warnings = new EncoderProcessRunner(command -> process).run(List.of("fake"), null);
		assertEquals(1, warnings.size());
		assertTrue(warnings.getFirst().contains(text));
		assertTrue(warnings.getFirst().contains("best-effort"));
	}

	@Test
	void ordinaryInformationalOutputDoesNotCreateFalseVbvWarnings() throws Exception {
		Process process = completed(0, "encoded 100 frames\nVBV buffer configured\nunderflow alone\n", "");
		assertTrue(new EncoderProcessRunner(command -> process).run(List.of("fake"), null).isEmpty());
	}

	@Test
	void warningsAreCappedAndKeywordBeyondExcerptIsStillDetected() throws Exception {
		String huge = "x".repeat(10000) + " unsupported beyond excerpt\n";
		StringBuilder output = new StringBuilder(huge);
		for (int i = 0; i < 150; i++) {
			output.append("fallback setting ").append(i).append('\n');
		}
		Process process = completed(0, output.toString(), "");
		List<String> warnings = new EncoderProcessRunner(command -> process).run(List.of("fake"),
				Duration.ofSeconds(5));
		assertEquals(100, warnings.size());
		assertTrue(warnings.getFirst().contains("[line truncated]"));
		assertFalse(warnings.getFirst().contains("unsupported beyond excerpt"));
		assertTrue(warnings.stream().allMatch(warning -> warning.length() < 4500));
	}

	@Test
	void failureIncludesOnlyBoundedTailAndKeepsNewestDiagnostics() throws Exception {
		Process process = completed(12, "old-start\n" + "x".repeat(100000) + "\nnewest-end\n", "");
		IOException failure = assertThrows(IOException.class,
				() -> new EncoderProcessRunner(command -> process).run(List.of("fake"), Duration.ofSeconds(5)));
		assertTrue(failure.getMessage().contains("status 12"));
		assertTrue(failure.getMessage().contains("newest-end"));
		assertFalse(failure.getMessage().contains("old-start"));
		String tail = failure.getMessage().substring(failure.getMessage().indexOf("KiB):\n") + "KiB):\n".length());
		assertEquals(32768, tail.length());
		verify(process).destroyForcibly();
	}

	@Test
	void timeoutTerminatesAndReapsFakeProcessWithinBoundedTime() throws Exception {
		Process process = completed(0, "", "");
		var runner = new EncoderProcessRunner(command -> process);
		IOException failure = assertTimeoutPreemptively(Duration.ofSeconds(5),
				() -> assertThrows(IOException.class, () -> runner.run(List.of("fake"), Duration.ofNanos(1))));
		assertTrue(failure.getMessage().contains("Timed out"));
		verify(process).destroyForcibly();
		verify(process).waitFor(anyLong(), any(TimeUnit.class));
	}

	@Test
	void interruptedWaitRestoresFlagAndTerminatesProcess() throws Exception {
		Process process = completed(0, "", "");
		when(process.waitFor(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("test interruption"))
				.thenReturn(true);
		try {
			IOException failure = assertThrows(IOException.class,
					() -> new EncoderProcessRunner(command -> process).run(List.of("fake"), null));
			assertTrue(failure.getMessage().contains("Interrupted"));
			assertTrue(Thread.currentThread().isInterrupted());
			verify(process).destroyForcibly();
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void alreadyInterruptedThreadDoesNotLaunchAndFlagRemainsSet() {
		AtomicInteger launches = new AtomicInteger();
		var runner = new EncoderProcessRunner(command -> {
			launches.incrementAndGet();
			throw new AssertionError("Interrupted invocation must not launch");
		});
		try {
			Thread.currentThread().interrupt();
			assertThrows(IOException.class, () -> runner.run(List.of("fake"), null));
			assertTrue(Thread.currentThread().isInterrupted());
			assertEquals(0, launches.get());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void launcherFailureAndNullProcessAreContextualIoFailures() {
		var failed = new EncoderProcessRunner(command -> {
			throw new IOException("cannot start fake executable");
		});
		IOException failure = assertThrows(IOException.class, () -> failed.run(List.of("fake-binary"), null));
		assertTrue(failure.getMessage().contains("fake-binary"));
		assertTrue(failure.getMessage().contains("cannot start"));
		var nullProcess = new EncoderProcessRunner(command -> null);
		assertThrows(IOException.class, () -> nullProcess.run(List.of("fake-binary"), null));
	}

	@Test
	void readerIoFailureIsPropagatedAndProcessIsDestroyed() throws Exception {
		Process process = completed(0, "", "");
		when(process.getInputStream()).thenReturn(new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("broken pipe fixture");
			}
		});
		IOException failure = assertThrows(IOException.class,
				() -> new EncoderProcessRunner(command -> process).run(List.of("fake"), Duration.ofSeconds(5)));
		assertTrue(failure.getMessage().contains("Cannot read encoder output"));
		verify(process).destroyForcibly();
	}

	@Test
	void hugeTimeoutDoesNotOverflowIntoImmediateFailure() throws Exception {
		Process process = completed(0, "", "");
		assertTrue(new EncoderProcessRunner(command -> process).run(List.of("fake"), Duration.ofSeconds(Long.MAX_VALUE))
				.isEmpty());
	}

	@Test
	void invalidCommandAndTimeoutFailBeforeLaunch() {
		var runner = new EncoderProcessRunner(command -> {
			throw new AssertionError("Invalid invocation must not launch");
		});
		assertThrows(IllegalArgumentException.class, () -> runner.run(List.of(), null));
		assertThrows(IllegalArgumentException.class, () -> runner.run(List.of(" "), null));
		assertThrows(IllegalArgumentException.class, () -> runner.run(List.of("fake"), Duration.ZERO));
		assertThrows(IllegalArgumentException.class, () -> runner.run(List.of("fake"), Duration.ofMillis(-1)));
	}
}
