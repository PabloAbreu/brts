/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/middle/DiscAnalyseCli.java' is part of BRTS.
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

package org.brts.cli.middle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.cli.BaseOptions;
import org.brts.cli.BrtsMain;
import org.brts.cli.CliBatchRunner;
import org.brts.cli.FeatureRunner;
import org.brts.common.orchestration.BrtsBatch;
import org.brts.common.orchestration.BrtsBatchHooks;
import org.brts.common.orchestration.BrtsBatchHooks.StepContext;
import org.brts.common.orchestration.BrtsBatchResult;
import org.brts.common.orchestration.BrtsImmediateInvocator;
import org.brts.common.orchestration.BrtsInvocation;
import org.brts.middle.analysis.DiscAnalyzer;
import org.brts.middle.analysis.DiscAnalyzer.Diagnostic;
import org.kohsuke.args4j.Option;

import lombok.RequiredArgsConstructor;

/** CLI for parsing and browsing supported structures of a Blu-ray disc. */
public class DiscAnalyseCli {

	static class Options extends BaseOptions {

		@Option(name = "--disc-root", required = true, usage = "Disc root containing the BDMV directory")
		Path discRoot;

		@Option(name = "--output", required = true, usage = "Empty directory for JSON and HTML analysis output")
		Path output;

		@Option(name = "--defer", usage = "Write the parser batch descriptor but do not execute it")
		boolean defer;
	}

	@RequiredArgsConstructor
	public static class Run extends FeatureRunner<Options> {

		private final DiscAnalyzer analyzer;

		public Run() {
			this(new DiscAnalyzer());
		}

		@Override
		public String getCommandName() {
			return "disc-analyse";
		}

		@Override
		public String getDescription() {
			return "Parse a Blu-ray disc structure and generate linked JSON/HTML analysis";
		}

		@Override
		protected Options createOptions() {
			return new Options();
		}

		@Override
		protected void validateOptions(Options opts) {
			super.validateOptions(opts);
			if (!Files.isDirectory(opts.discRoot)) {
				throw new IllegalArgumentException("Disc root is not a directory: " + opts.discRoot);
			}
		}

		@Override
		protected void execute(Options opts) throws Exception {
			DiscAnalyzer.Plan plan = analyzer.prepare(opts.discRoot, opts.output);
			if (opts.defer) {
				analyzer.render(plan, List.of(), true);
				System.out.println("Disc analysis prepared (not executed): " + plan.descriptor());
				return;
			}

			List<Diagnostic> failures = executeBatch(plan);
			analyzer.render(plan, failures, false);
			if (!failures.isEmpty()) {
				Diagnostic failure = failures.get(0);
				throw new IllegalStateException("Disc analysis stopped at " + failure.file() + ": " + failure.message()
						+ "; partial report: " + plan.outputRoot().resolve("index.html"));
			}
			System.out.println("Disc analysis complete: " + plan.outputRoot().resolve("index.html"));
		}

		private List<Diagnostic> executeBatch(DiscAnalyzer.Plan plan) throws Exception {
			BrtsBatch batch = CliBatchRunner.fromDescriptor(plan.batch());
			BrtsImmediateInvocator invocator = getInvocator() == null ? BrtsMain::main : getInvocator();
			BrtsBatchHooks hooks = new BrtsBatchHooks() {
				@Override
				public void executeStep(StepContext context, StepExecutor executor) {
					BrtsInvocation invocation = context.step().invocation();
					if (invocation == null) {
						return;
					}
					List<String> args = new ArrayList<>(invocation.toTokens());
					args.add(2, BaseOptions.OPTION_NO_EXIT_ON_FAILURE);
					args.add(3, BaseOptions.OPTION_NO_BANNER);
					invocator.invoke(args.toArray(String[]::new));
				}
			};
			BrtsBatchResult result = new CliBatchRunner().run(batch, hooks);
			List<Diagnostic> failures = new ArrayList<>();
			for (BrtsBatchResult.Failure failure : result.failures()) {
				StepContext context = failure.context();
				BrtsInvocation invocation = context.step().invocation();
				String file = invocation == null ? "batch step"
						: invocation.level() + " "
								+ String.join(" ", invocation.toTokens().subList(1, invocation.toTokens().size()));
				Exception cause = failure.cause();
				failures.add(new Diagnostic(file, "Step " + (context.index() + 1) + "/" + context.total() + " failed: "
						+ (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage())));
			}
			return failures;
		}
	}
}
