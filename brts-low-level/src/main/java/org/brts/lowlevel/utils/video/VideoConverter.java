/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/VideoConverter.java' is part of BRTS.
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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.ProcessUtils;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.VideoConversionResult.Compliance;
import org.brts.lowlevel.utils.video.VideoEncoderAdapter.Settings;

import lombok.extern.slf4j.Slf4j;

/**
 * Converts compressed video elementary-stream files using one selected external executable. Source extraction, audio
 * retiming, multiplexing, and whole-disc certification are outside this utility. Progressive square-pixel targets only.
 * Codec/GOP/VBV/signaling policy is not user-overridable.
 * <p>
 * The default engine is FFmpeg at /usr/bin/ffmpeg. Request values override BrtsFileConfig. No hardware/software
 * fallback or helper executable is launched. Unsupported transformations fail before launching the encoder; achievable
 * but unenforced stream constraints are reported as best-effort warnings.
 * <p>
 * A converter is stateless between invocations, but callers must not mutate requests during prepare/convert or
 * concurrently convert to the same destination. prepare probes input and previews arguments without validating
 * executable availability. convert validates the binary, encodes to a sibling temporary file, checks basic output
 * metadata and only then publishes it. Output probing is NOT a full GOP/VBV/HRD conformance check.
 */
@Slf4j
public final class VideoConverter {

	private static final String EXT_264 = ".264";
	private static final String BT2020NC = "bt2020nc";
	private static final String BT2020 = "bt2020";
	private static final String BT709 = "bt709";
	private static final int MAX_BITRATE = 40000;
	private static final int MAX_BFRAMES = 3;
	public static final String CONFIG_PREFIX = "brts.video.converter.";
	public static final String ENGINE_PROPERTY = CONFIG_PREFIX + "engine";

	@FunctionalInterface
	interface Probe {
		VideoStreamInfo probe(Path path) throws IOException;
	}

	private final BiFunction<String, String, String> configuration;
	private final Probe probe;
	private final EncoderProcessRunner runner;

	public VideoConverter() {
		this(BrtsFileConfig.getInstance());
	}

	public VideoConverter(BrtsFileConfig configuration) {
		this(Objects.requireNonNull(configuration)::propertyOrDefault, VideoStreamProbe::probe,
				new EncoderProcessRunner());
	}

	/** Injectable boundaries for deterministic non-encoding tests. */
	VideoConverter(BiFunction<String, String, String> configuration, Probe probe, EncoderProcessRunner runner) {
		this.configuration = Objects.requireNonNull(configuration);
		this.probe = Objects.requireNonNull(probe);
		this.runner = Objects.requireNonNull(runner);
	}

	public VideoConversionPlan prepare(VideoConversionParams params) throws IOException {
		if (params == null)
			throw new IllegalArgumentException("Video conversion parameters are required");
		Path input = absolute(params.getInputPath(), "inputPath"),
				output = absolute(params.getOutputPath(), "outputPath");
		validatePaths(input, output, params.isOverwrite());
		if (params.getTimeout() != null && (params.getTimeout().isZero() || params.getTimeout().isNegative()))
			throw new IllegalArgumentException("Conversion timeout must be positive or null");
		StreamCodingType codec = params.getCodec() == null ? StreamCodingType.H264_AVC : params.getCodec();
		BluRayVideoConstraints.formats(codec);
		Engine engine = params.getEngine() == null ? Engine.parse(configuration.apply(ENGINE_PROPERTY, "ffmpeg"))
				: params.getEngine();
		String executable = params.getExecutablePath();
		if (executable == null) {
			String fallback = switch (engine) {
			case FFMPEG -> "/usr/bin/ffmpeg";
			case NVENCC -> "/usr/bin/nvencc";
			case VCEENCC -> "/usr/bin/vceencc";
			case QSVENCC -> "/usr/bin/qsvencc";
			};
			executable = configuration.apply(CONFIG_PREFIX + engine.name().toLowerCase(Locale.ROOT) + ".binary",
					fallback);
		}
		if (executable == null || executable.isBlank() || executable.indexOf('\0') >= 0)
			throw new IllegalArgumentException("Encoder executable must not be blank");
		Map<String, String> options;
		try {
			options = params.getEngineOptions() == null ? Map.of() : Map.copyOf(params.getEngineOptions());
		} catch (NullPointerException e) {
			throw new IllegalArgumentException("Engine option keys and values must not be null", e);
		}
		VideoEncoderAdapter.validateOptions(engine, options);
		// Validate rate control before probing native code or starting costly work.
		var rateControl = VideoEncoderAdapter.rateControl(params, engine, options);
		VideoStreamInfo source = probe.probe(input);
		if (!source.progressive())
			throw new IllegalArgumentException(
					"Interlaced source is not supported; no implicit deinterlacing: " + input);
		if (!List.of("h264", "hevc", "mpegvideo", "vc1").contains(source.inputFormat()))
			throw new IllegalArgumentException("Compressed elementary-stream input required: " + source.inputFormat());
		FrameRate sourceRate = params.getSourceFrameRate() != null ? params.getSourceFrameRate() : source.frameRate();
		List<String> warnings = new ArrayList<>();
		if (sourceRate == null) {
			sourceRate = FrameRate.FPS_23976;
			warnings.add("Source timing is absent; using 24000/1001 fps (23.976 fallback)");
		}
		FrameRate targetRate = params.getFrameRate() == null ? sourceRate : params.getFrameRate();
		var geometry = BluRayVideoConstraints.geometry(codec, source, params.getWidth(), params.getHeight(),
				targetRate);
		if (!sourceRate.equals(targetRate))
			warnings.add("Playback speed changes from " + sourceRate + " to " + targetRate
					+ "; separately extracted audio must be adjusted outside VideoConverter");
		validateColors(codec, source, warnings);
		Settings settings = new Settings(input, codec, engine, executable, source, sourceRate, targetRate, geometry,
				params.getBitrateKbps(), rateControl, options, warnings);
		if (codec == StreamCodingType.H265_HEVC && engine == Engine.FFMPEG)
			warnings.add(
					"x265 UHD Blu-ray support is experimental; targeted settings are not conformance certification");
		List<String> command = VideoEncoderAdapter.forEngine(engine).command(settings, output);
		return new VideoConversionPlan(input, output, codec, engine, source, sourceRate, targetRate, geometry.width(),
				geometry.height(), geometry.contentWidth(), geometry.contentHeight(), geometry.left(), geometry.top(),
				geometry.right(), geometry.bottom(), targetRate.nominalGopFrames(), MAX_BFRAMES, MAX_BITRATE,
				settings.bufferKbits(), rateControl, params.isOverwrite(), params.getTimeout(), command, warnings);
	}

	public VideoConversionResult convert(VideoConversionParams params) throws IOException {
		VideoConversionPlan plan = prepare(params);
		String executable = resolveExecutable(plan.command().getFirst());
		Path parent = plan.outputPath().getParent();
		if (!Files.isDirectory(parent))
			throw new IOException("Output parent directory does not exist: " + parent);
		Path temporary = Files.createTempFile(parent, ".brts-video-",
				plan.codec() == StreamCodingType.H264_AVC ? EXT_264 : ".hevc");
		try {
			List<String> command = new ArrayList<>(plan.command());
			command.set(0, executable);
			command.set(command.size() - 1, temporary.toString());
			log.info("Converting {} to {} using {} ({}x{}, {} fps)", plan.inputPath(), plan.outputPath(), plan.engine(),
					plan.width(), plan.height(), plan.frameRate());
			List<String> warnings = new ArrayList<>(plan.warnings());
			warnings.addAll(runner.run(command, plan.timeout()));
			if (!Files.isRegularFile(temporary, LinkOption.NOFOLLOW_LINKS) || Files.size(temporary) == 0)
				throw new IOException("Encoder produced no nonempty regular output: " + temporary);
			try {
				validateOutput(plan, probe.probe(temporary), warnings);
			} catch (IOException e) {
				List<String> reported = warnings.stream().distinct().toList();
				if (reported.isEmpty())
					throw e;
				throw new IOException(e.getMessage() + "\nEncoder warnings:\n- " + String.join("\n- ", reported), e);
			}
			if (Thread.currentThread().isInterrupted())
				throw new IOException("Video conversion interrupted before publishing output");
			validatePaths(plan.inputPath(), plan.outputPath(), plan.overwrite());
			publish(temporary, plan.outputPath(), plan.overwrite());
			warnings = warnings.stream().distinct().toList();
			warnings.forEach(warning -> log.warn("VideoConverter: {}", warning));
			return new VideoConversionResult(plan.outputPath(), plan, warnings,
					warnings.isEmpty() ? Compliance.TARGETED : Compliance.BEST_EFFORT);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void validateColors(StreamCodingType codec, VideoStreamInfo source, List<String> warnings) {
		if (!source.rangeKnown())
			warnings.add("Source range is unknown; assuming limited range. Verify the source before authoring");
		if (source.colorPrimaries() == null || source.transfer() == null || source.colorMatrix() == null) {
			if (source.hdr10() || source.masterDisplay() != null || source.maxCll() != null)
				throw new IllegalArgumentException(
						"HDR input needs complete colorimetry; missing values cannot be inferred safely");
			warnings.add("Missing SDR colorimetry is assumed BT.709; verify source color/range information");
		}
		if (codec == StreamCodingType.H264_AVC) {
			if (source.hdr10() || source.masterDisplay() != null || source.maxCll() != null
					|| !isNullOr(source.colorPrimaries(), BT709) || !isNullOr(source.transfer(), BT709)
					|| !isNullOr(source.colorMatrix(), BT709))
				throw new IllegalArgumentException(
						"AVC targets require BT.709 SDR; color conversion/tone mapping is outside this utility");
		} else {
			if (!isNullOr(source.colorPrimaries(), BT709, BT2020)
					|| !isNullOr(source.transfer(), BT709, "bt2020-10", "smpte2084")
					|| !isNullOr(source.colorMatrix(), BT709, BT2020NC))
				throw new IllegalArgumentException("Unsupported UHD colorimetry (only BT.709/BT.2020 SDR or HDR10)");
			if (source.hdr10() && (!BT2020.equals(source.colorPrimaries()) || !BT2020NC.equals(source.colorMatrix())))
				throw new IllegalArgumentException("HDR10 requires BT.2020 primaries and nonconstant-luminance matrix");
			if (source.hdr10() && source.masterDisplay() == null)
				warnings.add(
						"HDR10 source has no complete static mastering-display metadata; none will be synthesized");
		}
	}

	private static boolean isNullOr(String value, String... accepted) {
		return value == null || List.of(accepted).contains(value);
	}

	private static Path absolute(Path path, String field) {
		if (path == null)
			throw new IllegalArgumentException(field + " is required");
		return path.toAbsolutePath().normalize();
	}

	private static void validatePaths(Path input, Path output, boolean overwrite) throws IOException {
		if (!Files.isRegularFile(input) || !Files.isReadable(input) || Files.size(input) == 0)
			throw new IOException("Input must be a readable nonempty elementary-stream file: " + input);
		if (input.equals(output) || (Files.exists(output) && Files.isSameFile(input, output)))
			throw new IllegalArgumentException("Input and output must be different files");
		if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
			if (!overwrite)
				throw new IOException("Output already exists (overwrite is false): " + output);
			if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS))
				throw new IOException("Output must not be a directory or symbolic link: " + output);
		}
	}

	private static String resolveExecutable(String value) throws IOException {
		Path path = Path.of(value);
		if (!path.isAbsolute() && path.getNameCount() == 1) {
			String resolved = System.getenv("PATH") == null ? null : ProcessUtils.findFullPath(value);
			if (resolved == null)
				throw new IOException("Encoder executable not found on PATH: " + value);
			path = Path.of(resolved);
		}
		if (!Files.isRegularFile(path) || !Files.isExecutable(path))
			throw new IOException("Encoder executable is not a regular executable file: " + path);
		return path.toAbsolutePath().normalize().toString();
	}

	private static void validateOutput(VideoConversionPlan plan, VideoStreamInfo output, List<String> warnings)
			throws IOException {
		boolean hevc = plan.codec() == StreamCodingType.H265_HEVC;
		VideoStreamInfo source = plan.source();
		List<String> mismatches = new ArrayList<>();
		expect(mismatches, "codec", hevc ? "hevc" : "h264", output.inputFormat());
		expect(mismatches, "width", plan.width(), output.width());
		expect(mismatches, "height", plan.height(), output.height());
		expect(mismatches, "progressive", true, output.progressive());
		expect(mismatches, "frameRate", plan.frameRate(), output.frameRate());
		if (output.sarNumerator() != output.sarDenominator())
			mismatches.add("sar expected=1:1 actual=" + output.sarNumerator() + ":" + output.sarDenominator());
		expect(mismatches, "fullRange", false, output.fullRange());
		expect(mismatches, "rangeKnown", true, output.rangeKnown());
		expect(mismatches, "bitDepth", hevc ? 10 : 8, output.bitDepth());
		expect(mismatches, "chromaWidthShift", 1, output.chromaWidthShift());
		expect(mismatches, "chromaHeightShift", 1, output.chromaHeightShift());
		expect(mismatches, "profile", hevc ? 2 : 100, output.profile());
		expect(mismatches, "level", hevc ? 153 : 41, output.level());
		int requiredChromaLocation = BT2020.equals(source.colorPrimaries()) ? 3 : 1;
		// Some encoders (e.g. QSVEncC) omit chroma_loc_info; FFmpeg then infers LEFT (1).
		if (output.chromaLocation() != requiredChromaLocation)
			warnings.add("Output chromaLocation expected=" + requiredChromaLocation + " actual="
					+ output.chromaLocation() + "; chroma siting signaling is missing or differs, verify playback");
		expect(mismatches, "colorPrimaries", source.colorPrimaries() == null ? BT709 : source.colorPrimaries(),
				output.colorPrimaries());
		expect(mismatches, "transfer", source.transfer() == null ? BT709 : source.transfer(), output.transfer());
		expect(mismatches, "colorMatrix", source.colorMatrix() == null ? BT709 : source.colorMatrix(),
				output.colorMatrix());
		if (source.masterDisplay() != null)
			expect(mismatches, "HDR10 masterDisplay", source.masterDisplay(), output.masterDisplay());
		if (source.maxCll() != null)
			expect(mismatches, "HDR10 maxCll", source.maxCll(), output.maxCll());
		if (!mismatches.isEmpty())
			throw new IOException(
					"Encoder output does not match plan: " + String.join(", ", mismatches) + "; probed=" + output);
	}

	private static void expect(List<String> mismatches, String field, Object expected, Object actual) {
		if (!Objects.equals(expected, actual))
			mismatches.add(field + " expected=" + expected + " actual=" + actual);
	}

	private static void publish(Path temporary, Path output, boolean overwrite) throws IOException {
		if (!overwrite) {
			// Do not use ATOMIC_MOVE here: providers may replace an existing destination even without REPLACE_EXISTING.
			Files.move(temporary, output);
		} else {
			try {
				Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
