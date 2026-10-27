/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/m2ts/PreservationVideoGeometry.java' is part of BRTS.
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

package org.brts.lowlevel.m2ts;

import static org.brts.lowlevel.m2ts.PreservationTransportNormalizer.require;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_context;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avformat.av_find_input_format;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_BT709;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_PRI_UNSPECIFIED;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_MPEG;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_RANGE_UNSPECIFIED;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_BT709;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_SPC_UNSPECIFIED;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_BT709;
import static org.bytedeco.ffmpeg.global.avutil.AVCOL_TRC_UNSPECIFIED;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_free;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_set;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;

/** Bounded native geometry probe; no external process is opened. */
final class PreservationVideoGeometry {
	private PreservationVideoGeometry() {
	}

	record Geometry(int codec, int width, int height, int sarNum, int sarDen, int fieldOrder, int primaries,
			int transfer, int matrix, int range) {
		public String toString() {
			return "Geometry(codec=" + codec + ", width=" + width + ", height=" + height + ", sarNum=" + sarNum
					+ ", sarDen=" + sarDen + ", fieldOrder=" + fieldOrder + ", primaries=" + primaries + ", transfer="
					+ transfer + ", matrix=" + matrix + ", range=" + range + ")";
		}
	}

	/**
	 * A first-header geometry comparison is insufficient if later sequence headers change. Initially permit exactly one
	 * byte-identical SPS (and HEVC VPS) per file, repeated as often as needed. This conservative whole-ES check also
	 * rejects multiple SPS IDs instead of guessing activation.
	 */
	static void validateStableSequence(Path elementary, int streamType) throws IOException {
		Map<Integer, byte[]> headers = new HashMap<>();
		ByteArrayOutputStream header = null;
		int type = -1;
		int zeros = 0;
		boolean start = false;
		boolean needHeader = false;
		try (var input = new BufferedInputStream(Files.newInputStream(elementary))) {
			int value;
			while ((value = input.read()) >= 0) {
				if (value == 0) {
					zeros++;
					continue;
				}
				if (value == 1 && zeros >= 2) {
					if (header != null)
						remember(headers, type, header.toByteArray());
					header = null;
					zeros = 0;
					start = true;
					needHeader = true;
					continue;
				}
				require(start, "Video ES is not Annex B");
				if (needHeader) {
					require(zeros == 0, "Invalid Annex B NAL header");
					type = streamType == 0x1b ? value & 31 : (value >>> 1) & 63;
					require(streamType != 0x1b || type != 13 && type != 14 && type != 15 && type != 20,
							"AVC extension/multiview sequence signaling is unsupported");
					if (streamType == 0x1b ? type == 7 : type == 32 || type == 33)
						header = new ByteArrayOutputStream();
					needHeader = false;
				}
				if (header != null) {
					require(header.size() + (long) zeros < 1024 * 1024,
							"Sequence parameter set exceeds validation bound");
					while (zeros-- > 0)
						header.write(0);
					header.write(value);
				}
				zeros = 0;
			}
			if (header != null)
				remember(headers, type, header.toByteArray());
		}
		require(!needHeader && headers.containsKey(streamType == 0x1b ? 7 : 33)
				&& (streamType == 0x1b || headers.containsKey(32)), "Missing/truncated video sequence headers");
	}

	private static void remember(Map<Integer, byte[]> headers, int type, byte[] bytes) throws IOException {
		byte[] previous = headers.putIfAbsent(type, bytes);
		require(previous == null || Arrays.equals(previous, bytes),
				"Multiple/changing video sequence parameter sets are unsupported");
	}

	static Geometry read(Path path, int pid, int streamType) throws IOException {
		AVFormatContext context = avformat_alloc_context();
		AVDictionary options = new AVDictionary(null);
		try {
			require(context != null && !context.isNull(), "Cannot allocate geometry probe");
			context.probesize(16L * 1024 * 1024).max_analyze_duration(2_000_000).max_probe_packets(256)
					.fps_probe_size(0).max_streams(64);
			require(av_dict_set(options, "protocol_whitelist", "file", 0) >= 0
					&& av_dict_set(options, "codec_whitelist", streamType == 0x1b ? "h264" : "hevc", 0) >= 0,
					"Cannot restrict geometry probe");
			require(avformat_open_input(context, path.toString(), av_find_input_format("mpegts"), options) >= 0,
					"Cannot open M2TS for geometry validation: " + path);
			avformat_find_stream_info(context, (AVDictionary) null);
			for (int i = 0; i < context.nb_streams(); i++) {
				var stream = context.streams(i);
				if (stream.id() != pid)
					continue;
				var p = stream.codecpar();
				require(p.codec_id() == (streamType == 0x1b ? AV_CODEC_ID_H264 : AV_CODEC_ID_HEVC),
						"Video elementary codec disagrees with PMT");
				require(p.width() > 0 && p.height() > 0, "Exact video dimensions cannot be established");
				var sar = p.sample_aspect_ratio();
				if (sar.num() <= 0 || sar.den() <= 0)
					sar = stream.sample_aspect_ratio();
				require(sar.num() > 0 && sar.den() > 0, "Video pixel aspect ratio cannot be established");
				return new Geometry(p.codec_id(), p.width(), p.height(), sar.num(), sar.den(), p.field_order(),
						orDefault(p.color_primaries(), AVCOL_PRI_UNSPECIFIED, AVCOL_PRI_BT709),
						orDefault(p.color_trc(), AVCOL_TRC_UNSPECIFIED, AVCOL_TRC_BT709),
						orDefault(p.color_space(), AVCOL_SPC_UNSPECIFIED, AVCOL_SPC_BT709),
						orDefault(p.color_range(), AVCOL_RANGE_UNSPECIFIED, AVCOL_RANGE_MPEG));
			}
			throw new IOException("Video PID missing from geometry probe: " + pid);
		} catch (LinkageError failure) {
			throw new IOException("Native FFmpeg is required for preservation geometry validation", failure);
		} finally {
			av_dict_free(options);
			if (context != null && !context.isNull())
				avformat_close_input(context);
		}
	}

	// Mirrors VideoConverter's BT.709/limited-range assumptions for unsignaled sources.
	private static int orDefault(int value, int unspecified, int assumed) {
		return value == unspecified ? assumed : value;
	}
}
