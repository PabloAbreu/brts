package org.brts.highlevel.template;



import org.brts.highlevel.descriptor.HighLevelDiscDescriptor;
import org.brts.highlevel.style.StyleTemplate;
import org.brts.middle.descriptor.DiscDescriptor;

/**
 * Contract for all high-level disc authoring templates.
 * <p>
 * A template translates a {@link HighLevelDiscDescriptor} into a {@link DiscDescriptor} (middle-level), plus any
 * additional metadata (e.g. menu structure) the middle-level layer needs.
 */
public interface DiscTemplate<D extends HighLevelDiscDescriptor> {

	/**
	 * Expands the high-level descriptor into a middle-level disc descriptor.
	 *
	 * @param descriptor the high-level input
	 * @param style      the loaded style template, or {@code null}
	 * @return the equivalent middle-level representation
	 */
	DiscDescriptor expand(D descriptor, StyleTemplate style) throws java.io.IOException;

	/** Returns the template type token this implementation handles (e.g. "MOVIE"). */
	String templateType();

}
