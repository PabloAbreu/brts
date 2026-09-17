package org.brts.doc.metadata;

import java.util.List;

public record OptionMetadata(String name, List<String> aliases, String description, String javaType, boolean required,
		boolean help, boolean hidden, List<String> depends, List<String> forbids, boolean inherited,
		List<AnnotationMetadata> annotations, String inputTypeRef) {
}