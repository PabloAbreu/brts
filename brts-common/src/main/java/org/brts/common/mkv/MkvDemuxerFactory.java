package org.brts.common.mkv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/mkv/MkvDemuxerFactory.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import org.brts.common.utils.BrtsFileConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves and caches the active MKV demuxer singleton.
 * <p>
 * The implementation is chosen once at first access via the {@code brts.mkv.demuxer} property read from
 * {@link BrtsFileConfig}. Accepted values:
 * <ul>
 * <li>{@code jebml} - {@link MkvDemuxer}</li>
 * <li>{@code ffmpeg} - {@link FfmpegDemuxer} (default)</li>
 * <li>{@code tsmuxer} - {@link TsMuxerDemuxer}</li>
 * </ul>
 */
@Slf4j
public final class MkvDemuxerFactory {

	public static final String DEMUXER_PROPERTY = "brts.mkv.demuxer";

	public static final String DEMUXER_JEBML = "jebml";

	public static final String DEMUXER_FFMPEG = "ffmpeg";

	public static final String DEMUXER_TSMUXER = "tsmuxer";

	private static volatile EsDemuxer instance;

	private MkvDemuxerFactory() {
	}

	/** Returns the active {@link EsDemuxer} singleton (created on first call). */
	public static EsDemuxer get() {
		if (instance == null) {
			synchronized (MkvDemuxerFactory.class) {
				if (instance == null) {
					instance = create();
				}
			}
		}
		return instance;
	}

	private static EsDemuxer create() {
		String value = BrtsFileConfig.getInstance().getProperty(DEMUXER_PROPERTY);
		// JEBML only when requested, otherwise FFmpeg is preferred if available
		if (DEMUXER_JEBML.equalsIgnoreCase(value)) {
			log.info("MKV demuxer: JEBML");
			return new MkvDemuxer();
		}
		if (DEMUXER_TSMUXER.equalsIgnoreCase(value)) {
			log.info("MKV demuxer: tsMuxeR");
			return new TsMuxerDemuxer();
		}
		if (value != null && !value.isBlank() && !DEMUXER_FFMPEG.equalsIgnoreCase(value)) {
			log.warn("Unknown MKV demuxer '{}', falling back to FFmpeg", value);
		}
		log.info("MKV demuxer: FFmpeg (default)");
		return new FfmpegDemuxer();
	}
}
