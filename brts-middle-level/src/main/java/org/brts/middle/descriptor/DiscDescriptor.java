package org.brts.middle.descriptor;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Middle-level descriptor for a complete Blu-ray disc.
 * <p>
 * This is the main input for the middle-level authoring pipeline. Example {@code disc.json}:
 *
 * <pre>{@code
 * {
 *   "discName": "My Movie",
 *   "hasTopMenu": false,
 *   "titles": [
 *     {
 *       "titleId": 1,
 *       "sourceMkv": "/videos/movie.mkv",
 *       "audioLanguages": ["eng"],
 *       "chapters": [{ "timeSeconds": 0 }, { "timeSeconds": 3600 }]
 *     }
 *   ]
 * }
 * }</pre>
 */
@Getter
@Setter
public class DiscDescriptor {

	private String discName;

	private String outputFolder;// for the BD

	/** If true, a top menu object will be generated (requires menu title config). */
	private boolean hasTopMenu = false;

	private List<TitleDescriptor> titles;

}
