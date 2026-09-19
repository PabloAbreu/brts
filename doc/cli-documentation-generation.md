# CLI documentation generation

BRTS generates machine-readable CLI metadata and Markdown documentation as part of the Maven `package` lifecycle.

## Artifacts

`org.brts:brts-doc-parser` publishes:

- The parser and metadata model JAR.
- `brts-doc-parser-<version>-cli-metadata.json`, attached with type `json` and classifier `cli-metadata`.
- The same metadata at `META-INF/brts/cli-metadata.json` inside the parser JAR.

`org.brts:brts-doc-generator` publishes:

- The documentation generator JAR.
- `brts-doc-generator-<version>-markdown-docs.zip`, attached with classifier `markdown-docs`.

The unpacked Markdown tree is available under `brts-doc-generator/target/generated-docs`.

## Generate release documentation

```bash
mvn clean package
```

To build only the documentation pipeline and its dependencies:

```bash
mvn -pl brts-doc-generator -am package
```

## Metadata contents

The JSON document contains a schema version, application version, ordered CLI levels and commands, complete command
paths, inherited and hidden options, args4j attributes, validation annotations, warnings, and a qualified-name type
registry for typed JSON inputs.

Jackson annotations determine effective JSON property names, ignored fields, and polymorphic subtype relationships.
They are not copied into the exported annotation list. Missing source Javadocs produce warnings and null descriptions;
they do not stop generation.

Nested descriptor fields are expanded only for options declared with `@JsonInputOption`. Ordinary `File` options remain
documented as file options, even when command implementation code later deserializes them.

## Standalone generation

The parser entry point is `org.brts.doc.parser.CliMetadataParserMain`. It accepts `--version`, `--output`, and one or
more `--source-root` arguments.

The Markdown entry point is `org.brts.doc.generator.DocumentationGeneratorMain`:

```bash
java -cp <generator-and-dependencies> org.brts.doc.generator.DocumentationGeneratorMain \
  --input cli-metadata.json \
  --output generated-docs
```

Consumers must reject unsupported metadata schema versions. The current schema version is `1.0`.