/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-cli/src/main/java/org/brts/cli/low/VideoConvertCli.java' is part of BRTS.
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

package org.brts.cli.low;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.brts.cli.BaseOptions;
import org.brts.cli.FeatureRunner;
import org.brts.common.model.StreamCodingType;
import org.brts.common.video.FrameRate;
import org.brts.lowlevel.utils.video.VideoConversionParams;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.VideoConversionParams.RateControl;
import org.brts.lowlevel.utils.video.VideoConversionResult;
import org.brts.lowlevel.utils.video.VideoConverter;
import org.kohsuke.args4j.Option;

/** CLI entry point for compressed elementary-video conversion, without extraction, audio retiming or muxing. */
public class VideoConvertCli {

	/** Conversion arguments; omitted optional settings retain the converter's source/configuration defaults. */
	public static class ConvertOptions extends BaseOptions {
		/** Compressed elementary-video input, not a container or raw pixel file. */
		@Option(name = "--input", required = true, metaVar = "FILE", usage = "Compressed elementary-video input file")
		File input;

		/** Destination elementary-stream file. */
		@Option(name = "--output", required = true, metaVar = "FILE", usage = "Output AVC/HEVC elementary-stream file")
		File output;

		/** Target codec; defaults to AVC. */
		@Option(name = "--codec", metaVar = "CODEC", usage = "H264_AVC (default) or H265_HEVC (UHD target)")
		String codec;

		/** Explicit engine override, otherwise selected by configuration. */
		@Option(name = "--engine", metaVar = "ENGINE", usage = "FFMPEG, NVENCC, VCEENCC or QSVENCC (default: configuration, then FFMPEG)")
		String engine;

		/** Selected encoder executable, not a shell command. */
		@Option(name = "--executable", metaVar = "PATH", usage = "Encoder executable path or name on PATH (default: engine configuration)")
		String executable;

		/** Optional target width, paired with height. */
		@Option(name = "--width", metaVar = "PIXELS", usage = "Target width; supply together with --height")
		Integer width;

		/** Optional target height, paired with width. */
		@Option(name = "--height", metaVar = "PIXELS", usage = "Target height; supply together with --width")
		Integer height;

		/** Requested playback rate; changing it also changes playback speed. */
		@Option(name = "--frame-rate", metaVar = "FPS", usage = "Target fps, e.g. 24000/1001 or 23.976; speed changes require separate audio retiming")
		String frameRate;

		/** Source timing override, distinct from requested playback speed. */
		@Option(name = "--source-frame-rate", metaVar = "FPS", usage = "Source timing override, e.g. 24000/1001 (not the target rate)")
		String sourceFrameRate;

		/** Target bitrate, mutually exclusive with native quality options. */
		@Option(name = "--bitrate-kbps", metaVar = "KBPS", usage = "Target bitrate in 1..40000; supply this OR one native quality option")
		Integer bitrateKbps;

		/** Optional explicit rate-control mode, subject to engine support. */
		@Option(name = "--rate-control", metaVar = "MODE", usage = "VBR, CBR, CRF, CQP, ICQ, LA_ICQ or QVBR (default: VBR with bitrate, otherwise native quality mode)")
		String rateControl;

		/** Repeatable generic native options; validation belongs to VideoConverter. */
		@Option(name = "--engine-option", metaVar = "KEY=VALUE", usage = "Repeat for each converter-approved native option, e.g. crf=20, preset=slow; no leading dashes or duplicate keys")
		List<String> engineOptions = new ArrayList<>();

		/** Permit replacing an existing regular output file after successful conversion. */
		@Option(name = "--overwrite", usage = "Replace existing output after successful conversion")
		boolean overwrite;

		/** Optional positive whole-second conversion limit. */
		@Option(name = "--timeout-seconds", metaVar = "SECONDS", usage = "Positive whole-second conversion timeout (default: no limit)")
		Long timeoutSeconds;
	}

	@FunctionalInterface
	interface Conversion {
		VideoConversionResult convert(VideoConversionParams params) throws IOException;
	}

	/** Converts one elementary-video file using the selected external encoder. */
	public static class Convert extends FeatureRunner<ConvertOptions> {
		private final Conversion conversion;

		public Convert() {
			this(params -> new VideoConverter().convert(params));
		}

		Convert(Conversion conversion) {
			this.conversion = Objects.requireNonNull(conversion);
		}

		@Override
		public String getCommandName() {
			return "video-convert";
		}

		@Override
		public String getDescription() {
			return "Convert compressed elementary video to AVC/HEVC using FFmpeg or hardware encoders";
		}

		@Override
		protected void execute(ConvertOptions opts) throws IOException {
			VideoConversionResult result = conversion.convert(toParams(opts));
			System.out.println("Video conversion complete → " + result.outputPath() + " (" + result.compliance() + ")");
		}
	}

	/** Maps arguments without probing files or launching native executables. */
	static VideoConversionParams toParams(ConvertOptions opts) {
		VideoConversionParams params = new VideoConversionParams();
		params.setInputPath(opts.input.toPath());
		params.setOutputPath(opts.output.toPath());
		if (opts.codec != null) {
			try {
				StreamCodingType codec = StreamCodingType.valueOf(opts.codec.trim().toUpperCase(Locale.ROOT));
				if (codec != StreamCodingType.H264_AVC && codec != StreamCodingType.H265_HEVC)
					throw new IllegalArgumentException();
				params.setCodec(codec);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid --codec: " + opts.codec + "; use H264_AVC or H265_HEVC", e);
			}
		}
		if (opts.engine != null)
			params.setEngine(Engine.parse(opts.engine));
		params.setExecutablePath(opts.executable);
		params.setWidth(opts.width);
		params.setHeight(opts.height);
		params.setFrameRate(parseFrameRate("--frame-rate", opts.frameRate));
		params.setSourceFrameRate(parseFrameRate("--source-frame-rate", opts.sourceFrameRate));
		params.setBitrateKbps(opts.bitrateKbps);
		if (opts.rateControl != null) {
			try {
				params.setRateControl(
						RateControl.valueOf(opts.rateControl.trim().replace('-', '_').toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid --rate-control: " + opts.rateControl, e);
			}
		}
		params.setEngineOptions(parseEngineOptions(opts.engineOptions));
		params.setOverwrite(opts.overwrite);
		if (opts.timeoutSeconds != null) {
			if (opts.timeoutSeconds <= 0)
				throw new IllegalArgumentException("--timeout-seconds must be positive");
			params.setTimeout(Duration.ofSeconds(opts.timeoutSeconds));
		}
		return params;
	}

	private static FrameRate parseFrameRate(String argument, String value) {
		if (value == null)
			return null;
		try {
			return FrameRate.parse(value);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid " + argument + ": " + value, e);
		}
	}

	/** Splits only at the first equals sign; values are single tokens and are never shell-parsed. */
	static Map<String, String> parseEngineOptions(List<String> arguments) {
		Map<String, String> options = new LinkedHashMap<>();
		for (String argument : arguments) {
			int separator = argument.indexOf('=');
			if (separator <= 0 || argument.substring(0, separator).isBlank()
					|| argument.substring(0, separator).startsWith("-") || argument.substring(separator + 1).isBlank())
				throw new IllegalArgumentException("Invalid --engine-option: " + argument
						+ "; expected KEY=VALUE with a nonblank name/value and no leading dashes");
			String key = argument.substring(0, separator), value = argument.substring(separator + 1);
			if (options.putIfAbsent(key, value) != null)
				throw new IllegalArgumentException("Duplicate --engine-option key: " + key);
		}
		return options;
	}
}
