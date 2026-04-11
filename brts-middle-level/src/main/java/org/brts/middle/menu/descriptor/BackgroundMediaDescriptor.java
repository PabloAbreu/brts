package org.brts.middle.menu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for all background media descriptors.
 * <p>
 * Currently only MKV is supported, but this polymorphic base allows future media types
 * (MP4, WAV+PNG, etc.) to be added without breaking the schema.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = MkvBackgroundMedia.class)
@JsonSubTypes({ @JsonSubTypes.Type(value = MkvBackgroundMedia.class, name = "MKV") })
public abstract class BackgroundMediaDescriptor {

	/**
	 * Discriminator field — e.g. "MKV". Mapped by Jackson for polymorphic
	 * deserialization.
	 */
	private String type;

}
