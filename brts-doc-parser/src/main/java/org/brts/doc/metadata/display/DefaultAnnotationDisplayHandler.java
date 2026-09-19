package org.brts.doc.metadata.display;

import java.util.Optional;
import java.util.Set;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Fallback used for annotations no dedicated handler claims.
 */
public final class DefaultAnnotationDisplayHandler implements AnnotationDisplayHandler {

	@Override
	public boolean supports(AnnotationMetadata annotation) {
		return true;
	}

	@Override
	public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
		return Optional.of(new DisplayedAnnotation(AnnotationDisplaySupport.simpleName(annotation.type()),
				AnnotationDisplaySupport.attributes(annotation.attributes(), Set.of())));
	}
}
