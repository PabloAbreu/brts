/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/clpi/M2tsVideoMetadataProbe.java' is part of BRTS.
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

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.brts.common.model.StreamCodingType;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avcodec.AVPacketSideData;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;

/**
 * Local M2TS first-frame metadata snapshot, not a whole-stream validator. The non-seekable custom AVIO shares a 16 MiB
 * budget across opening, analysis and decoding; at most 512 packets are read after analysis. No elementary streams,
 * subprocesses or network protocols are used. Missing evidence remains null, including HDR10+ absence. OC/CR are
 * deliberately not inferred. Native loading errors are deliberately not caught.
 */
public class M2tsVideoMetadataProbe {

	static final long MAX_INPUT_BYTES = 16L * 1024 * 1024;
	static final int MAX_PACKETS = 512;
	private static final int IO_BUFFER_SIZE = 32 * 1024;
	private static final long MAX_PIXELS = 8192L * 4320;

	public record Metadata(Integer videoFormat, boolean fourK, Integer dynamicRangeType, Integer colorSpace,
			Boolean hdrPlusFlag) {
	}

	public Map<Integer, Metadata> probe(Path path, Map<Integer, StreamCodingType> expectedVideos) throws IOException {
		if (expectedVideos.isEmpty()) {
			return Map.of();
		}
		if (path == null || !Files.isRegularFile(path)) {
			throw new IOException("M2TS metadata probe requires a regular local file: " + path);
		}
		AVFormatContext format = null;
		AVIOContext io = null;
		BytePointer buffer = null;
		AVPacket packet = null;
		AVFrame frame = null;
		Map<Integer, Video> videos = new LinkedHashMap<>();
		try (InputStream input = Files.newInputStream(path); BoundedReader reader = new BoundedReader(input)) {
			try {
				buffer = new BytePointer(av_malloc(IO_BUFFER_SIZE));
				if (buffer.isNull()) {
					throw new IOException("Cannot allocate M2TS probe I/O buffer: " + path);
				}
				io = avio_alloc_context(buffer, IO_BUFFER_SIZE, 0, null, reader, null, null);
				if (io == null || io.isNull()) {
					throw new IOException("Cannot allocate M2TS probe I/O context: " + path);
				}
				buffer = null; // AVIO owns (and may replace) the buffer now.
				format = avformat_alloc_context();
				if (format == null || format.isNull()) {
					throw new IOException("Cannot allocate M2TS probe format context: " + path);
				}
				format.pb(io).flags(format.flags() | AVFormatContext.AVFMT_FLAG_CUSTOM_IO);
				format.probesize(2L * 1024 * 1024).max_analyze_duration(2_000_000).max_probe_packets(64)
						.fps_probe_size(0).max_streams(64);
				// Stream analysis must not open implicit audio/video decoders without our thread/pixel limits.
				// Parsers still discover headers; only the explicitly bounded per-PID decoders below decode pictures.
				check(av_opt_set(format, "codec_whitelist", "none", 0), path, "disable analysis decoders");
				check(avformat_open_input(format, path.toAbsolutePath().toString(), av_find_input_format("mpegts"),
						(AVDictionary) null), path, "open MPEG-TS input");
				// Incomplete analysis (including budget exhaustion) does not discard already discovered PIDs.
				avformat_find_stream_info(format, (AVDictionary) null);
				if (format.nb_streams() == 0) {
					throw new IOException("No MPEG-TS streams found within bounded metadata probe: " + path,
							reader.failure);
				}
				for (int i = 0; i < format.nb_streams(); i++) {
					var stream = format.streams(i);
					AVCodecParameters parameters = stream.codecpar();
					StreamCodingType expected = expectedVideos.get(stream.id());
					if (parameters.codec_type() == AVMEDIA_TYPE_VIDEO && matchesCodec(expected, parameters.codec_id())
							&& videos.values().stream().noneMatch(video -> video.pid == stream.id())) {
						Video video = new Video(stream.id(), parameters);
						videos.put(i, video);
						video.open();
					}
				}
				packet = av_packet_alloc();
				frame = av_frame_alloc();
				if (packet == null || packet.isNull() || frame == null || frame.isNull()) {
					throw new IOException("Cannot allocate M2TS probe packet/frame: " + path);
				}
				for (int i = 0; i < MAX_PACKETS && videos.values().stream().anyMatch(video -> !video.done); i++) {
					if (av_read_frame(format, packet) < 0) {
						break;
					}
					try {
						Video video = videos.get(packet.stream_index());
						if (video != null && !video.done) {
							video.read(packet.side_data(), packet.side_data_elems());
							int sent = avcodec_send_packet(video.decoder, packet);
							if (sent >= 0) {
								video.receive(frame);
							} else {
								video.done = true; // Keep this PID's parameter metadata and continue other PIDs.
							}
						}
					} finally {
						av_packet_unref(packet);
					}
				}
				Map<Integer, Metadata> result = new LinkedHashMap<>();
				for (Video video : videos.values()) {
					if (!video.done && avcodec_send_packet(video.decoder, (AVPacket) null) >= 0) {
						video.receive(frame); // Delayed first picture, even when the packet budget was reached.
					}
					video.read(video.parameters.coded_side_data(), video.parameters.nb_coded_side_data());
					if (video.decoder != null && !video.decoder.isNull()) {
						video.read(video.decoder.coded_side_data(), video.decoder.nb_coded_side_data());
						for (int i = 0; i < video.decoder.nb_decoded_side_data(); i++) {
							video.readFrameSideData(video.decoder.decoded_side_data(i).type());
						}
					}
					result.put(video.pid, video.metadata());
				}
				if (reader.failure != null) {
					throw new IOException("Cannot read M2TS metadata prefix: " + path, reader.failure);
				}
				return result;
			} finally {
				if (frame != null && !frame.isNull()) {
					av_frame_free(frame);
				}
				if (packet != null && !packet.isNull()) {
					av_packet_free(packet);
				}
				for (Video video : videos.values()) {
					if (video.decoder != null && !video.decoder.isNull()) {
						avcodec_free_context(video.decoder);
					}
				}
				if (format != null && !format.isNull()) {
					avformat_close_input(format);
				}
				if (io != null && !io.isNull()) {
					av_free(io.buffer());
					io.buffer(null);
					avio_context_free(io);
				}
				if (buffer != null) {
					av_free(buffer);
				}
			}
		}
	}

	static Integer dynamicRange(int transfer, boolean dolbyVision) {
		if (dolbyVision) {
			return 2;
		}
		return switch (transfer) {
		case AVCOL_TRC_SMPTE2084 -> 1;
		case AVCOL_TRC_BT709, AVCOL_TRC_GAMMA22, AVCOL_TRC_GAMMA28, AVCOL_TRC_SMPTE170M, AVCOL_TRC_SMPTE240M,
				AVCOL_TRC_LINEAR, AVCOL_TRC_LOG, AVCOL_TRC_LOG_SQRT, AVCOL_TRC_IEC61966_2_4, AVCOL_TRC_BT1361_ECG,
				AVCOL_TRC_IEC61966_2_1, AVCOL_TRC_BT2020_10, AVCOL_TRC_BT2020_12 ->
			0;
		default -> null; // HLG, unspecified, reserved and unrecognized transfer functions are not guessed.
		};
	}

	static Integer colorSpace(int primaries) {
		return switch (primaries) {
		case AVCOL_PRI_BT709 -> 1;
		case AVCOL_PRI_BT2020 -> 2;
		default -> null;
		};
	}

	static boolean fourK(int width, int height) {
		return width >= 3840 || height >= 2160;
	}

	static boolean dolbyVisionSideData(int type, boolean frame) {
		return frame ? type == AV_FRAME_DATA_DOVI_METADATA || type == AV_FRAME_DATA_DOVI_RPU_BUFFER
				: type == AV_PKT_DATA_DOVI_CONF;
	}

	static Boolean hdrPlusFlag(int type, boolean frame) {
		return (frame ? type == AV_FRAME_DATA_DYNAMIC_HDR_PLUS : type == AV_PKT_DATA_DYNAMIC_HDR10_PLUS) ? Boolean.TRUE
				: null;
	}

	static Integer videoFormat(int width, int height, Boolean progressive) {
		if (progressive == null) {
			return null;
		}
		if (width == 720 || width == 704) {
			if (height == 480) {
				return progressive ? 3 : 1;
			}
			if (height == 576) {
				return progressive ? 7 : 2;
			}
		}
		if (width == 1280 && height == 720 && progressive) {
			return 5;
		}
		if ((width == 1920 || width == 1440) && height == 1080) {
			return progressive ? 6 : 4;
		}
		return width == 3840 && height == 2160 && progressive ? 8 : null;
	}

	static boolean matchesCodec(StreamCodingType expected, int codec) {
		if (expected == null) {
			return false;
		}
		return switch (expected) {
		case MPEG2_VIDEO -> codec == AV_CODEC_ID_MPEG2VIDEO;
		case H264_AVC -> codec == AV_CODEC_ID_H264;
		case H265_HEVC -> codec == AV_CODEC_ID_HEVC;
		case VC1 -> codec == AV_CODEC_ID_VC1;
		default -> false;
		};
	}

	private static void check(int result, Path path, String operation) throws IOException {
		if (result < 0) {
			throw new IOException(
					"M2TS metadata probe cannot " + operation + " for " + path + " (FFmpeg error " + result + ")");
		}
	}

	private static final class Video {
		private final int pid;
		private final AVCodecParameters parameters;
		private AVCodecContext decoder;
		private boolean done;
		private boolean decoded;
		private int width;
		private int height;
		private Boolean progressive;
		private int transfer = AVCOL_TRC_UNSPECIFIED;
		private int primaries = AVCOL_PRI_UNSPECIFIED;
		private boolean dolbyVision;
		private boolean hdrPlus;

		Video(int pid, AVCodecParameters parameters) {
			this.pid = pid;
			this.parameters = parameters;
			read(parameters.coded_side_data(), parameters.nb_coded_side_data());
		}

		void open() {
			var codec = avcodec_find_decoder(parameters.codec_id());
			if (codec == null || codec.isNull()) {
				done = true;
				return;
			}
			decoder = avcodec_alloc_context3(codec);
			if (decoder == null || decoder.isNull() || avcodec_parameters_to_context(decoder, parameters) < 0) {
				done = true;
				return;
			}
			decoder.thread_count(1).max_pixels(MAX_PIXELS);
			done = avcodec_open2(decoder, codec, (AVDictionary) null) < 0;
		}

		void receive(AVFrame frame) {
			int received = avcodec_receive_frame(decoder, frame);
			if (received >= 0) {
				try {
					read(decoder.coded_side_data(), decoder.nb_coded_side_data());
					if ((frame.flags() & AVFrame.AV_FRAME_FLAG_CORRUPT) == 0 && frame.decode_error_flags() == 0) {
						decoded = true;
						width = frame.width();
						height = frame.height();
						progressive = (frame.flags() & AVFrame.AV_FRAME_FLAG_INTERLACED) == 0
								&& (parameters.field_order() == AV_FIELD_UNKNOWN
										|| parameters.field_order() == AV_FIELD_PROGRESSIVE);
						transfer = frame.color_trc();
						primaries = frame.color_primaries();
						for (int i = 0; i < frame.nb_side_data(); i++) {
							readFrameSideData(frame.side_data(i).type());
						}
					}
				} finally {
					av_frame_unref(frame);
					done = true;
				}
			} else {
				int again = org.bytedeco.javacpp.Loader.getPlatform().startsWith("macosx") ? -35 : -11;
				done = received != again;
			}
		}

		void read(AVPacketSideData sides, int count) {
			if (sides != null && !sides.isNull()) {
				long original = sides.position();
				try {
					for (int i = 0; i < count; i++) {
						int type = sides.position(i).type();
						dolbyVision |= dolbyVisionSideData(type, false);
						hdrPlus |= Boolean.TRUE.equals(hdrPlusFlag(type, false));
					}
				} finally {
					sides.position(original); // Borrowed codec/packet memory; never close or free it here.
				}
			}
		}

		void readFrameSideData(int type) {
			dolbyVision |= dolbyVisionSideData(type, true);
			hdrPlus |= Boolean.TRUE.equals(hdrPlusFlag(type, true));
		}

		Metadata metadata() {
			int w = decoded && width > 0 ? width : parameters.width();
			int h = decoded && height > 0 ? height : parameters.height();
			Boolean scan = decoded ? progressive
					: parameters.field_order() == AV_FIELD_UNKNOWN ? null
							: parameters.field_order() == AV_FIELD_PROGRESSIVE;
			Integer range = dynamicRange(transfer, dolbyVision);
			if (range == null && transfer == AVCOL_TRC_UNSPECIFIED) {
				range = dynamicRange(parameters.color_trc(), dolbyVision);
			}
			Integer color = colorSpace(primaries);
			if (color == null && primaries == AVCOL_PRI_UNSPECIFIED) {
				color = colorSpace(parameters.color_primaries());
			}
			return new Metadata(videoFormat(w, h, scan), fourK(w, h), range, color, hdrPlus ? Boolean.TRUE : null);
		}
	}

	/** Non-seekable read-ahead counts toward the same hard cap; the callback never owns/closes the input. */
	static final class BoundedReader extends Read_packet_Pointer_BytePointer_int {
		private final InputStream input;
		private final byte[] bytes = new byte[IO_BUFFER_SIZE];
		private long remaining = MAX_INPUT_BYTES;
		private IOException failure;

		BoundedReader(InputStream input) {
			this.input = input;
		}

		@Override
		public int call(Pointer opaque, BytePointer destination, int requested) {
			if (remaining == 0 || failure != null) {
				return AVERROR_EOF;
			}
			try {
				int count = input.read(bytes, 0, (int) Math.min(Math.min(requested, bytes.length), remaining));
				if (count < 0) {
					return AVERROR_EOF;
				}
				destination.put(bytes, 0, count);
				remaining -= count;
				return count;
			} catch (IOException exception) {
				failure = exception;
				return AVERROR_EXTERNAL;
			}
		}
	}
}
