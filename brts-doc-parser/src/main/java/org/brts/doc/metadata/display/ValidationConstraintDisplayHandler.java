package org.brts.doc.metadata.display;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Renders bean validation constraints, keeping only the attributes that carry constraint semantics.
 */
public final class ValidationConstraintDisplayHandler implements AnnotationDisplayHandler {

	private static final List<String> SUPPORTED_PACKAGES = List.of("jakarta.validation.constraints.",
			"org.brts.common.validation.");

	private static final Set<String> EXCLUDED = Set.of("groups", "payload", "flags");

	@Override
	public boolean supports(AnnotationMetadata annotation) {
		return SUPPORTED_PACKAGES.stream().anyMatch(annotation.type()::startsWith);
	}

	@Override
	public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
		Map<String, Object> attributes = new LinkedHashMap<>(annotation.attributes());
		if (isDefaultMessage(attributes.get("message"))) {
			attributes.remove("message");
		}
		return Optional.of(new DisplayedAnnotation(AnnotationDisplaySupport.simpleName(annotation.type()),
				AnnotationDisplaySupport.attributes(attributes, EXCLUDED)));
	}

	/** Unresolved bundle keys such as {jakarta.validation.constraints.NotBlank.message}. */
	private boolean isDefaultMessage(Object message) {
		return message instanceof String text && text.startsWith("{") && text.endsWith("}");
	}
}
