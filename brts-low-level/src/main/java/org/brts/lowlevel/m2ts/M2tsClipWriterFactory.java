package org.brts.lowlevel.m2ts;

import org.brts.common.utils.TsMuxerUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Chooses the appropriate {@link M2tsClipWriter} implementation based on the runtime environment.
 */
@Slf4j
public class M2tsClipWriterFactory {

	public static M2tsClipWriter createWriter() {
		if (TsMuxerUtils.isTsMuxeRAvailable())
			return new TsMuxerM2tsClipWriter();
		log.warn("tsMuxeR not found in PATH or configured via properties; falling back to basic M2TS writer (buggy).");
		return new M2tsClipWriterImpl();
	}

}
