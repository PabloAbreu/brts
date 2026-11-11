/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/low/BatchRunCli.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.brts.cli.BaseOptions;
import org.brts.cli.BrtsMain;
import org.brts.cli.FeatureRunner;
import org.brts.cli.LevelDispatcher;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.validation.DescriptorValidator;
import org.brts.common.validation.ExistingFile;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import org.kohsuke.args4j.Option;

import lombok.extern.slf4j.Slf4j;

/**
 * CLI meta-feature running an ordered list of BRTS commands read from a JSON descriptor.
 * <p>
 * Preflight steps run on every invocation before ordinary steps. Execution stops at the first failing step. Progress is
 * persisted before each ordinary step so that, once the problem is fixed, {@code --resume} restarts from the failed
 * step after rerunning preflight. Preflight failures leave saved progress unchanged.
 */
public class BatchRunCli {

	public static class RunOptions extends BaseOptions {

		@ExistingFile
		@Option(name = "--descriptor", required = true, usage = "Path to the batch-run JSON descriptor (ordered list of BRTS command invocations)")
		Path descriptor;

		@Option(name = "--resume", usage = "Resume a previously failed run from the failed step")
		boolean resume;

	}

	/** Persisted state guarding completed steps and preflight definitions; legacy states omit preflight. */
	record Progress(String descriptorPath, int nextStep, List<BatchStep> completedSteps,
			List<BatchStep> preflightSteps) {
	}

	@Slf4j
	public static class Run extends FeatureRunner<RunOptions> {

		private final Supplier<Map<String, LevelDispatcher>> levels;
		private final Path stateDir;

		public Run() {
			this(BrtsMain::getLevels, Path.of(System.getProperty("java.io.tmpdir"), "brts-batch-run"));
		}

		Run(Supplier<Map<String, LevelDispatcher>> levels, Path stateDir) {
			this.levels = levels;
			this.stateDir = stateDir;
		}

		@Override
		public String getCommandName() {
			return "batch-run";
		}

		@Override
		public String getDescription() {
			return "Run an ordered list of BRTS commands from a JSON descriptor (fail-fast, resumable)";
		}

		@Override
		protected void execute(RunOptions opts) throws Exception {
			Path descriptorPath = opts.descriptor.toAbsolutePath().normalize();
			BatchRunDescriptor descriptor = JsonMapperFactory.get().readValue(descriptorPath.toFile(),
					BatchRunDescriptor.class);
			DescriptorValidator.validateOrThrow(descriptor, "batch-run descriptor " + descriptorPath);
			Map<String, LevelDispatcher> dispatchers = levels.get();
			checkCommandsExist(descriptor, dispatchers, descriptorPath);

			List<BatchStep> steps = descriptor.getSteps();
			List<BatchStep> preflightSteps = descriptor.getPreflightSteps() == null ? List.of()
					: descriptor.getPreflightSteps();
			Path stateFile = stateFile(descriptorPath);
			int start = 0;
			if (opts.resume) {
				start = resumePoint(stateFile, descriptorPath, steps, preflightSteps);
				log.info("Resuming {} from step {}/{}", descriptorPath, start + 1, steps.size());
			}

			// Do not modify existing progress until every preflight step has succeeded.
			for (int i = 0; i < preflightSteps.size(); i++) {
				BatchStep step = preflightSteps.get(i);
				try {
					runStep(step, i, preflightSteps.size(), dispatchers);
				} catch (Exception e) {
					throw new IllegalStateException(String.format("Preflight step %d/%d (%s) of %s failed: %s", i + 1,
							preflightSteps.size(), step.describe(), descriptorPath, e.getMessage()), e);
				}
			}
			if (!opts.resume && Files.deleteIfExists(stateFile)) {
				log.info("Discarded previous progress of {}", descriptorPath);
			}

			for (int i = start; i < steps.size(); i++) {
				BatchStep step = steps.get(i);
				// saved before running: some commands System.exit() on failure
				saveProgress(stateFile,
						new Progress(descriptorPath.toString(), i, steps.subList(0, i), preflightSteps));
				try {
					runStep(step, i, steps.size(), dispatchers);
				} catch (Exception e) {
					throw new IllegalStateException(String.format(
							"Step %d/%d (%s) of %s failed: %s%nFix the problem and re-run with --resume to continue from this step.",
							i + 1, steps.size(), step.describe(), descriptorPath, e.getMessage()), e);
				}
			}
			Files.deleteIfExists(stateFile);
			System.out.println("Batch run complete: " + steps.size() + " step(s) from " + descriptorPath);
		}

		private static void checkCommandsExist(BatchRunDescriptor descriptor, Map<String, LevelDispatcher> dispatchers,
				Path descriptorPath) {
			if (descriptor.getPreflightSteps() != null) {
				checkCommandsExist(descriptor.getPreflightSteps(), dispatchers, descriptorPath, "Preflight step");
			}
			checkCommandsExist(descriptor.getSteps(), dispatchers, descriptorPath, "Step");
		}

		private static void checkCommandsExist(List<BatchStep> steps, Map<String, LevelDispatcher> dispatchers,
				Path descriptorPath, String label) {
			for (int i = 0; i < steps.size(); i++) {
				BatchStep step = steps.get(i);
				if (!step.isInvocation()) {
					continue;
				}
				LevelDispatcher dispatcher = dispatchers.get(step.getLevel().toLowerCase());
				if (dispatcher == null) {
					throw new IllegalArgumentException(
							String.format("%s %d of %s: unknown level '%s' (expected one of %s)", label, i + 1,
									descriptorPath, step.getLevel(), dispatchers.keySet()));
				}
				boolean known = dispatcher.getRunners().stream()
						.anyMatch(r -> r.getCommandName().equalsIgnoreCase(step.getCommand()));
				if (!known) {
					throw new IllegalArgumentException(String.format("%s %d of %s: unknown %s command '%s'", label,
							i + 1, descriptorPath, step.getLevel(), step.getCommand()));
				}
			}
		}

		private static void runStep(BatchStep step, int index, int total, Map<String, LevelDispatcher> dispatchers)
				throws Exception {
			if (step.getComment() != null) {
				log.info("# {}", step.getComment());
			}
			if (step.getMessage() != null) {
				System.out.println(step.getMessage());
			}
			if (step.isInvocation()) {
				log.info("Step {}/{}: {}", index + 1, total, step.describe());
				dispatchers.get(step.getLevel().toLowerCase()).dispatch(step.toCommandTokens().toArray(new String[0]));
			}
		}

		private static int resumePoint(Path stateFile, Path descriptorPath, List<BatchStep> steps,
				List<BatchStep> preflightSteps) throws IOException {
			if (!Files.isRegularFile(stateFile)) {
				throw new IllegalArgumentException("No saved progress for " + descriptorPath
						+ "; run it without --resume (state file: " + stateFile + ")");
			}
			Progress progress = JsonMapperFactory.get().readValue(stateFile.toFile(), Progress.class);
			int next = progress.nextStep();
			List<BatchStep> completed = progress.completedSteps() == null ? List.of() : progress.completedSteps();
			if (next < 0 || next != completed.size() || next > steps.size()
					|| !steps.subList(0, next).equals(completed)) {
				throw new IllegalArgumentException("Already completed steps of " + descriptorPath
						+ " were modified since the failed run; run it again without --resume");
			}
			List<BatchStep> savedPreflight = progress.preflightSteps() == null ? List.of() : progress.preflightSteps();
			if (!preflightSteps.equals(savedPreflight)) {
				throw new IllegalArgumentException("Preflight steps of " + descriptorPath
						+ " were modified since the failed run; run it again without --resume");
			}
			return next;
		}

		private static void saveProgress(Path stateFile, Progress progress) throws IOException {
			Files.createDirectories(stateFile.getParent());
			JsonMapperFactory.get().writeValue(stateFile.toFile(), progress);
		}

		Path stateFile(Path descriptorPath) {
			try {
				byte[] hash = MessageDigest.getInstance("SHA-256")
						.digest(descriptorPath.toString().getBytes(StandardCharsets.UTF_8));
				return stateDir.resolve(HexFormat.of().formatHex(hash) + ".json");
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException("SHA-256 not available", e);
			}
		}

	}

}
