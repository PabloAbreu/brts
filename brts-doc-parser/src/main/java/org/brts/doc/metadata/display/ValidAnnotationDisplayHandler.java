package org.brts.doc.metadata.display;

import java.util.Optional;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * {@code @Valid} is a marker: its attributes never carry information.
 */
public final class ValidAnnotationDisplayHandler implements AnnotationDisplayHandler {

	@Override
	public boolean supports(AnnotationMetadata annotation) {
		return "jakarta.validation.Valid".equals(annotation.type());
	}

	@Override
	public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
		return Optional.of(new DisplayedAnnotation("Valid"));
	}
}
