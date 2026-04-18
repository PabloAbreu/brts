package org.brts.common.m2ts;

/**
 * Chooses the appropriate {@link M2tsClipWriter} implementation based on the runtime environment.
 */
public class M2tsClipWriterFactory {
	public static M2tsClipWriter createWriter() {
		if (TsMuxerM2tsClipWriter.isTsMuxeRAvailable())
			return new TsMuxerM2tsClipWriter();
		return new M2tsClipWriterImpl();
	}
}
