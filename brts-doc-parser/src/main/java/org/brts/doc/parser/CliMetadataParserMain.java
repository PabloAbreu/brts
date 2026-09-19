package org.brts.doc.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-parser/src/main/java/org/brts/doc/parser/CliMetadataParserMain.java' is part of BRTS.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.doc.metadata.CliMetadata;

public final class CliMetadataParserMain {

	private CliMetadataParserMain() {
	}

	public static void main(String[] args) throws Exception {
		String version = null;
		Path output = null;
		List<Path> sourceRoots = new ArrayList<>();
		for (int index = 0; index < args.length; index++) {
			switch (args[index]) {
			case "--version" -> version = next(args, ++index, "--version");
			case "--output" -> output = Path.of(next(args, ++index, "--output"));
			case "--source-root" -> sourceRoots.add(Path.of(next(args, ++index, "--source-root")));
			default -> throw new IllegalArgumentException("Unknown argument: " + args[index]);
			}
		}
		if (version == null || output == null) {
			throw new IllegalArgumentException("Required arguments: --version <version> --output <file>");
		}

		CliMetadata metadata = new CliMetadataParser(sourceRoots).parse(version);
		Files.createDirectories(output.toAbsolutePath().getParent());
		JsonMapperFactory.get().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), metadata);
		for (String warning : metadata.warnings()) {
			System.err.println("[brts-doc-parser] " + warning);
		}
	}

	private static String next(String[] args, int index, String option) {
		if (index >= args.length) {
			throw new IllegalArgumentException("Missing value for " + option);
		}
		return args[index];
	}
}
