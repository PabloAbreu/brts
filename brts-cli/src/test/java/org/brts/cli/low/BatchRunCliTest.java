/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/low/BatchRunCliTest.java' is part of BRTS.
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

package org.brts.cli.low;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.cli.LevelDispatcher;
import org.brts.cli.LowLevelDispatcher;
import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.batch.BatchArgument;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.args4j.Option;

class BatchRunCliTest {

	static final List<String> calls = new ArrayList<>();
	static boolean failOnBoom;

	public static class RecordOptions extends BaseOptions {
		@Option(name = "--value")
		public String value;

		@Option(name = "--flag")
		public boolean flag;
	}

	public static class RecordRunner extends FeatureRunner<RecordOptions> {
		@Override
		public String getCommandName() {
			return "record";
		}

		@Override
		public String getDescription() {
			return "records its arguments";
		}

		@Override
		protected void execute(RecordOptions opts) {
			if (failOnBoom && "boom".equals(opts.value)) {
				throw new IllegalStateException("boom failed");
			}
			calls.add(opts.value + (opts.flag ? "+flag" : ""));
		}
	}

	@TempDir
	Path tempDir;

	private BatchRunCli.Run runner;

	@BeforeEach
	void setUp() {
		calls.clear();
		failOnBoom = false;
		LevelDispatcher fake = new LevelDispatcher("low").register(new RecordRunner());
		runner = new BatchRunCli.Run(() -> Map.of("low", fake), tempDir.resolve("state"));
	}

	private static BatchStep record(String value) {
		return BatchStep.invocation("low", "record", List.of(new BatchArgument("--value", value)));
	}

	private Path writeDescriptor(BatchStep... steps) throws Exception {
		BatchRunDescriptor descriptor = new BatchRunDescriptor();
		descriptor.getSteps().addAll(List.of(steps));
		Path path = tempDir.resolve("batch.json");
		JsonMapperFactory.get().writeValue(path.toFile(), descriptor);
		return path;
	}

	private void run(Path descriptor, String... extra) throws Exception {
		List<String> args = new ArrayList<>(List.of("--descriptor", descriptor.toString()));
		args.addAll(List.of(extra));
		runner.run(args.toArray(new String[0]));
	}

	private void setPreflight(Path path, BatchStep... steps) throws Exception {
		BatchRunDescriptor descriptor = JsonMapperFactory.get().readValue(path.toFile(), BatchRunDescriptor.class);
		descriptor.setPreflightSteps(List.of(steps));
		JsonMapperFactory.get().writeValue(path.toFile(), descriptor);
	}

	@Test
	void preflightRunsBeforeOrdinaryStepsOnEveryFreshInvocation() throws Exception {
		Path descriptor = writeDescriptor(record("ordinary"));
		setPreflight(descriptor, BatchStep.comment("checking"), record("preflight"), BatchStep.message("checked"));

		run(descriptor);
		run(descriptor);

		assertThat(calls).containsExactly("preflight", "ordinary", "preflight", "ordinary");
		assertThat(runner.stateFile(descriptor)).doesNotExist();
	}

	@Test
	void preflightRunsOnEveryResumeWithoutRepeatingCompletedOrdinarySteps() throws Exception {
		Path descriptor = writeDescriptor(record("a"), record("boom"));
		setPreflight(descriptor, record("preflight"));
		failOnBoom = true;

		assertThatThrownBy(() -> run(descriptor)).hasMessageContaining("Step 2/2");
		BatchRunCli.Progress progress = JsonMapperFactory.get().readValue(runner.stateFile(descriptor).toFile(),
				BatchRunCli.Progress.class);
		assertThat(progress.nextStep()).isEqualTo(1);
		assertThat(progress.completedSteps()).containsExactly(record("a"));
		assertThat(progress.preflightSteps()).containsExactly(record("preflight"));
		assertThatThrownBy(() -> run(descriptor, "--resume")).hasMessageContaining("Step 2/2");
		failOnBoom = false;
		run(descriptor, "--resume");

		assertThat(calls).containsExactly("preflight", "a", "preflight", "preflight", "boom");
		assertThat(runner.stateFile(descriptor)).doesNotExist();
	}

	@Test
	void failedInitialPreflightDoesNotCreateProgressOrRunOrdinarySteps() throws Exception {
		Path descriptor = writeDescriptor(record("ordinary"));
		setPreflight(descriptor, record("boom"), record("later"));
		failOnBoom = true;

		assertThatThrownBy(() -> run(descriptor)).hasMessageContaining("Preflight step 1/2");

		assertThat(calls).isEmpty();
		assertThat(runner.stateFile(descriptor)).doesNotExist();
	}

	@Test
	void failedPreflightPreservesExistingProgressOnResumeAndFreshRun() throws Exception {
		Path descriptor = writeDescriptor(record("a"), record("boom"));
		setPreflight(descriptor, record("boom"));
		Path stateFile = runner.stateFile(descriptor);
		Files.createDirectories(stateFile.getParent());
		JsonMapperFactory.get().writeValue(stateFile.toFile(),
				new BatchRunCli.Progress(descriptor.toString(), 1, List.of(record("a")), List.of(record("boom"))));
		String saved = Files.readString(stateFile);
		failOnBoom = true;

		assertThatThrownBy(() -> run(descriptor, "--resume")).hasMessageContaining("Preflight step 1/1");
		assertThat(Files.readString(stateFile)).isEqualTo(saved);
		assertThatThrownBy(() -> run(descriptor)).hasMessageContaining("Preflight step 1/1");
		assertThat(Files.readString(stateFile)).isEqualTo(saved);
		assertThat(calls).isEmpty();

		failOnBoom = false;
		run(descriptor, "--resume");
		assertThat(calls).containsExactly("boom", "boom");
	}

	@Test
	void resumeRejectsChangedRemovedOrAddedPreflightBeforeExecutingAnything() throws Exception {
		Path descriptor = writeDescriptor(record("a"), record("boom"));
		setPreflight(descriptor, record("original"));
		failOnBoom = true;
		assertThatThrownBy(() -> run(descriptor));
		calls.clear();
		Path stateFile = runner.stateFile(descriptor);
		String saved = Files.readString(stateFile);

		for (List<BatchStep> preflight : List.of(List.of(record("changed")), List.<BatchStep>of(),
				List.of(record("original"), record("added")))) {
			setPreflight(descriptor, preflight.toArray(new BatchStep[0]));
			assertThatThrownBy(() -> run(descriptor, "--resume")).hasMessageContaining("Preflight steps")
					.hasMessageContaining("were modified");
			assertThat(Files.readString(stateFile)).isEqualTo(saved);
		}
		assertThat(calls).isEmpty();
	}

	@Test
	void legacyDescriptorAndProgressWithoutPreflightCanResume() throws Exception {
		Path descriptor = writeDescriptor(record("a"), record("b"));
		JsonMapperFactory.get().writeValue(descriptor.toFile(), Map.of("steps", List.of(record("a"), record("b"))));
		Path stateFile = runner.stateFile(descriptor);
		Files.createDirectories(stateFile.getParent());
		JsonMapperFactory.get().writeValue(stateFile.toFile(),
				Map.of("descriptorPath", descriptor.toString(), "nextStep", 1, "completedSteps", List.of(record("a"))));

		setPreflight(descriptor, record("added"));
		assertThatThrownBy(() -> run(descriptor, "--resume")).hasMessageContaining("Preflight steps");
		assertThat(calls).isEmpty();
		JsonMapperFactory.get().writeValue(descriptor.toFile(), Map.of("steps", List.of(record("a"), record("b"))));
		run(descriptor, "--resume");

		assertThat(calls).containsExactly("b");
		assertThat(stateFile).doesNotExist();
	}

	@Test
	void unknownPreflightLevelAndCommandAreRejectedBeforeExecution() throws Exception {
		Path descriptor = writeDescriptor(record("ordinary"));
		for (BatchStep invalid : List.of(BatchStep.invocation("unknown", "record", List.of()),
				BatchStep.invocation("low", "nope", List.of()))) {
			setPreflight(descriptor, record("preflight"), invalid);
			assertThatThrownBy(() -> run(descriptor)).hasMessageContaining("Preflight step 2")
					.hasMessageContaining("unknown");
		}
		assertThat(calls).isEmpty();
	}

	@Test
	void unknownOrdinaryCommandIsRejectedBeforePreflightRuns() throws Exception {
		Path descriptor = writeDescriptor(BatchStep.invocation("low", "nope", List.of()));
		setPreflight(descriptor, record("preflight"));

		assertThatThrownBy(() -> run(descriptor)).hasMessageContaining("unknown low command 'nope'");
		assertThat(calls).isEmpty();
	}

	@Test
	void commandIsRegistered() {
		assertThat(LowLevelDispatcher.getLevelDispatcher().getRunners())
				.anyMatch(r -> r.getCommandName().equals("batch-run"));
	}

	@Test
	void runsStepsInOrderAndSupportsValuelessFlags() throws Exception {
		BatchStep flagged = BatchStep.invocation("low", "record",
				List.of(new BatchArgument("--value", "b"), new BatchArgument("--flag", null)));
		Path descriptor = writeDescriptor(BatchStep.comment("start"), record("a"), flagged, BatchStep.message("done"));

		run(descriptor);

		assertThat(calls).containsExactly("a", "b+flag");
		assertThat(runner.stateFile(descriptor.toAbsolutePath().normalize())).doesNotExist();
	}

	@Test
	void failsFastThenResumesFromFailedStep() throws Exception {
		failOnBoom = true;
		Path descriptor = writeDescriptor(record("a"), record("boom"), record("c"));

		assertThatThrownBy(() -> run(descriptor)).hasMessageContaining("Step 2/3").hasMessageContaining("--resume");
		assertThat(calls).containsExactly("a");
		assertThat(runner.stateFile(descriptor.toAbsolutePath().normalize())).exists();

		failOnBoom = false;
		run(descriptor, "--resume");

		assertThat(calls).containsExactly("a", "boom", "c");
		assertThat(runner.stateFile(descriptor.toAbsolutePath().normalize())).doesNotExist();
	}

	@Test
	void resumeAllowsEditingFailedStepButNotCompletedOnes() throws Exception {
		failOnBoom = true;
		Path descriptor = writeDescriptor(record("a"), record("boom"));
		assertThatThrownBy(() -> run(descriptor));

		writeDescriptor(record("a"), record("fixed"));
		run(descriptor, "--resume");
		assertThat(calls).containsExactly("a", "fixed");
	}

	@Test
	void resumeRejectsModifiedCompletedSteps() throws Exception {
		failOnBoom = true;
		Path descriptor = writeDescriptor(record("a"), record("boom"));
		assertThatThrownBy(() -> run(descriptor));

		writeDescriptor(record("changed"), record("boom"));
		assertThatThrownBy(() -> run(descriptor, "--resume")).hasMessageContaining("were modified");
	}

	@Test
	void resumeWithoutSavedProgressFails() throws Exception {
		Path descriptor = writeDescriptor(record("a"));
		assertThatThrownBy(() -> run(descriptor, "--resume")).hasMessageContaining("No saved progress");
		assertThat(calls).isEmpty();
	}

	@Test
	void unknownCommandIsRejectedBeforeAnyStepRuns() throws Exception {
		Path descriptor = writeDescriptor(record("a"), BatchStep.invocation("low", "nope", List.of()));
		assertThatThrownBy(() -> run(descriptor)).hasMessageContaining("unknown low command 'nope'");
		assertThat(calls).isEmpty();
	}

}
