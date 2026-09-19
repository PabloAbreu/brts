package org.brts.doc.metadata.display;

import java.util.List;
import java.util.Optional;

import org.brts.doc.metadata.AnnotationMetadata;

/**
 * Hides annotations whose content is already rendered as first-class metadata.
 */
public final class SuppressedAnnotationDisplayHandler implements AnnotationDisplayHandler {

	private static final List<String> SUPPRESSED = List.of("org.kohsuke.args4j.Option", "org.kohsuke.args4j.Argument",
			"org.brts.cli.JsonInputOption");

	@Override
	public boolean supports(AnnotationMetadata annotation) {
		return SUPPRESSED.contains(annotation.type());
	}

	@Override
	public Optional<DisplayedAnnotation> display(AnnotationMetadata annotation) {
		return Optional.empty();
	}
}
