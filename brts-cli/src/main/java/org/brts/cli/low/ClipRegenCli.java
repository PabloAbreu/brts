package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/ClipRegenCli.java' is part of BRTS.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.brts.cli.FeatureRunner;
import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.clpi.M2tsClpiRegenBuilder;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Path;

/**
 * CLI for the {@code clpi-regen} command.
 */
public class ClipRegenCli {

	public static class RegenOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the source .m2ts file to parse")
		File input;

		@Option(name = "--output", required = true, usage = "Output directory for the generated .clpi file (BDMV/CLIPINF/ recommended)")
		File outputDir;

		@Option(name = "--clip-name", usage = "5-digit clip name without extension (default: input file basename without extension)")
		String clipName;

		@Option(name = "--json", usage = "Also write the ClipInfo model as a JSON sidecar file alongside the .clpi")
		boolean writeJson;

	}

	public static class Regen extends FeatureRunner<RegenOptions> {

		@Override
		public String getCommandName() {
			return "clpi-regen";
		}

		@Override
		public String getDescription() {
			return "Regenerate a CLPI file from an M2TS stream";
		}

		@Override
		protected void execute(RegenOptions opts) throws Exception {
			Path inputPath = opts.input.toPath();
			String clipName = resolveClipName(opts);

			ClipInfo clipInfo = new M2tsClpiRegenBuilder().build(inputPath, clipName);

			Path outputDir = opts.outputDir.toPath();
			Path clpiPath = outputDir.resolve(clipName + ".clpi");
			new ClipInfoWriter().write(clipInfo, clpiPath);
			System.out.println("CLPI generated → " + clpiPath);

			if (opts.writeJson) {
				Path jsonPath = outputDir.resolve(clipName + ".clpi.json");
				ObjectMapper mapper = JsonMapperFactory.get();
				mapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), clipInfo);
				System.out.println("CLPI JSON sidecar → " + jsonPath);
			}
		}

	}

	private static String resolveClipName(RegenOptions opts) {
		if (opts.clipName != null && !opts.clipName.isBlank()) {
			return opts.clipName;
		}
		String fileName = opts.input.getName();
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

}
