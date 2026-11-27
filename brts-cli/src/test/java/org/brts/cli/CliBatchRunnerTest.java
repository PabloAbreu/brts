/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/test/java/org/brts/cli/CliBatchRunnerTest.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.brts.common.orchestration.BrtsBatch;
import org.brts.common.orchestration.BrtsBatchHooks;
import org.brts.common.orchestration.BrtsBatchResult;
import org.brts.common.orchestration.BrtsInvocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.args4j.Option;

class CliBatchRunnerTest {

	private static final List<String> calls = new ArrayList<>();
	private static boolean failSecond;

	public static class Options extends BaseOptions {
		@Option(name = "--value")
		public String value;
	}

	public static class Runner extends FeatureRunner<Options> {
		@Override
		public String getCommandName() {
			return "record";
		}

		@Override
		public String getDescription() {
			return "record test invocations";
		}

		@Override
		protected void execute(Options options) {
			if (failSecond && "second".equals(options.value)) {
				throw new IllegalStateException("expected failure");
			}
			calls.add(options.value);
		}
	}

	@BeforeEach
	void setUp() {
		calls.clear();
		failSecond = false;
	}

	private static BrtsInvocation invocation(String value) {
		return BrtsInvocation.builder("low", "record").argument("--value", value).build();
	}

	private CliBatchRunner runner() {
		return new CliBatchRunner(() -> Map.of("low", new LevelDispatcher("low").register(new Runner())));
	}

	@Test
	void executesProgrammaticBatchAndAllowsFailureContinuationHook() throws Exception {
		failSecond = true;
		BrtsBatch batch = BrtsBatch.builder().add(invocation("first")).add(invocation("second"))
				.add(invocation("third")).build();
		BrtsBatchResult result = runner().run(batch, new BrtsBatchHooks() {
			@Override
			public FailureAction onFailure(StepContext context, Exception failure) {
				return FailureAction.CONTINUE;
			}
		});

		assertThat(calls).containsExactly("first", "third");
		assertThat(result.completedSteps()).isEqualTo(2);
		assertThat(result.failures()).hasSize(1);
		assertThat(result.failures().getFirst().context().index()).isEqualTo(1);
	}

	@Test
	void hookCanWrapOrReplaceStepDispatch() throws Exception {
		BrtsBatch batch = BrtsBatch.builder().add(invocation("delegated")).build();
		List<String> hookCalls = new ArrayList<>();

		runner().run(batch, new BrtsBatchHooks() {
			@Override
			public void executeStep(StepContext context, StepExecutor executor) throws Exception {
				hookCalls.add("before");
				executor.execute(context.step().invocation());
				hookCalls.add("after");
			}
		});

		assertThat(hookCalls).containsExactly("before", "after");
		assertThat(calls).containsExactly("delegated");
	}
}
