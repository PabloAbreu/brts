/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/NoExitOnFailureTest.java' is part of BRTS.
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

package org.brts.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.brts.cli.middle.DiscAnalyseCli;
import org.brts.common.validation.DescriptorValidationException;
import org.brts.common.validation.ExistingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

class NoExitOnFailureTest {

	private static final String NO_EXIT = BaseOptions.OPTION_NO_EXIT_ON_FAILURE;
	private static final String NO_BANNER = BaseOptions.OPTION_NO_BANNER;

	@TempDir
	Path tempDir;

	public static class TestOptions extends BaseOptions {
		@Option(name = "--value", required = true)
		String value;

		@ExistingFile
		@Option(name = "--existing")
		Path existing;
	}

	public static class TestRunner extends FeatureRunner<TestOptions> {
		private final List<String> calls = new ArrayList<>();
		private final List<Boolean> policies = new ArrayList<>();

		@Override
		public String getCommandName() {
			return "test";
		}

		@Override
		public String getDescription() {
			return "Records successful execution";
		}

		@Override
		protected void execute(TestOptions opts) {
			calls.add(opts.value);
			policies.add(opts.isNoExitOnFailure());
		}
	}

	@Test
	void parseFailureHonorsFlagEvenAfterInvalidArgument() {
		TestRunner runner = new TestRunner();
		assertThatThrownBy(() -> runner.run(new String[] { "--invalid", NO_EXIT }))
				.isInstanceOf(CmdLineException.class);
		assertThat(runner.calls).isEmpty();
	}

	@Test
	void validationFailureThrowsWithoutExecuting() {
		TestRunner runner = new TestRunner();
		assertThatThrownBy(() -> runner
				.run(new String[] { "--value", "ok", "--existing", tempDir.resolve("missing").toString(), NO_EXIT }))
				.isInstanceOf(DescriptorValidationException.class);
		assertThat(runner.calls).isEmpty();
	}

	@Test
	void successfulRunsDoNotRetainOptionState() throws Exception {
		TestRunner runner = new TestRunner();
		runner.run(new String[] { NO_EXIT, "--value", "first" });
		runner.run(new String[] { "--value", "second" });
		assertThat(runner.calls).containsExactly("first", "second");
		assertThat(runner.policies).containsExactly(true, false);
	}

	@Test
	void helpAcceptsInternalInvocationFlags() {
		TestRunner runner = new TestRunner();
		assertThatCode(() -> runner.run(new String[] { "--help", NO_EXIT, NO_BANNER })).doesNotThrowAnyException();
		assertThat(runner.calls).isEmpty();
	}

	@Test
	void unknownLevelAndCommandThrowThroughRootInvocator() {
		assertThatThrownBy(() -> BrtsMain.main(new String[] { "unknown", "test", NO_EXIT, NO_BANNER }))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown level");
		assertThatThrownBy(() -> BrtsMain.main(new String[] { "low", "unknown", NO_EXIT, NO_BANNER }))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown low command");
	}

	@Test
	void rootWrapsCheckedParseFailureWithCause() {
		assertThatThrownBy(() -> BrtsMain.main(new String[] { "low", "index-parse", "--invalid", NO_EXIT, NO_BANNER }))
				.isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(CmdLineException.class)
				.hasMessageContaining("--invalid");
	}

	@Test
	void rootPreservesRuntimeValidationFailure() {
		assertThatThrownBy(() -> BrtsMain.main(new String[] { "low", "batch-run", NO_EXIT, NO_BANNER, "--descriptor",
				tempDir.resolve("missing.json").toString() })).isInstanceOf(DescriptorValidationException.class);
	}

	@Test
	void discAnalysisForwardsTokensAndStopsOnInjectedFailure() throws Exception {
		Path disc = tempDir.resolve("disc with spaces");
		Path bdmv = Files.createDirectories(disc.resolve("BDMV"));
		Files.writeString(bdmv.resolve("index.bdmv"), "placeholder");
		Files.writeString(bdmv.resolve("MovieObject.bdmv"), "placeholder");
		Path output = tempDir.resolve("report with spaces");
		List<List<String>> calls = new ArrayList<>();
		FeatureRunner<?> runner = new DiscAnalyseCli.Run();
		runner.setInvocator(args -> {
			calls.add(List.of(args));
			throw new IllegalArgumentException("injected parser failure");
		});

		assertThatThrownBy(
				() -> runner.run(new String[] { "--disc-root", disc.toString(), "--output", output.toString() }))
				.hasMessageContaining("injected parser failure");
		assertThat(calls).containsExactly(List.of("low", "index-parse", NO_EXIT, NO_BANNER, "--input",
				bdmv.resolve("index.bdmv").toString(), "--output", output.resolve("BDMV/index.json").toString()));
		assertThat(Files.readString(output.resolve("index.html"))).contains("Step 1/2 failed");
	}

	@Test
	void deferredDiscAnalysisDoesNotInvokeCommands() throws Exception {
		Path disc = tempDir.resolve("disc");
		Path bdmv = Files.createDirectories(disc.resolve("BDMV"));
		Files.writeString(bdmv.resolve("index.bdmv"), "placeholder");
		FeatureRunner<?> runner = new DiscAnalyseCli.Run();
		runner.setInvocator(args -> {
			throw new AssertionError("Deferred analysis must not invoke commands");
		});
		Path output = tempDir.resolve("report");
		runner.run(new String[] { "--disc-root", disc.toString(), "--output", output.toString(), "--defer" });
		assertThat(output.resolve("batch-run.json")).exists();
		assertThat(output.resolve("index.html")).exists();
	}

	@ParameterizedTest
	@ValueSource(strings = { "unknown-level", "unknown-command", "parse", "validation", "execution" })
	void ordinaryInvocationPreservesExitCodes(String failure) throws Exception {
		List<String> args = switch (failure) {
		case "unknown-level" -> List.of("unknown", "test", NO_BANNER);
		case "unknown-command" -> List.of("low", "unknown", NO_BANNER);
		case "parse" -> List.of("low", "index-parse", NO_BANNER);
		case "validation" ->
			List.of("low", "batch-run", "--descriptor", tempDir.resolve("missing.json").toString(), NO_BANNER);
		default -> List.of("low", "index-parse", "--input", tempDir.resolve("missing.bdmv").toString(), NO_BANNER);
		};
		List<String> command = new ArrayList<>(
				List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
						System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
						BrtsMain.class.getName()));
		command.addAll(args);
		Process process = new ProcessBuilder(command).redirectErrorStream(true)
				.redirectOutput(tempDir.resolve("process.log").toFile()).start();
		try {
			assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
			assertThat(process.exitValue()).isEqualTo("execution".equals(failure) ? 2 : 1);
		} finally {
			process.destroyForcibly();
		}
	}
}
