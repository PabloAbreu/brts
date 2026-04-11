package org.brts.highlevel.descriptor;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Root high-level disc descriptor.
 * <p>
 * The {@code templateType} field selects the authoring template:
 * <ul>
 * <li>{@code "MOVIE"} → {@link MovieDiscDescriptor}</li>
 * <li>{@code "TV_SERIES"} → {@link TvSeriesDiscDescriptor}</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "templateType")
@JsonSubTypes({ @JsonSubTypes.Type(value = MovieDiscDescriptor.class, name = "MOVIE"),
		@JsonSubTypes.Type(value = TvSeriesDiscDescriptor.class, name = "TV_SERIES") })
@Getter
@Setter
public abstract class HighLevelDiscDescriptor {

	private String templateType;

	private String discTitle;

	private String outputDirectory;

	/**
	 * If true, the high-level step only generates middle-level descriptors without
	 * running them.
	 */
	private boolean dryRun = false;

}
