/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/clpi/M2tsVideoMetadataProbeFactory.java' is part of BRTS.
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

package org.brts.lowlevel.clpi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;

import lombok.extern.slf4j.Slf4j;

/** Creates the configured M2TS video metadata probe shared by CLI inspection and CLPI regeneration. */
@Slf4j
public final class M2tsVideoMetadataProbeFactory {

	public static final String ENGINE_PROPERTY = "brts.clpi.probe.engine";

	private M2tsVideoMetadataProbeFactory() {
	}

	/** Probe contract keyed by transport PID. */
	@FunctionalInterface
	public interface Probe {
		Map<Integer, M2tsVideoMetadataProbe.Metadata> probe(Path path, Map<Integer, StreamCodingType> videos)
				throws IOException;
	}

	/**
	 * Creates the configured backend. The default is {@code ffprobe}; if it is unavailable, the existing FFmpeg library
	 * probe is used instead.
	 */
	public static Probe createDefault(UnaryOperator<String> config) {
		String engine = config.apply(ENGINE_PROPERTY);
		engine = engine == null || engine.isBlank() ? "ffprobe" : engine.trim().toLowerCase(Locale.ROOT);
		Probe library = (path, videos) -> new M2tsVideoMetadataProbe().probe(path, videos);
		return switch (engine) {
		case "ffmpeg" -> library;
		case "ffprobe" -> {
			var ffprobe = FfprobeM2tsVideoMetadataProbe.fromConfig(config);
			if (ffprobe == null) {
				log.warn("ffprobe executable '{}' unavailable; M2TS metadata probe falls back to FFmpeg library",
						config.apply(FfprobeM2tsVideoMetadataProbe.BINARY_PROPERTY));
				yield library;
			}
			yield ffprobe::probe;
		}
		default ->
			throw new IllegalArgumentException(ENGINE_PROPERTY + " must be 'ffprobe' or 'ffmpeg', got: " + engine);
		};
	}

	/** Builds the PID-to-coding-type map expected by the probe from parsed transport metadata. */
	public static Map<Integer, StreamCodingType> videoStreamsByPid(M2tsInfo info) {
		Map<Integer, StreamCodingType> videos = new LinkedHashMap<>();
		if (info != null && info.getStreams() != null) {
			for (M2tsStreamInfo stream : info.getStreams()) {
				if (stream.getCodingType() != null && stream.getCodingType().isVideo()) {
					videos.put(stream.getPid(), stream.getCodingType());
				}
			}
		}
		return videos;
	}

}
