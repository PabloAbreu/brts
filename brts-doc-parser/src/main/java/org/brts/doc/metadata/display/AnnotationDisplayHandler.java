package org.brts.doc.metadata.display;

import java.util.Optional;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Turns raw annotation metadata into a presentation-neutral simplified form.
 */
public interface AnnotationDisplayHandler {

	boolean supports(AnnotationMetadata annotation);

	/** An empty result suppresses the annotation from the documentation. */
	Optional<DisplayedAnnotation> display(AnnotationMetadata annotation);
}
