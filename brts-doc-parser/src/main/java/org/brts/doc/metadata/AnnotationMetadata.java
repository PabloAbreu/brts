package org.brts.doc.metadata;

import java.util.Map;

public record AnnotationMetadata(String type, Map<String, Object> attributes) {
}