package org.brts.doc.parser;

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