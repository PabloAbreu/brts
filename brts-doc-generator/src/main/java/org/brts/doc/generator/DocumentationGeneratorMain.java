package org.brts.doc.generator;

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