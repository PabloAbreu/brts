package org.brts.lowlevel.titlemenu.descriptor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * An optional absolute bounding box (in pixels) that constrains button placement. When set on a {@link LayoutConfig},
 * it replaces the margin-based usable area entirely: the layout engine positions buttons within ({@code x}, {@code y},
 * {@code width}, {@code height}) instead of deriving the area from screen dimensions minus margins.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class BoundingBox {

	/** X origin of the bounding box in pixels. */
	private Integer x;

	/** Y origin of the bounding box in pixels. */
	private Integer y;

	/** Width of the bounding box in pixels. */
	private Integer width;

	/** Height of the bounding box in pixels. */
	private Integer height;

	/**
	 * Returns {@code true} when all four fields are set and dimensions are positive.
	 */
	public boolean isValid() {
		return x != null && y != null && width != null && height != null && width > 0 && height > 0;
	}
}
