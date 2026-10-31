/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/VideoEncoderAdapter.java' is part of BRTS.
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

import org.brts.common.video.FrameRate;

import static org.brts.lowlevel.utils.video.VideoEncoderAdapter.args;
import static org.brts.lowlevel.utils.video.VideoEncoderAdapter.nativeOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.utils.video.BluRayVideoConstraints.Geometry;
import org.brts.lowlevel.utils.video.VideoConversionParams.Engine;
import org.brts.lowlevel.utils.video.VideoConversionParams.RateControl;

/** Pure engine-specific command translation. Only a small, validated native option surface is exposed. */
interface VideoEncoderAdapter {

	Set<String> QUALITY_KEYS = Set.of("crf", "cqp", "icq", "la-icq", "qvbr");

	List<String> command(Settings settings, Path output);

	static VideoEncoderAdapter forEngine(Engine engine) {
		return engine == Engine.FFMPEG ? new FfmpegVideoEncoder() : new HardwareVideoEncoder(engine);
	}

	static RateControl rateControl(VideoConversionParams params, Engine engine, Map<String, String> options) {
		List<String> quality = options.keySet().stream().filter(QUALITY_KEYS::contains).toList();
		if (quality.size() > 1 || (params.getBitrateKbps() != null && !quality.isEmpty())
				|| (params.getBitrateKbps() == null && quality.isEmpty()))
			throw new IllegalArgumentException("Supply exactly one positive bitrateKbps or native quality option");
		RateControl mode;
		if (params.getBitrateKbps() != null) {
			if (params.getBitrateKbps() <= 0 || params.getBitrateKbps() > 40_000)
				throw new IllegalArgumentException("bitrateKbps must be in 1..40000 (conservative video peak cap)");
			mode = params.getRateControl() == null ? RateControl.VBR : params.getRateControl();
			if (mode != RateControl.CBR && mode != RateControl.VBR)
				throw new IllegalArgumentException("bitrateKbps requires CBR or VBR rate control");
		} else {
			String key = quality.getFirst();
			mode = RateControl.valueOf(key.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
			if (params.getRateControl() != null && params.getRateControl() != mode)
				throw new IllegalArgumentException("rateControl conflicts with native quality option " + key);
			validateQuality(key, options.get(key));
		}
		Set<RateControl> supported = switch (engine) {
		case FFMPEG -> Set.of(RateControl.CBR, RateControl.VBR, RateControl.CRF);
		case NVENCC, VCEENCC -> Set.of(RateControl.CBR, RateControl.VBR, RateControl.CQP, RateControl.QVBR);
		case QSVENCC -> Set.of(RateControl.CBR, RateControl.VBR, RateControl.CQP, RateControl.ICQ, RateControl.LA_ICQ);
		};
		if (!supported.contains(mode))
			throw new IllegalArgumentException(engine + " does not support native " + mode + " in VideoConverter");
		if (engine == Engine.VCEENCC && params.getCodec() == StreamCodingType.H265_HEVC && mode == RateControl.QVBR)
			throw new IllegalArgumentException("VCEEncC QVBR is not documented for HEVC; use bitrate or CQP");
		return mode;
	}

	private static void validateQuality(String key, String value) {
		try {
			String[] values = value.split(":", -1);
			if (values.length != 1 && !(key.equals("cqp") && values.length == 3))
				throw new IllegalArgumentException();
			for (String part : values) {
				double number = Double.parseDouble(part);
				double minimum = key.contains("icq") || key.equals("qvbr") ? 1 : 0;
				double maximum = 51;
				if (!Double.isFinite(number) || number < minimum || number > maximum
						|| (!key.equals("crf") && !key.equals("qvbr") && number != Math.rint(number)))
					throw new IllegalArgumentException();
			}
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Invalid native quality value for " + key + ": " + value, e);
		}
	}

	static void validateOptions(Engine engine, Map<String, String> options) {
		Set<String> allowed = switch (engine) {
		case FFMPEG -> Set.of("crf", "preset", "threads");
		case NVENCC -> Set.of("cqp", "qvbr", "preset", "device");
		case VCEENCC -> Set.of("cqp", "qvbr", "preset", "device");
		case QSVENCC -> Set.of("cqp", "icq", "la-icq", "quality", "device");
		};
		for (var entry : options.entrySet()) {
			String key = entry.getKey(), value = entry.getValue();
			if (key == null || !allowed.contains(key) || value == null || value.isBlank() || value.indexOf('\0') >= 0
					|| value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
				throw new IllegalArgumentException("Unsupported/protected " + engine + " option: " + key);
			if (QUALITY_KEYS.contains(key))
				continue;
			if (key.equals("device") || key.equals("threads")) {
				if (!value.matches("[0-9]+") || Long.parseLong(value) > 1024
						|| key.equals("threads") && Integer.parseInt(value) == 0)
					throw new IllegalArgumentException("Invalid " + key + ": " + value);
			} else {
				Set<String> presets = switch (engine) {
				case FFMPEG -> Set.of("ultrafast", "superfast", "veryfast", "faster", "fast", "medium", "slow",
						"slower", "veryslow");
				case NVENCC -> Set.of("default", "performance", "quality", "P1", "P2", "P3", "P4", "P5", "P6", "P7");
				case VCEENCC -> Set.of("balanced", "fast", "slow", "slower");
				case QSVENCC -> Set.of("best", "higher", "high", "balanced", "fast", "faster", "fastest");
				};
				if (!presets.contains(value))
					throw new IllegalArgumentException("Unsupported " + engine + " " + key + ": " + value);
			}
		}
	}

	record Settings(Path input, StreamCodingType codec, Engine engine, String executable, VideoStreamInfo source,
			FrameRate sourceRate, FrameRate targetRate, Geometry geometry, Integer bitrate, RateControl rateControl,
			Map<String, String> options, List<String> warnings) {
		boolean hevc() {
			return codec == StreamCodingType.H265_HEVC;
		}

		int bufferKbits() {
			return hevc() ? 40_000 : 30_000;
		}

		String primaries() {
			return source.colorPrimaries() == null ? "bt709" : source.colorPrimaries();
		}

		String transfer() {
			return source.transfer() == null ? "bt709" : source.transfer();
		}

		String matrix() {
			return source.colorMatrix() == null ? "bt709" : source.colorMatrix();
		}

		int chromaPosition() {
			return primaries().equals("bt2020") ? 0 : 128;
		}
	}

	static void args(List<String> command, String... args) {
		command.addAll(List.of(args));
	}

	static void nativeOptions(List<String> command, Settings s, String prefix) {
		new TreeMap<>(s.options()).forEach((key, value) -> args(command, prefix + key, value));
	}
}

final class FfmpegVideoEncoder implements VideoEncoderAdapter {
	@Override
	public List<String> command(Settings s, Path output) {
		List<String> command = new ArrayList<>();
		Geometry g = s.geometry();
		args(command, s.executable(), "-hide_banner", "-nostdin", "-y", "-loglevel", "info", "-r",
				s.targetRate().toString(), "-f", s.source().inputFormat(), "-i", s.input().toString(), "-map", "0:v:0",
				"-an", "-sn", "-dn");
		// Input -r synthesizes one timestamp per decoded frame, changing speed without an fps filter.
		String filter = "scale=" + g.contentWidth() + ":" + g.contentHeight() + ":flags=lanczos:in_range="
				+ (s.source().fullRange() ? "pc" : "tv") + ":out_range=tv:out_h_chr_pos=0:out_v_chr_pos="
				+ s.chromaPosition() + ",setsar=1,pad=" + g.width() + ":" + g.height() + ":" + g.left() + ":" + g.top()
				+ ":color=black";
		args(command, "-vf", filter, "-vsync", "0", "-enc_time_base",
				s.targetRate().denominator() + "/" + s.targetRate().numerator(), "-c:v",
				s.hevc() ? "libx265" : "libx264");
		nativeOptions(command, s, "-");
		args(command, "-pix_fmt", s.hevc() ? "yuv420p10le" : "yuv420p", "-profile:v", s.hevc() ? "main10" : "high",
				"-color_primaries", s.primaries(), "-color_trc", s.transfer(), "-colorspace", s.matrix(),
				"-color_range", "tv", "-maxrate", "40000k", "-bufsize", s.bufferKbits() + "k");
		if (s.bitrate() != null) {
			args(command, "-b:v", s.bitrate() + "k");
			if (s.rateControl() == RateControl.CBR)
				args(command, "-minrate", s.bitrate() + "k", "-maxrate", s.bitrate() + "k");
		}
		int gop = s.targetRate().nominalGopFrames();
		String parameters;
		if (s.hevc()) {
			// uhd-bd sets min-keyint=1; scenecut=0 and gop-lookahead=0 keep a fixed nominal GOP.
			parameters = "uhd-bd=1:level-idc=5.1:high-tier=1:open-gop=0:keyint=" + gop
					+ ":min-keyint=1:scenecut=0:gop-lookahead=0:bframes=3:b-adapt=0:ref=3:b-pyramid=0" + ":vbv-maxrate="
					+ (s.rateControl() == RateControl.CBR ? s.bitrate() : 40000)
					+ ":vbv-bufsize=40000:hrd=1:aud=1:repeat-headers=1:annexb=1:interlace=0:pic-struct=0"
					+ ":colorprim=" + s.primaries() + ":transfer=" + s.transfer() + ":colormatrix=" + s.matrix()
					+ ":range=limited:chromaloc=" + (s.primaries().equals("bt2020") ? 2 : 0);
			if (s.source().masterDisplay() != null)
				parameters += ":master-display=" + s.source().masterDisplay();
			if (s.source().maxCll() != null)
				parameters += ":max-cll=" + s.source().maxCll();
			if (s.source().hdr10())
				parameters += ":hdr10=1";
			args(command, "-x265-params", parameters);
		} else {
			parameters = "bluray-compat=1:level=4.1:force-cfr=1:open-gop=0:keyint=" + gop + ":min-keyint=" + gop
					+ ":scenecut=0:bframes=3:b-adapt=0:b-pyramid=strict:ref=3:slices=4" + ":vbv-maxrate="
					+ (s.rateControl() == RateControl.CBR ? s.bitrate() : 40000) + ":vbv-bufsize=30000:nal-hrd="
					+ (s.rateControl() == RateControl.CBR ? "cbr" : "vbr") + ":aud=1:repeat-headers=1:interlaced=0";
			args(command, "-level:v", "4.1", "-x264-params", parameters);
		}
		args(command, "-f", s.hevc() ? "hevc" : "h264", output.toString());
		return List.copyOf(command);
	}
}

/** Rigaya adapters share native geometry/IO switches; differences remain explicit in the engine branch. */
final class HardwareVideoEncoder implements VideoEncoderAdapter {
	private final Engine engine;

	HardwareVideoEncoder(Engine engine) {
		this.engine = engine;
	}

	@Override
	public List<String> command(Settings s, Path output) {
		if (s.source().frameRate() == null || !s.sourceRate().equals(s.source().frameRate())
				|| !s.targetRate().equals(s.sourceRate()))
			throw new IllegalArgumentException(engine + " cannot reliably retime compressed ES with --fps; "
					+ "source timing must be present and retained (no helper executable is permitted)");
		int requiredChromaLocation = s.primaries().equals("bt2020") ? 3 : 1;
		if (s.source().chromaLocation() > 0 && s.source().chromaLocation() != requiredChromaLocation)
			throw new IllegalArgumentException(engine + " cannot reliably resample source chroma location; use FFmpeg");
		if (s.source().chromaLocation() < 1)
			s.warnings().add(engine + " source chroma location is unknown; native default placement is assumed");
		if (engine == Engine.VCEENCC && s.rateControl() == RateControl.QVBR
				&& !s.options().get("qvbr").matches("[0-9]+"))
			throw new IllegalArgumentException("VCEEncC qvbr quality must be an integer");
		if (s.geometry().contentWidth() != s.source().width() || s.geometry().contentHeight() != s.source().height())
			s.warnings().add(engine + " resize/padding requires the native GPU VPP filters in the installed build");
		// No software fallback, helper FFmpeg or backend auto-fallback in these adapters.
		List<String> command = new ArrayList<>();
		args(command, s.executable(), "--avsw", "--input-format", s.source().inputFormat(), "-i", s.input().toString());
		args(command, "--codec", s.hevc() ? "hevc" : "h264");
		if (s.bitrate() != null)
			args(command, s.rateControl() == RateControl.CBR ? "--cbr" : "--vbr", s.bitrate().toString());
		nativeOptions(command, s, "--");
		boolean unboundedQuality = Set.of(RateControl.CQP, RateControl.ICQ, RateControl.LA_ICQ)
				.contains(s.rateControl());
		if (unboundedQuality)
			s.warnings().add(engine + " " + s.rateControl()
					+ " cannot enforce Blu-ray VBV/peak-bitrate limits; output is best-effort");
		else
			args(command, "--max-bitrate", s.rateControl() == RateControl.CBR ? s.bitrate().toString() : "40000",
					"--vbv-bufsize", Integer.toString(s.bufferKbits()));
		args(command, "--profile", s.hevc() ? "main10" : "high", "--level", s.hevc() ? "5.1" : "4.1", "--output-depth",
				s.hevc() ? "10" : "8", "--gop-len", Integer.toString(s.targetRate().nominalGopFrames()), "--bframes",
				"3", "--ref", "3", "--slices", "4", "--aud", "--repeat-headers", "--sar", "1:1", "--colorrange",
				"limited", "--colorprim", s.primaries(), "--transfer", s.transfer(), "--colormatrix", s.matrix(),
				"--chromaloc", s.primaries().equals("bt2020") ? "2" : "0");
		if (s.hevc()) {
			args(command, "--tier", "high");
			s.warnings().add(engine + " has no documented UHD Blu-ray compatibility mode; HEVC output is best-effort");
		} else if (engine != Engine.VCEENCC && !unboundedQuality)
			args(command, "--bluray");
		if (engine == Engine.NVENCC)
			args(command, "--strict-gop", "--no-i-adapt", "--no-b-adapt", "--bref-mode", "disabled", "--pic-struct",
					"--output-csp", "yuv420");
		else if (engine == Engine.QSVENCC)
			args(command, "--no-fallback-rc", "--strict-gop", "--pic-struct", "--buf-period", "--output-csp", "i420");
		else {
			if (!unboundedQuality)
				args(command, "--enforce-hrd");
			s.warnings()
					.add("VCEEncC has no documented Blu-ray compatibility mode; stream constraints are best-effort");
		}
		if (s.source().sarNumerator() != s.source().sarDenominator())
			throw new IllegalArgumentException(
					engine + " anamorphic scaling is not supported in this version; use FFmpeg");
		Geometry g = s.geometry();
		if (g.width() != s.source().width() || g.height() != s.source().height())
			args(command, "--output-res", g.width() + "x" + g.height());
		if (g.left() + g.top() + g.right() + g.bottom() > 0)
			args(command, "--vpp-pad", g.left() + "," + g.top() + "," + g.right() + "," + g.bottom());
		if (s.source().fullRange())
			args(command, "--vpp-colorspace", "range=full:limited");
		if (s.source().masterDisplay() != null)
			args(command, "--master-display", s.source().masterDisplay());
		if (s.source().maxCll() != null)
			args(command, "--max-cll", s.source().maxCll());
		args(command, "--output-format", "raw", "-o", output.toString());
		s.warnings().add(engine
				+ " hardware may adjust requested GOP/B-frame/reference/signaling options; inspect the resulting bitstream");
		return List.copyOf(command);
	}
}
