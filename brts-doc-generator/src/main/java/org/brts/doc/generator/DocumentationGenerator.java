package org.brts.doc.generator;

import java.io.IOException;
import java.nio.file.Path;

import org.brts.doc.metadata.CliMetadata;

public interface DocumentationGenerator {

	void generate(CliMetadata metadata, Path outputDirectory) throws IOException;
}