package org.brts.doc.metadata;

import java.util.List;

public record FieldMetadata(String name, String jsonName, String javaType, String description, boolean required,
		List<AnnotationMetadata> annotations, String typeRef, List<String> typeArguments) {
}