package org.brts.doc.metadata;

import java.util.List;
import java.util.Map;

public record TypeMetadata(String qualifiedName, String kind, String description, String superType,
		List<String> typeArguments, List<String> enumValues, String discriminator, Map<String, String> subtypes,
		List<FieldMetadata> fields) {
}