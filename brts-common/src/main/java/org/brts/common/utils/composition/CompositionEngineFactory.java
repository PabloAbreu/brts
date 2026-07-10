package org.brts.common.utils.composition;

import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.composition.java2d.Java2DCompositionEngine;
import org.brts.common.utils.composition.opencv.OpenCvCompositionEngine;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves and caches the active {@link CompositionEngine} singleton.
 *
 * <p>
 * The implementation is chosen once at first access via the {@code brts.composition.engine} property read from
 * {@link BrtsFileConfig}. Accepted values:
 * <ul>
 * <li>{@code java2d} – {@link Java2DCompositionEngine} (default)</li>
 * <li>{@code opencv} – {@link OpenCvCompositionEngine}</li>
 * </ul>
 */
@Slf4j
public final class CompositionEngineFactory {

	public static final String ENGINE_PROPERTY = "brts.composition.engine";

	public static final String ENGINE_JAVA2D = "java2d";

	public static final String ENGINE_OPENCV = "opencv";

	private static volatile CompositionEngine instance;

	private CompositionEngineFactory() {
	}

	/** Returns the active {@link CompositionEngine} singleton (created on first call). */
	public static CompositionEngine get() {
		if (instance == null) {
			synchronized (CompositionEngineFactory.class) {
				if (instance == null) {
					instance = create();
				}
			}
		}
		return instance;
	}

	private static CompositionEngine create() {
		String value = BrtsFileConfig.getInstance().getProperty(ENGINE_PROPERTY);
		if (ENGINE_OPENCV.equalsIgnoreCase(value)) {
			log.info("Composition engine: OpenCV (INTER_AREA)");
			return new OpenCvCompositionEngine();
		}
		if (value != null && !value.isBlank() && !ENGINE_JAVA2D.equalsIgnoreCase(value)) {
			log.warn("Unknown composition engine '{}', falling back to java2d", value);
		}
		log.info("Composition engine: Java2D");
		return new Java2DCompositionEngine();
	}

	/** Resets the singleton — for testing purposes only. */
	static void reset() {
		instance = null;
	}

}
