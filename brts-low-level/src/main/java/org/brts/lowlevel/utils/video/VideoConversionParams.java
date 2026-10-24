/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/VideoConversionParams.java' is part of BRTS.
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

package org.brts.lowlevel.utils.video;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.brts.common.model.StreamCodingType;

import lombok.Getter;
import lombok.Setter;

/**
 * File-to-file compressed elementary-video conversion request. Null target fields retain source settings.
 * Engine-specific option names have no leading dashes; values are single argument tokens, not shell expressions.
 * Exactly one bitrate or native quality option (crf, cqp, icq, la-icq, qvbr) is required. Instances are mutable; do not
 * modify a request concurrently with prepare/convert.
 */
@Getter
@Setter
public class VideoConversionParams {

	public enum Engine {
		FFMPEG, NVENCC, VCEENCC, QSVENCC;

		public static Engine parse(String value) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (RuntimeException e) {
				throw new IllegalArgumentException("Unknown video converter engine: " + value, e);
			}
		}
	}

	/**
	 * Choice of rate control method for video encoding.
	 *
	 * Note that most of them are bad for blu-rays due to strict bitrate requirements. Blu-ray discs typically require
	 * CBR or constrained VBR for compliance.
	 *
	 * However in practice, other rate control methods may be used when reducing file size for use with tolerant
	 * hardware players.
	 */
	public enum RateControl {
		VBR, CBR, CRF, CQP, ICQ, LA_ICQ, QVBR
	}

	private Path inputPath;
	private Path outputPath;
	private StreamCodingType codec = StreamCodingType.H264_AVC;
	private Engine engine;
	private String executablePath;
	private Integer width;
	private Integer height;
	private FrameRate frameRate;
	/** Optional source timing override for ES without timing; distinct from requested playback speed. */
	private FrameRate sourceFrameRate;
	private Integer bitrateKbps;
	private RateControl rateControl;
	private Map<String, String> engineOptions = new LinkedHashMap<>();
	private boolean overwrite;
	/** Null means no conversion time limit. */
	private Duration timeout;
}
