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
import org.brts.cli.FeatureRunner;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsParser;
import org.brts.lowlevel.batch.BatchRunDescriptor;
import org.brts.lowlevel.batch.BatchStep;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.parser.IndexBdmvParser;
import org.brts.lowlevel.parser.MovieObjectsParser;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.brts.middle.analysis.DiscAnalyzer;
import org.brts.middle.analysis.DiscAnalyzer.Diagnostic;
import org.kohsuke.args4j.Option;

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

	public static class Run extends FeatureRunner<Options> {

		private final DiscAnalyzer analyzer;

		public Run() {
			this(new DiscAnalyzer());
		}

		Run(DiscAnalyzer analyzer) {
			this.analyzer = analyzer;
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

		private static List<Diagnostic> executeBatch(DiscAnalyzer.Plan plan) throws Exception {
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

		private static void executeParserStep(BatchStep step) throws Exception {
			if (!"low".equalsIgnoreCase(step.getLevel())) {
				throw new IllegalArgumentException("Unsupported batch level: " + step.getLevel());
			}
			Path input = argumentPath(step, "--input");
			Path output = argumentPath(step, "--output");
			Object parsed = switch (step.getCommand()) {
			case "index-parse" -> new IndexBdmvParser().parse(input);
			case "mobj-parse" -> new MovieObjectsParser().parse(input);
			case "playlist-parse" -> new MoviePlaylistParser().parse(input);
			case "clip-parse" -> new ClipInfoParser().parse(input);
			case "m2ts-info" -> new M2tsParser().parse(input);
			default -> throw new IllegalArgumentException("Unsupported parser command: " + step.getCommand());
			};
			if (output.getParent() != null) {
				Files.createDirectories(output.getParent());
			}
			JsonMapperFactory.get().writeValue(output.toFile(), parsed);
			System.out.println("Parsed " + input.getFileName() + " → " + output);
		}

		private static Path argumentPath(BatchStep step, String flag) {
			if (step.getArguments() != null) {
				for (var argument : step.getArguments()) {
					if (flag.equals(argument.getFlag()) && argument.getValue() != null) {
						return Path.of(argument.getValue());
					}
				}
			}
			throw new IllegalArgumentException("Batch step " + step.describe() + " is missing " + flag);
		}
	}
}
