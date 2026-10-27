/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/low/M2tsShrinkCli.java' is part of BRTS.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.validation.ExistingDirectory;
import org.brts.lowlevel.shrink.M2tsShrinkConfig;
import org.brts.lowlevel.shrink.M2tsShrinkInputResolver;
import org.brts.lowlevel.shrink.M2tsShrinkInventory;
import org.brts.lowlevel.shrink.M2tsShrinkPlan;
import org.brts.lowlevel.shrink.M2tsShrinkPlanner;
import org.brts.lowlevel.shrink.BatchWholeM2tsConverter;
import org.brts.lowlevel.shrink.WholeM2tsConverter.BatchExecutor;
import org.brts.lowlevel.shrink.WholeM2tsConverter.M2tsConversionRequest;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.kohsuke.args4j.Option;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/** Analyze shrink decisions and execute video-required conversion through a resumable batch. */
public class M2tsShrinkCli {
	@FunctionalInterface
	public interface InventoryResolver {
		M2tsShrinkInventory resolve(Path discRoot, String clipId) throws IOException;
	}

	public static class Options extends BaseOptions {

		@ExistingDirectory
		@Option(name = "--disc-root", required = true, usage = "Blu-ray disc root containing BDMV")
		Path discRoot;

		@NotNull(message = "--clip-id must contain exactly five digits")
		@Pattern(regexp = "[0-9]{5}", message = "--clip-id must contain exactly five digits")
		@Option(name = "--clip-id", required = true, usage = "Five-digit clip identifier, e.g. 00001")
		String clipId;

		@JsonInputOption(name = "--config", required = true, usage = "Versioned m2ts-shrink JSON filtering config")
		M2tsShrinkConfig config;

		@Positive(message = "--target-bytes must be positive")
		@Option(name = "--target-bytes", required = true, usage = "Maximum desired output M2TS size in bytes")
		long targetBytes;

		@Option(name = "--engine", usage = "Whole-M2TS conversion engine (FFMPEG, NVENCC, VCEENCC, QSVENCC)")
		Engine engine;

		@Option(name = "--report", usage = "Write the plan JSON to this path (default: print to stdout)")
		File report;

		@Option(name = "--output", usage = "Separate destination disc root (required for video conversion)")
		Path output;

		@Option(name = "--defer", usage = "Write batch/descriptors only; no extraction, encoding or muxing")
		boolean defer;

		@Option(name = "--work-dir", usage = "Workspace (default: OUTPUT/.brts-shrink/CLIP-ID); retained for resume")
		Path workDir;
	}

	@RequiredArgsConstructor
	public static class Shrink extends FeatureRunner<Options> {

		private final InventoryResolver resolver;
		private final M2tsShrinkPlanner planner;
		private final BatchExecutor executor;
		private final BatchWholeM2tsConverter.MetadataReader metadataReader;

		public Shrink() {
			this(new M2tsShrinkInputResolver(), new M2tsShrinkPlanner());
		}

		public Shrink(M2tsShrinkInputResolver resolver, M2tsShrinkPlanner planner) {
			this(resolver, planner, descriptor -> {
				try {
					new BatchRunCli.Run().run(new String[] { "--descriptor", descriptor.toString() });
				} catch (IOException e) {
					throw e;
				} catch (Exception e) {
					throw new IOException("Shrink batch failed: " + descriptor, e);
				}
			});
		}

		public Shrink(M2tsShrinkInputResolver resolver, M2tsShrinkPlanner planner, BatchExecutor executor) {
			this(resolver::resolve, planner, executor, new M2tsParser()::parse);
		}

		@Override
		public String getCommandName() {
			return "m2ts-shrink";
		}

		@Override
		public String getDescription() {
			return "Shrink video through a preservation batch; --defer prepares only (other decisions: analysis-only)";
		}

		@Override
		protected void execute(Options opts) throws Exception {
			M2tsShrinkConfig config = opts.config;

			if (opts.engine == null) {
				opts.engine = Engine
						.parse(BrtsFileConfig.getInstance().propertyOrDefault("brts.video.converter.engine", "ffmpeg"));
			}
			var inventory = resolver.resolve(opts.discRoot, opts.clipId);
			M2tsShrinkPlan plan = planner.plan(inventory, opts.clipId, config, opts.targetBytes, opts.engine);
			String json = JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValueAsString(plan);
			if (opts.report == null) {
				System.out.println(json);
			} else {
				Path reportPath = opts.report.toPath().toAbsolutePath().normalize();
				Path parent = reportPath.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				Files.writeString(reportPath, json);
				System.out.println("M2TS shrink plan → " + reportPath);
			}
			if (plan.decision() != M2tsShrinkPlan.Decision.VIDEO_CONVERSION_REQUIRED) {
				System.err.println("Decision: " + plan.decision() + " (analysis-only; not executed)");
				return;
			}
			if (opts.output == null) {
				throw new IllegalArgumentException("--output is required for VIDEO_CONVERSION_REQUIRED");
			}
			var converter = new BatchWholeM2tsConverter(opts.engine, executor, metadataReader);
			var request = new M2tsConversionRequest(inventory, plan, config, opts.output, opts.workDir, opts.clipId);
			if (opts.defer) {
				var prepared = converter.prepare(request);
				System.err.println("Deferred (no media operations): "
						+ JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValueAsString(prepared));
				System.err.println("Run: brts low batch-run --descriptor \"" + prepared.batchDescriptor() + "\"");
			} else {
				var result = converter.convert(request);
				System.err.println("Executed: "
						+ JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValueAsString(result));
			}
		}
	}
}
