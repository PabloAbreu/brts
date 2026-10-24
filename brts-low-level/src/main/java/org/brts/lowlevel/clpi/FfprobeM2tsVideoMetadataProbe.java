/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/clpi/FfprobeM2tsVideoMetadataProbe.java' is part of BRTS.
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */
package org.brts.lowlevel.clpi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.ProcessUtils;
import org.brts.lowlevel.clpi.M2tsVideoMetadataProbe.Metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * First-frame M2TS video metadata snapshot obtained from a native ffprobe executable (no shell, no JNI). Only the first
 * {@code packets} video packets are read; missing evidence remains null, as in {@link M2tsVideoMetadataProbe}.
 */
final class FfprobeM2tsVideoMetadataProbe {

	static final String BINARY_PROPERTY = "brts.ffprobe.binary";
	static final String PACKETS_PROPERTY = "brts.clpi.probe.ffprobe.packets";
	static final String TIMEOUT_PROPERTY = "brts.clpi.probe.ffprobe.timeout.ms";
	static final int DEFAULT_PACKETS = 64;
	static final long DEFAULT_TIMEOUT_MS = 60_000;
	private static final int MAX_STDOUT_BYTES = 16 * 1024 * 1024;
	private static final int MAX_STDERR_BYTES = 8 * 1024;
	private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(2);

	private final String binary;
	private final int packets;
	private final Duration timeout;

	FfprobeM2tsVideoMetadataProbe(String binary, int packets, Duration timeout) {
		if (packets <= 0) {
			throw new IllegalArgumentException(PACKETS_PROPERTY + " must be positive: " + packets);
		}
		if (timeout == null || timeout.isZero() || timeout.isNegative()) {
			throw new IllegalArgumentException(TIMEOUT_PROPERTY + " must be positive: " + timeout);
		}
		this.binary = binary;
		this.packets = packets;
		this.timeout = timeout;
	}

	/** Returns null when the configured (or PATH) ffprobe executable is unavailable. */
	static FfprobeM2tsVideoMetadataProbe fromConfig(UnaryOperator<String> config) {
		String binary = resolveBinary(config.apply(BINARY_PROPERTY));
		if (binary == null) {
			return null;
		}
		return new FfprobeM2tsVideoMetadataProbe(binary, (int) longProperty(config, PACKETS_PROPERTY, DEFAULT_PACKETS),
				Duration.ofMillis(longProperty(config, TIMEOUT_PROPERTY, DEFAULT_TIMEOUT_MS)));
	}

	static String resolveBinary(String configured) {
		String value = configured == null || configured.isBlank() ? "ffprobe" : configured.trim();
		if (value.indexOf('\0') >= 0) {
			return null;
		}
		Path path = Path.of(value);
		if (!path.isAbsolute() && path.getNameCount() == 1) {
			String found = System.getenv("PATH") == null ? null : ProcessUtils.findFullPath(value);
			if (found == null) {
				return null;
			}
			path = Path.of(found);
		}
		return Files.isRegularFile(path) && Files.isExecutable(path) ? path.toAbsolutePath().normalize().toString()
				: null;
	}

	private static long longProperty(UnaryOperator<String> config, String key, long fallback) {
		String value = config.apply(key);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		try {
			long parsed = Long.parseLong(value.trim());
			if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
				throw new IllegalArgumentException(key + " must be a positive integer: " + value);
			}
			return parsed;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(key + " must be a positive integer: " + value, e);
		}
	}

	Map<Integer, Metadata> probe(Path path, Map<Integer, StreamCodingType> expectedVideos) throws IOException {
		if (expectedVideos.isEmpty()) {
			return Map.of();
		}
		if (path == null || !Files.isRegularFile(path)) {
			throw new IOException("ffprobe metadata probe requires a regular local file: " + path);
		}
		return parse(run(command(path), path), expectedVideos);
	}

	List<String> command(Path path) {
		return List.of(binary, "-v", "error", "-hide_banner", "-f", "mpegts", "-select_streams", "v", "-read_intervals",
				"%+#" + packets, "-show_streams", "-show_frames", "-show_entries",
				"stream=index,id,codec_name,width,height,field_order,color_transfer,color_primaries"
						+ ":stream_side_data_list"
						+ ":frame=stream_index,width,height,interlaced_frame,color_transfer,color_primaries"
						+ ":frame_side_data_list",
				"-of", "json", path.toAbsolutePath().toString());
	}

	private String run(List<String> command, Path path) throws IOException {
		Process process = new ProcessBuilder(command).start();
		try {
			process.getOutputStream().close();
			Capture stdout = new Capture(process.getInputStream(), MAX_STDOUT_BYTES);
			Capture stderr = new Capture(process.getErrorStream(), MAX_STDERR_BYTES);
			Thread out = Thread.ofVirtual().name("brts-ffprobe-stdout").start(stdout);
			Thread err = Thread.ofVirtual().name("brts-ffprobe-stderr").start(stderr);
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new IOException("ffprobe timed out after " + timeout + " for " + path);
			}
			if (!out.join(DRAIN_TIMEOUT) || !err.join(DRAIN_TIMEOUT)) {
				throw new IOException("ffprobe output was not drained in time for " + path);
			}
			if (process.exitValue() != 0) {
				throw new IOException("ffprobe exited with status " + process.exitValue() + " for " + path + ": "
						+ stderr.text().strip());
			}
			if (stdout.failure != null) {
				throw new IOException("Cannot read ffprobe output for " + path, stdout.failure);
			}
			if (stdout.truncated) {
				throw new IOException("ffprobe output exceeds " + MAX_STDOUT_BYTES + " bytes for " + path);
			}
			return stdout.text();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Interrupted while running ffprobe for " + path);
		} finally {
			if (process.isAlive()) {
				process.destroyForcibly();
			}
		}
	}

	static Map<Integer, Metadata> parse(String json, Map<Integer, StreamCodingType> expectedVideos) throws IOException {
		JsonNode root = JsonMapperFactory.get().readTree(json);
		Map<Integer, JsonNode> firstFrames = new HashMap<>();
		for (JsonNode frame : root.path("frames")) {
			firstFrames.putIfAbsent(frame.path("stream_index").asInt(-1), frame);
		}
		Map<Integer, Metadata> result = new LinkedHashMap<>();
		for (JsonNode stream : root.path("streams")) {
			Integer pid = pid(text(stream, "id"));
			if (pid == null || result.containsKey(pid)
					|| !matchesCodec(expectedVideos.get(pid), text(stream, "codec_name"))) {
				continue;
			}
			JsonNode frame = firstFrames.getOrDefault(stream.path("index").asInt(-1), MissingNode.getInstance());
			int width = positive(frame, "width", stream);
			int height = positive(frame, "height", stream);
			boolean dolbyVision = hasSideData(stream, "dovi", "dolby vision")
					|| hasSideData(frame, "dovi", "dolby vision");
			boolean hdrPlus = hasSideData(stream, "2094-40", "hdr10+") || hasSideData(frame, "2094-40", "hdr10+");
			result.put(pid,
					new Metadata(M2tsVideoMetadataProbe.videoFormat(width, height, progressive(frame, stream)),
							M2tsVideoMetadataProbe.fourK(width, height),
							dynamicRange(known(frame, stream, "color_transfer"), dolbyVision),
							colorSpace(known(frame, stream, "color_primaries")), hdrPlus ? Boolean.TRUE : null));
		}
		return result;
	}

	static Integer dynamicRange(String transfer, boolean dolbyVision) {
		if (dolbyVision) {
			return 2;
		}
		if (transfer == null) {
			return null;
		}
		return switch (transfer) {
		case "smpte2084" -> 1;
		case "bt709", "bt470m", "bt470bg", "smpte170m", "smpte240m", "linear", "log100", "log316", "iec61966-2-4",
				"bt1361e", "iec61966-2-1", "bt2020-10", "bt2020-12" ->
			0;
		default -> null; // HLG (arib-std-b67) and unrecognized transfer functions are not guessed.
		};
	}

	static Integer colorSpace(String primaries) {
		if (primaries == null) {
			return null;
		}
		return switch (primaries) {
		case "bt709" -> 1;
		case "bt2020" -> 2;
		default -> null;
		};
	}

	static boolean matchesCodec(StreamCodingType expected, String codec) {
		if (expected == null || codec == null) {
			return false;
		}
		return switch (expected) {
		case MPEG2_VIDEO -> codec.equals("mpeg2video");
		case H264_AVC -> codec.equals("h264");
		case H265_HEVC -> codec.equals("hevc");
		case VC1 -> codec.equals("vc1");
		default -> false;
		};
	}

	/** Mirrors the library probe: a decoded frame is progressive only if not interlaced and not field-ordered. */
	static Boolean progressive(JsonNode frame, JsonNode stream) {
		String fieldOrder = text(stream, "field_order");
		Boolean streamProgressive = fieldOrder == null || fieldOrder.equals("unknown") ? null
				: fieldOrder.equals("progressive");
		JsonNode interlaced = frame.path("interlaced_frame");
		if (interlaced.isNumber()) {
			return interlaced.asInt() == 0 && !Boolean.FALSE.equals(streamProgressive);
		}
		return streamProgressive;
	}

	private static Integer pid(String id) {
		if (id == null) {
			return null;
		}
		try {
			return id.startsWith("0x") || id.startsWith("0X") ? Integer.parseInt(id.substring(2), 16)
					: Integer.parseInt(id);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int positive(JsonNode frame, String field, JsonNode stream) {
		int value = frame.path(field).asInt(0);
		return value > 0 ? value : Math.max(0, stream.path(field).asInt(0));
	}

	private static String known(JsonNode frame, JsonNode stream, String field) {
		String value = text(frame, field);
		if (value == null || value.equals("unknown") || value.equals("unspecified")) {
			value = text(stream, field);
		}
		return value;
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isValueNode() && !value.isNull() ? value.asText() : null;
	}

	/** Side-data names differ between FFmpeg releases, hence the case-insensitive substring match. */
	private static boolean hasSideData(JsonNode node, String... markers) {
		for (JsonNode side : node.path("side_data_list")) {
			String type = text(side, "side_data_type");
			if (type != null) {
				String lower = type.toLowerCase(Locale.ROOT);
				for (String marker : markers) {
					if (lower.contains(marker)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Keeps draining past the limit so the child never blocks on a full pipe. */
	private static final class Capture implements Runnable {
		private final InputStream input;
		private final int limit;
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private volatile boolean truncated;
		private volatile IOException failure;

		Capture(InputStream input, int limit) {
			this.input = input;
			this.limit = limit;
		}

		@Override
		public void run() {
			try (InputStream stream = input) {
				byte[] chunk = new byte[8192];
				int count;
				while ((count = stream.read(chunk)) != -1) {
					int keep = Math.min(count, limit - bytes.size());
					if (keep > 0) {
						bytes.write(chunk, 0, keep);
					}
					if (keep < count) {
						truncated = true;
					}
				}
			} catch (IOException e) {
				failure = e;
			}
		}

		String text() {
			return bytes.toString(StandardCharsets.UTF_8);
		}
	}
}
