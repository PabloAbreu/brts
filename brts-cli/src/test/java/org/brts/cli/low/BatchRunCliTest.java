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
