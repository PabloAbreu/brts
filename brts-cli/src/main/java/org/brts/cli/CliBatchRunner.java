/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/CliBatchRunner.java' is part of BRTS.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.brts.common.orchestration.BrtsBatch;
import org.brts.common.orchestration.BrtsBatchHooks;
import org.brts.common.orchestration.BrtsBatchHooks.FailureAction;
import org.brts.common.orchestration.BrtsBatchHooks.StepContext;
import org.brts.common.orchestration.BrtsBatchResult;
import org.brts.common.orchestration.BrtsBatchRunner;
import org.brts.common.orchestration.BrtsInvocation;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CliBatchRunner implements BrtsBatchRunner {

	private static final BrtsBatchHooks NO_HOOKS = new BrtsBatchHooks() {
	};

	private final Supplier<Map<String, LevelDispatcher>> levels;

	public CliBatchRunner() {
		this(BrtsMain::getLevels);
	}

	public CliBatchRunner(Supplier<Map<String, LevelDispatcher>> levels) {
		this.levels = Objects.requireNonNull(levels, "levels");
	}

	@Override
	public BrtsBatchResult run(BrtsBatch batch, BrtsBatchHooks hooks) throws Exception {
		return run(batch, hooks, 0, false);
	}

	public BrtsBatchResult run(BrtsBatch batch, BrtsBatchHooks hooks, int firstOrdinaryStep) throws Exception {
		return run(batch, hooks, firstOrdinaryStep, firstOrdinaryStep > 0);
	}

	public BrtsBatchResult run(BrtsBatch batch, BrtsBatchHooks hooks, int firstOrdinaryStep, boolean resuming)
			throws Exception {
		Objects.requireNonNull(batch, "batch");
		BrtsBatchHooks effectiveHooks = hooks == null ? NO_HOOKS : hooks;
		firstOrdinaryStep = effectiveHooks.resumeFrom(batch, firstOrdinaryStep);
		if (firstOrdinaryStep < 0 || firstOrdinaryStep > batch.steps().size()) {
			throw new IllegalArgumentException("First ordinary step is outside the batch: " + firstOrdinaryStep);
		}
		Map<String, LevelDispatcher> dispatchers = levels.get();
		validate(batch, dispatchers);
		effectiveHooks.beforeBatch(batch);
		List<BrtsBatchResult.Failure> failures = new ArrayList<>();
		int completed = 0;
		if (!batch.preflightSteps().isEmpty()) {
			effectiveHooks.beforePreflight(batch.preflightSteps().size());
			PhaseResult preflight = runSteps(batch.preflightSteps(), true, 0, batch.preflightSteps().size(),
					dispatchers, effectiveHooks);
			completed += preflight.completed();
			failures.addAll(preflight.failures());
			if (preflight.stopped()) {
				BrtsBatchResult result = new BrtsBatchResult(completed, failures);
				effectiveHooks.afterBatch(result);
				return result;
			}
		}
		if (resuming) {
			effectiveHooks.beforeResume(firstOrdinaryStep, batch.steps().size());
		}
		PhaseResult ordinary = runSteps(batch.steps().subList(firstOrdinaryStep, batch.steps().size()), false,
				firstOrdinaryStep, batch.steps().size(), dispatchers, effectiveHooks);
		completed += ordinary.completed();
		failures.addAll(ordinary.failures());
		BrtsBatchResult result = new BrtsBatchResult(completed, failures);
		effectiveHooks.afterBatch(result);
		return result;
	}

	public void validate(BrtsBatch batch) {
		validate(batch, levels.get());
	}

	public void executeStep(BrtsBatch.Step step, int index, int total, boolean preflight, BrtsBatchHooks hooks)
			throws Exception {
		Map<String, LevelDispatcher> dispatchers = levels.get();
		executeStep(step, index, total, preflight, dispatchers, hooks == null ? NO_HOOKS : hooks);
	}

	public static BrtsBatch fromDescriptor(BatchRunDescriptor descriptor) {
		BrtsBatch.Builder builder = BrtsBatch.builder();
		if (descriptor.getPreflightSteps() != null) {
			for (BatchStep step : descriptor.getPreflightSteps()) {
				builder.addPreflightStep(convert(step));
			}
		}
		for (BatchStep step : descriptor.getSteps()) {
			builder.addStep(convert(step));
		}
		return builder.build();
	}

	public static BrtsBatch.Step fromStep(BatchStep step) {
		return convert(step);
	}

	private static BrtsBatch.Step convert(BatchStep step) {
		if (!step.isInvocation()) {
			return step.getMessage() != null ? BrtsBatch.Step.message(step.getMessage())
					: BrtsBatch.Step.comment(step.getComment());
		}
		BrtsInvocation.Builder invocation = BrtsInvocation.builder(step.getLevel(), step.getCommand());
		if (step.getArguments() != null) {
			step.getArguments().forEach(argument -> invocation.argument(argument.getFlag(), argument.getValue()));
		}
		return new BrtsBatch.Step(invocation.build(), step.getComment(), step.getMessage());
	}

	private static void validate(BrtsBatch batch, Map<String, LevelDispatcher> dispatchers) {
		validate(batch.preflightSteps(), dispatchers, "Preflight step");
		validate(batch.steps(), dispatchers, "Step");
	}

	private static void validate(List<BrtsBatch.Step> steps, Map<String, LevelDispatcher> dispatchers, String label) {
		for (int i = 0; i < steps.size(); i++) {
			BrtsInvocation invocation = steps.get(i).invocation();
			if (invocation == null) {
				continue;
			}
			LevelDispatcher dispatcher = dispatchers.get(invocation.level().toLowerCase(Locale.ROOT));
			if (dispatcher == null) {
				throw new IllegalArgumentException(String.format("%s %d: unknown level '%s' (expected one of %s)",
						label, i + 1, invocation.level(), dispatchers.keySet()));
			}
			boolean known = dispatcher.getRunners().stream()
					.anyMatch(runner -> runner.getCommandName().equalsIgnoreCase(invocation.command()));
			if (!known) {
				throw new IllegalArgumentException(String.format("%s %d: unknown %s command '%s'", label, i + 1,
						invocation.level(), invocation.command()));
			}
		}
	}

	private static PhaseResult runSteps(List<BrtsBatch.Step> steps, boolean preflight, int indexOffset, int total,
			Map<String, LevelDispatcher> dispatchers, BrtsBatchHooks hooks) throws Exception {
		List<BrtsBatchResult.Failure> failures = new ArrayList<>();
		int completed = 0;
		for (int i = 0; i < steps.size(); i++) {
			BrtsBatch.Step step = steps.get(i);
			StepContext context = new StepContext(step, i + indexOffset, total, preflight);
			try {
				executeStep(step, i + indexOffset, total, preflight, dispatchers, hooks);
				completed++;
			} catch (Exception failure) {
				failures.add(new BrtsBatchResult.Failure(context, failure));
				if (hooks.onFailure(context, failure) != FailureAction.CONTINUE) {
					return new PhaseResult(completed, failures, true);
				}
			}
		}
		return new PhaseResult(completed, failures, false);
	}

	private static void executeStep(BrtsBatch.Step step, int index, int total, boolean preflight,
			Map<String, LevelDispatcher> dispatchers, BrtsBatchHooks hooks) throws Exception {
		StepContext context = new StepContext(step, index, total, preflight);
		hooks.beforeStep(context);
		if (step.invocation() != null) {
			log.info("Step {}/{}: {}", index + 1, total, String.join(" ", step.invocation().toTokens()));
		}
		if (step.comment() != null) {
			log.info("# {}", step.comment());
		}
		if (step.message() != null) {
			System.out.println(step.message());
		}
		hooks.executeStep(context, invocation -> dispatch(invocation, dispatchers));
		hooks.afterStep(context);
	}

	private static void dispatch(BrtsInvocation invocation, Map<String, LevelDispatcher> dispatchers) throws Exception {
		LevelDispatcher dispatcher = dispatchers.get(invocation.level().toLowerCase(Locale.ROOT));
		List<String> tokens = new ArrayList<>();
		tokens.add(invocation.command());
		tokens.add(BaseOptions.OPTION_NO_EXIT_ON_FAILURE);
		tokens.add(BaseOptions.OPTION_NO_BANNER);
		for (BrtsInvocation.Argument argument : invocation.arguments()) {
			tokens.add(argument.flag());
			if (argument.value() != null) {
				tokens.add(argument.value());
			}
		}
		dispatcher.dispatch(tokens.toArray(String[]::new));
	}

	private record PhaseResult(int completed, List<BrtsBatchResult.Failure> failures, boolean stopped) {
	}
}
