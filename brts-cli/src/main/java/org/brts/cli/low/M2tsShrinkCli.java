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
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.validation.ExistingDirectory;
import org.brts.common.validation.ExistingFile;
import org.brts.lowlevel.shrink.M2tsShrinkConfig;
import org.brts.lowlevel.shrink.M2tsShrinkInputResolver;
import org.brts.lowlevel.shrink.M2tsShrinkPlan;
import org.brts.lowlevel.shrink.M2tsShrinkPlanner;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.kohsuke.args4j.Option;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

/** Plan-only CLI entry point for M2TS stream filtering and video-size assessment. */
public class M2tsShrinkCli {

	public static class Options extends BaseOptions {

		@ExistingDirectory
		@Option(name = "--disc-root", required = true, usage = "Blu-ray disc root containing BDMV")
		Path discRoot;

		@NotNull(message = "--clip-id must contain exactly five digits")
		@Pattern(regexp = "[0-9]{5}", message = "--clip-id must contain exactly five digits")
		@Option(name = "--clip-id", required = true, usage = "Five-digit clip identifier, e.g. 00001")
		String clipId;

		@ExistingFile
		@JsonInputOption(name = "--config", required = true, usage = "Versioned m2ts-shrink JSON filtering config")
		M2tsShrinkConfig config;

		@Positive(message = "--target-bytes must be positive")
		@Option(name = "--target-bytes", required = true, usage = "Maximum desired output M2TS size in bytes")
		long targetBytes;

		@Option(name = "--engine", usage = "Whole-M2TS conversion engine (FFMPEG, NVENCC, VCEENCC, QSVENCC)")
		Engine engine;

		@Option(name = "--report", usage = "Write the plan JSON to this path (default: print to stdout)")
		File report;
	}

	@RequiredArgsConstructor
	public static class Shrink extends FeatureRunner<Options> {

		private final M2tsShrinkInputResolver resolver;
		private final M2tsShrinkPlanner planner;

		public Shrink() {
			this(new M2tsShrinkInputResolver(), new M2tsShrinkPlanner());
		}

		@Override
		public String getCommandName() {
			return "m2ts-shrink";
		}

		@Override
		public String getDescription() {
			return "Analyze stream filtering and video-size requirements (plan only; no media is changed)";
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
			System.err.println("Decision: " + plan.decision() + " (plan only; no files were changed)");
		}
	}
}
