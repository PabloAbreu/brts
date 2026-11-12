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
import org.brts.cli.FeatureRunner;
import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import org.brts.middle.analysis.DiscAnalyzer;
import org.brts.middle.analysis.DiscAnalyzer.Diagnostic;
import org.brts.middle.orchestration.launch.BrtsImmediateInvocator;
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
			BatchRunDescriptor batch = JsonMapperFactory.get().readValue(plan.descriptor().toFile(),
					BatchRunDescriptor.class);
			List<Diagnostic> failures = new ArrayList<>();
			for (int i = 0; i < batch.getSteps().size(); i++) {
				BatchStep step = batch.getSteps().get(i);
				if (!step.isInvocation()) {
					if (step.getMessage() != null) {
						System.out.println(step.getMessage());
					}
					continue;
				}
				try {
					executeParserStep(step);
				} catch (Exception e) {
					failures.add(new Diagnostic(step.describe(), "Step " + (i + 1) + "/" + batch.getSteps().size()
							+ " failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())));
					break;
				}
			}
			return failures;
		}

		private void executeParserStep(BatchStep step) {
			if (!"low".equalsIgnoreCase(step.getLevel())) {
				throw new IllegalArgumentException("Unsupported batch level: " + step.getLevel());
			}
			List<String> args = new ArrayList<>();
			args.add(step.getLevel());
			List<String> commandTokens = step.toCommandTokens();
			args.add(commandTokens.getFirst());
			args.add(BaseOptions.OPTION_NO_EXIT_ON_FAILURE);
			args.add(BaseOptions.OPTION_NO_BANNER);
			args.addAll(commandTokens.subList(1, commandTokens.size()));
			// Directly constructed runners have not passed through LevelDispatcher's injection.
			BrtsImmediateInvocator invocator = getInvocator() == null ? BrtsMain::main : getInvocator();
			invocator.invoke(args.toArray(String[]::new));
		}
	}
}
