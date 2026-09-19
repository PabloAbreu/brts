package org.brts.doc.generator;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-doc-generator/src/main/java/org/brts/doc/generator/MarkdownDocumentationGenerator.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.brts.doc.metadata.AnnotationMetadata;
import org.brts.doc.metadata.CliMetadata;
import org.brts.doc.metadata.CommandMetadata;
import org.brts.doc.metadata.FieldMetadata;
import org.brts.doc.metadata.LevelMetadata;
import org.brts.doc.metadata.OptionMetadata;
import org.brts.doc.metadata.TypeMetadata;
import org.brts.doc.metadata.display.AnnotationDisplayRegistry;
import org.brts.doc.metadata.display.DisplayedAnnotation;
import org.brts.doc.metadata.display.DisplayedAttribute;

public final class MarkdownDocumentationGenerator implements DocumentationGenerator {

	private final AnnotationDisplayRegistry annotationDisplayRegistry;

	public MarkdownDocumentationGenerator() {
		this(AnnotationDisplayRegistry.defaults());
	}

	public MarkdownDocumentationGenerator(AnnotationDisplayRegistry annotationDisplayRegistry) {
		this.annotationDisplayRegistry = annotationDisplayRegistry;
	}

	@Override
	public void generate(CliMetadata metadata, Path outputDirectory) throws IOException {
		if (!"1.0".equals(metadata.schemaVersion())) {
			throw new IllegalArgumentException("Unsupported CLI metadata schema version: " + metadata.schemaVersion());
		}
		Files.createDirectories(outputDirectory);
		Files.createDirectories(outputDirectory.resolve("commands"));
		Files.createDirectories(outputDirectory.resolve("types"));
		write(outputDirectory.resolve("index.md"), rootIndex(metadata));
		for (LevelMetadata level : metadata.levels()) {
			Path levelDirectory = outputDirectory.resolve("commands").resolve(slug(level.name()));
			Files.createDirectories(levelDirectory);
			write(levelDirectory.resolve("index.md"), levelIndex(level));
			for (CommandMetadata command : level.commands()) {
				write(levelDirectory.resolve(slug(command.name()) + ".md"), commandPage(command));
			}
		}
		for (TypeMetadata type : metadata.types().values()) {
			write(outputDirectory.resolve("types").resolve(typeFile(type.qualifiedName())), typePage(type));
		}
	}

	private String rootIndex(CliMetadata metadata) {
		StringBuilder page = new StringBuilder("# BRTS CLI Reference\n\n");
		page.append("Application version: `").append(metadata.applicationVersion()).append("`  \n");
		page.append("Metadata schema: `").append(metadata.schemaVersion()).append("`\n\n## Command levels\n\n");
		for (LevelMetadata level : metadata.levels()) {
			page.append("- [").append(level.name()).append("](commands/").append(slug(level.name()))
					.append("/index.md) (").append(level.commands().size()).append(" commands)\n");
		}
		page.append("\n## Input types\n\n");
		for (TypeMetadata type : metadata.types().values()) {
			page.append("- [").append(type.qualifiedName()).append("](types/").append(typeFile(type.qualifiedName()))
					.append(")\n");
		}
		return page.toString();
	}

	private String levelIndex(LevelMetadata level) {
		StringBuilder page = new StringBuilder("# ").append(level.name()).append(" commands\n\n");
		page.append("[All commands](../../index.md)\n\n");
		for (CommandMetadata command : level.commands()) {
			page.append("- [`").append(command.path()).append("`](").append(slug(command.name())).append(".md) - ")
					.append(command.description()).append("\n");
		}
		return page.toString();
	}

	private String commandPage(CommandMetadata command) {
		StringBuilder page = new StringBuilder("# `").append(command.path()).append("`\n\n");
		page.append("[").append(command.level()).append(" commands](index.md) | [All commands](../../index.md)\n\n")
				.append(command.description()).append("\n\n## Synopsis\n\n```text\nbrts ").append(command.path())
				.append(" [options]\n```\n\n## Options\n\n");
		for (OptionMetadata option : command.options()) {
			if (option.hidden()) {
				continue;
			}
			String type = simplifyType(option.javaType());
			page.append("### `").append(option.name()).append("`\n\n").append(option.description()).append("\n\n")
					.append("- Type: `").append(type).append("`\n").append("- Required: `").append(option.required())
					.append("`\n").append("- Inherited: `").append(option.inherited()).append("`\n");
			if (!option.aliases().isEmpty()) {
				page.append("- Aliases: ").append(codeList(option.aliases())).append("\n");
			}
			if (!option.depends().isEmpty()) {
				page.append("- Depends on: ").append(codeList(option.depends())).append("\n");
			}
			if (!option.forbids().isEmpty()) {
				page.append("- Forbids: ").append(codeList(option.forbids())).append("\n");
			}
			if (option.inputTypeRef() != null) {
				page.append("- JSON input: [").append(option.inputTypeRef()).append("](../../types/")
						.append(typeFile(option.inputTypeRef())).append(")\n");
			}
			appendAnnotations(page, option.annotations());
			page.append('\n');
		}
		page.append("## Implementation\n\n- Runner: `").append(command.runnerType()).append("`\n- Options bean: `")
				.append(command.optionsType()).append("`\n");
		return page.toString();
	}

	private String typePage(TypeMetadata type) {
		StringBuilder page = new StringBuilder("# `").append(type.qualifiedName()).append("`\n\n")
				.append("[CLI reference](../index.md)\n\n");
		if (type.description() != null) {
			page.append(type.description()).append("\n\n");
		}
		page.append("- Kind: `").append(type.kind()).append("`\n");
		if (type.superType() != null) {
			page.append("- Extends: `").append(type.superType()).append("`\n");
		}
		if (type.discriminator() != null) {
			page.append("- JSON discriminator: `").append(type.discriminator()).append("`\n");
		}
		if (!type.subtypes().isEmpty()) {
			page.append("\n## Variants\n\n");
			for (Map.Entry<String, String> subtype : type.subtypes().entrySet()) {
				page.append("- `").append(subtype.getKey()).append("`: [").append(subtype.getValue()).append("](")
						.append(typeFile(subtype.getValue())).append(")\n");
			}
		}
		if (!type.enumValues().isEmpty()) {
			page.append("\n## Values\n\n").append(codeList(type.enumValues())).append("\n");
		}
		if (!type.fields().isEmpty()) {
			page.append("\n## Fields\n\n");
			for (FieldMetadata field : type.fields()) {
				page.append("### `").append(field.jsonName()).append("`\n\n");
				if (field.description() != null) {
					page.append(field.description()).append("\n\n");
				}
				page.append("- Java field: `").append(field.name()).append("`\n- Type: `")
						.append(simplifyType(field.javaType())).append("`\n- Required: `").append(field.required())
						.append("`\n");
				if (field.typeRef() != null) {
					page.append("- Nested type: [").append(field.typeRef()).append("](")
							.append(typeFile(field.typeRef())).append(")\n");
				}
				appendAnnotations(page, field.annotations());
				page.append('\n');
			}
		}
		return page.toString();
	}

	private void appendAnnotations(StringBuilder page, List<AnnotationMetadata> annotations) {
		List<DisplayedAnnotation> displayed = annotationDisplayRegistry.display(annotations);
		if (displayed.isEmpty()) {
			return;
		}
		page.append("- Annotations:\n");
		for (DisplayedAnnotation annotation : displayed) {
			page.append("  - `@").append(annotation.name()).append('`');
			if (!annotation.attributes().isEmpty()) {
				List<String> parts = new ArrayList<>();
				for (DisplayedAttribute attribute : annotation.attributes()) {
					parts.add(attribute.label() + ": `" + inlineCode(attribute.value()) + "`");
				}
				page.append(" - ").append(String.join(", ", parts));
			}
			page.append('\n');
		}
	}

	private String inlineCode(String value) {
		return value.replace("`", "\\`").replaceAll("\\s+", " ");
	}

	private String codeList(List<String> values) {
		List<String> formatted = new ArrayList<>();
		for (String value : values) {
			formatted.add("`" + value + "`");
		}
		return String.join(", ", formatted);
	}

	private String typeFile(String qualifiedName) {
		return slug(qualifiedName) + ".md";
	}

	// strips package prefixes from qualified type names, including generics, e.g. "java.util.Set<java.lang.Integer>" ->
	// "Set<Integer>"
	private String simplifyType(String type) {
		if (type == null) {
			return null;
		}
		return type.replaceAll("\\b(?:[a-zA-Z_$][a-zA-Z0-9_$]*\\.)+([A-Za-z_$][a-zA-Z0-9_$]*)\\b", "$1");
	}

	private String slug(String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void write(Path path, String content) throws IOException {
		Files.writeString(path, content, StandardCharsets.UTF_8);
	}
}
