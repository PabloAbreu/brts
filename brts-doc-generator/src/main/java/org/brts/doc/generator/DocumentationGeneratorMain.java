package org.brts.doc.generator;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-generator/src/main/java/org/brts/doc/generator/DocumentationGeneratorMain.java' is part of BRTS.
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

import java.nio.file.Path;

import org.brts.common.json.JsonMapperFactory;
import org.brts.doc.metadata.CliMetadata;

public final class DocumentationGeneratorMain {

	private DocumentationGeneratorMain() {
	}

	public static void main(String[] args) throws Exception {
		Path input = null;
		Path output = null;
		for (int index = 0; index < args.length; index++) {
			switch (args[index]) {
			case "--input" -> input = Path.of(next(args, ++index, "--input"));
			case "--output" -> output = Path.of(next(args, ++index, "--output"));
			default -> throw new IllegalArgumentException("Unknown argument: " + args[index]);
			}
		}
		if (input == null || output == null) {
			throw new IllegalArgumentException("Required arguments: --input <metadata.json> --output <directory>");
		}
		CliMetadata metadata = JsonMapperFactory.get().readValue(input.toFile(), CliMetadata.class);
		new MarkdownDocumentationGenerator().generate(metadata, output);
	}

	private static String next(String[] args, int index, String option) {
		if (index >= args.length) {
			throw new IllegalArgumentException("Missing value for " + option);
		}
		return args[index];
	}
}
