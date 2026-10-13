/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/utils/video/VideoStreamProbe.java' is part of BRTS.
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

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avcodec.AVPacketSideData;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.Read_packet_Pointer_BytePointer_int;
import org.bytedeco.ffmpeg.avutil.AVContentLightMetadata;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.avutil.AVFrameSideData;
import org.bytedeco.ffmpeg.avutil.AVMasteringDisplayMetadata;
import org.bytedeco.ffmpeg.avutil.AVPixFmtDescriptor;
import org.bytedeco.ffmpeg.avutil.AVRational;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Pointer;

/**
 * Bounded, local-file elementary-stream probe; never invokes an external executable.
 * <p>
 * Timing comes only from a fresh decoder after clearing the demuxer's nominal frame rate. In particular, raw H.264's
 * default 25 fps is not source timing. Missing codec timing is represented by a null frame rate. Static HDR strings use
 * x265's integer units: chromaticities / 50000 and luminance / 10000 cd/m2; incomplete mastering metadata stays null.
 * Decoder timing is nominal header timing, not proof of constant frame rate: no whole-stream cadence, H.264 fixed-rate
 * flag or HEVC POC-proportional timing validation is performed. A positive rate does not certify frame durations.
 * <p>
 * This is not a whole-stream validator: changes in scan mode, timing, colors or HDR metadata beyond the bounded prefix
 * and first decoded frame cannot be discovered. Dolby Vision/HDR10+ detected in that prefix is rejected, not preserved.
 * Unknown range has rangeKnown=false; fullRange=false alone must not imply limited-range signaling. HEVC requires a
 * complete, supported first SPS with progressive scan evidence; a frame lacking interlace flags is insufficient when
 * picture-timing SEI is absent. Later SPS changes are outside this first-header snapshot. Streams requiring more than
 * the probe budget fail rather than being guessed.
 */
final class VideoStreamProbe {

	private static final Set<String> INPUT_FORMATS = Set.of("h264", "hevc", "mpegvideo", "vc1");
	private static final int IO_BUFFER_SIZE = 32 * 1024;
	private static final long MAX_INPUT_BYTES = 16L * 1024 * 1024;
	private static final int MAX_PACKETS = 128;
	private static final int MAX_HEADER_BYTES = 1024 * 1024;

	private VideoStreamProbe() {
	}

	static VideoStreamInfo probe(Path path) throws IOException {
		if (path == null || !Files.isRegularFile(path)) {
			throw new IOException("Video probe requires a regular elementary-stream file: " + path);
		}
		AVFormatContext format = null;
		AVIOContext io = null;
		BytePointer buffer = null;
		AVCodecContext decoder = null;
		AVPacket packet = null;
		AVFrame frame = null;
		AVDictionary options = new AVDictionary(null);
		try (InputStream input = Files.newInputStream(path); BoundedReader reader = new BoundedReader(input)) {
			try {
				buffer = new BytePointer(av_malloc(IO_BUFFER_SIZE));
				if (buffer.isNull()) {
					throw new IOException("Cannot allocate probe I/O buffer for " + path);
				}
				io = avio_alloc_context(buffer, IO_BUFFER_SIZE, 0, null, reader, null, null);
				if (io == null || io.isNull()) {
					throw new IOException("Cannot allocate probe I/O context for " + path);
				}
				// AVIO owns the buffer, and may replace it during format probing.
				buffer = null;
				format = avformat_alloc_context();
				if (format == null || format.isNull()) {
					throw new IOException("Cannot allocate video format context for " + path);
				}
				format.pb(io).flags(format.flags() | AVFormatContext.AVFMT_FLAG_CUSTOM_IO);
				format.probesize(2L * 1024 * 1024).format_probesize(1024 * 1024).max_analyze_duration(2_000_000)
						.max_probe_packets(64).fps_probe_size(0);
				check(av_dict_set(options, "format_whitelist", String.join(",", INPUT_FORMATS), 0), path,
						"configure raw input whitelist");
				check(avformat_open_input(format, path.toAbsolutePath().toString(), null, options), path, "open input");
				String inputFormat = format.iformat().name().getString();
				if (!INPUT_FORMATS.contains(inputFormat)) {
					throw new IOException(
							"Unsupported input format " + inputFormat + "; compressed raw ES required: " + path);
				}
				check(avformat_find_stream_info(format, (AVDictionary) null), path, "analyze stream headers");
				if (format.nb_streams() != 1 || format.streams(0).codecpar().codec_type() != AVMEDIA_TYPE_VIDEO) {
					throw new IOException("Exactly one video stream and no other media are required: " + path);
				}
				AVCodecParameters parameters = format.streams(0).codecpar();
				HdrMetadata hdr = new HdrMetadata();
				hdr.read(parameters.coded_side_data(), parameters.nb_coded_side_data(), path);
				var codec = avcodec_find_decoder(parameters.codec_id());
				if (codec == null || codec.isNull()) {
					throw new IOException("No decoder for video codec " + parameters.codec_id() + " in " + path);
				}
				decoder = avcodec_alloc_context3(codec);
				if (decoder == null || decoder.isNull()) {
					throw new IOException("Cannot allocate video decoder for " + path);
				}
				check(avcodec_parameters_to_context(decoder, parameters), path, "copy codec parameters");
				// Do not seed the decoder with avformat's guessed timing. The decoder parses sequence headers/VUI.
				decoder.framerate().num(0).den(1);
				decoder.thread_count(1).max_pixels(8192L * 4320);
				check(avcodec_open2(decoder, codec, (AVDictionary) null), path, "open decoder");
				packet = av_packet_alloc();
				frame = av_frame_alloc();
				if (packet == null || packet.isNull() || frame == null || frame.isNull()) {
					throw new IOException("Cannot allocate video packet/frame for " + path);
				}
				boolean decoded = decodeFirst(format, decoder, packet, frame, hdr, path);
				if (reader.failure != null) {
					throw new IOException("Cannot read video probe prefix from " + path, reader.failure);
				}
				if (inputFormat.equals("hevc") && reader.dolbyVisionNal) {
					throw unsupportedHdr(path);
				}
				hdr.read(decoder.coded_side_data(), decoder.nb_coded_side_data(), path);
				for (int i = 0; i < decoder.nb_decoded_side_data(); i++) {
					hdr.read(decoder.decoded_side_data(i), path);
				}
				if (decoded) {
					for (int i = 0; i < frame.nb_side_data(); i++) {
						hdr.read(frame.side_data(i), path);
					}
				}
				int fieldOrder = parameters.field_order();
				boolean progressive;
				if (decoded) {
					// A decoded interlaced picture overrides a progressive sequence-level declaration.
					progressive = (frame.flags() & AVFrame.AV_FRAME_FLAG_INTERLACED) == 0
							&& (fieldOrder == AV_FIELD_UNKNOWN || fieldOrder == AV_FIELD_PROGRESSIVE);
				} else if (fieldOrder != AV_FIELD_UNKNOWN) {
					progressive = fieldOrder == AV_FIELD_PROGRESSIVE;
				} else if (inputFormat.equals("hevc")) {
					progressive = true; // Must still be established by the SPS check below.
				} else {
					throw new IOException("Cannot determine video scan mode within bounded probe: " + path);
				}
				if (inputFormat.equals("hevc")) {
					// FFmpeg frame flags can miss field_seq_flag when picture-timing SEI is absent.
					if (!hevcProgressive(reader.prefix.toByteArray(), reader.eof, path) || !progressive) {
						throw new IOException("Interlaced HEVC sequence is not supported: " + path);
					}
				}
				int width = decoded ? frame.width() : parameters.width();
				int height = decoded ? frame.height() : parameters.height();
				if (width <= 0 || height <= 0) {
					throw new IOException("Unknown video dimensions in " + path);
				}
				AVRational sar = decoded ? frame.sample_aspect_ratio() : parameters.sample_aspect_ratio();
				if (!positive(sar)) {
					sar = parameters.sample_aspect_ratio();
				}
				int sarNum = positive(sar) ? sar.num() : 1;
				int sarDen = positive(sar) ? sar.den() : 1;
				AVRational timing = decoder.framerate();
				FrameRate rate = positive(timing) ? FrameRate.of(timing.num(), timing.den()) : null;
				int primaries = decoded ? frame.color_primaries() : parameters.color_primaries();
				int transfer = decoded ? frame.color_trc() : parameters.color_trc();
				int matrix = decoded ? frame.colorspace() : parameters.color_space();
				int range = decoded ? frame.color_range() : parameters.color_range();
				if (range == AVCOL_RANGE_UNSPECIFIED) {
					range = parameters.color_range();
				}
				int chromaLocation = decoded ? frame.chroma_location() : parameters.chroma_location();
				if (chromaLocation == AVCHROMA_LOC_UNSPECIFIED) {
					chromaLocation = parameters.chroma_location();
				}
				AVPixFmtDescriptor pixels = av_pix_fmt_desc_get(decoded ? frame.format() : parameters.format());
				if (pixels == null || pixels.isNull()) {
					pixels = av_pix_fmt_desc_get(parameters.format());
				}
				boolean pixelKnown = pixels != null && !pixels.isNull() && pixels.nb_components() > 0;
				int bitDepth = pixelKnown ? pixels.comp(0).depth() : -1;
				// Monochrome/RGB descriptors do not establish YUV chroma subsampling.
				boolean chromaKnown = pixelKnown && pixels.nb_components() >= 3
						&& (pixels.flags() & AV_PIX_FMT_FLAG_RGB) == 0;
				int profile = parameters.profile() >= 0 ? parameters.profile() : decoder.profile();
				int level = parameters.level() >= 0 ? parameters.level() : decoder.level();
				return new VideoStreamInfo(width, height, rate, sarNum, sarDen, progressive, inputFormat,
						colorName(av_color_primaries_name(primaries),
								av_color_primaries_name(parameters.color_primaries())),
						colorName(av_color_transfer_name(transfer), av_color_transfer_name(parameters.color_trc())),
						colorName(av_color_space_name(matrix), av_color_space_name(parameters.color_space())),
						range == AVCOL_RANGE_JPEG, hdr.masterDisplay, hdr.maxCll, bitDepth,
						chromaKnown ? pixels.log2_chroma_w() : -1, chromaKnown ? pixels.log2_chroma_h() : -1,
						profile >= 0 ? profile : -1, level >= 0 ? level : -1,
						chromaLocation == AVCHROMA_LOC_UNSPECIFIED ? -1 : chromaLocation,
						range != AVCOL_RANGE_UNSPECIFIED);
			} finally {
				if (frame != null && !frame.isNull()) {
					av_frame_free(frame);
				}
				if (packet != null && !packet.isNull()) {
					av_packet_free(packet);
				}
				if (decoder != null && !decoder.isNull()) {
					avcodec_free_context(decoder);
				}
				if (format != null && !format.isNull()) {
					avformat_close_input(format);
				}
				av_dict_free(options);
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

	private static boolean decodeFirst(AVFormatContext format, AVCodecContext decoder, AVPacket packet, AVFrame frame,
			HdrMetadata hdr, Path path) throws IOException {
		for (int i = 0; i < MAX_PACKETS; i++) {
			int read = av_read_frame(format, packet);
			if (read < 0) {
				if (read != AVERROR_EOF) {
					check(read, path, "read video packet");
				}
				check(avcodec_send_packet(decoder, (AVPacket) null), path, "flush first-frame decoder");
				return receiveFirst(decoder, frame, path);
			}
			try {
				if (packet.stream_index() != 0) {
					throw new IOException("Unexpected additional media stream in " + path);
				}
				hdr.read(packet.side_data(), packet.side_data_elems(), path);
				check(avcodec_send_packet(decoder, packet), path, "decode video packet");
			} finally {
				av_packet_unref(packet);
			}
			if (receiveFirst(decoder, frame, path)) {
				return true;
			}
		}
		return false;
	}

	private static boolean receiveFirst(AVCodecContext decoder, AVFrame frame, Path path) throws IOException {
		int receive = avcodec_receive_frame(decoder, frame);
		if (receive >= 0) {
			if ((frame.flags() & AVFrame.AV_FRAME_FLAG_CORRUPT) != 0 || frame.decode_error_flags() != 0) {
				throw new IOException("Corrupt first video frame in " + path);
			}
			return true;
		}
		// FFmpeg uses the platform errno for EAGAIN (35 on macOS, 11 on Linux/Windows).
		int again = org.bytedeco.javacpp.Loader.getPlatform().startsWith("macosx") ? -35 : -11;
		if (receive != again && receive != AVERROR_EOF) {
			check(receive, path, "receive first video frame");
		}
		return false;
	}

	private static boolean positive(AVRational rational) {
		return rational != null && !rational.isNull() && rational.num() > 0 && rational.den() > 0;
	}

	private static String colorName(BytePointer primary, BytePointer fallback) {
		String name = colorName(primary);
		return name != null ? name : colorName(fallback);
	}

	private static String colorName(BytePointer name) {
		if (name == null || name.isNull()) {
			return null;
		}
		String value = name.getString();
		return value.equals("unknown") || value.equals("unspecified") || value.startsWith("reserved") ? null : value;
	}

	private static void check(int result, Path path, String operation) throws IOException {
		if (result < 0) {
			throw new IOException(
					"Video probe cannot " + operation + " for " + path + " (FFmpeg error " + result + ")");
		}
	}

	private static IOException unsupportedHdr(Path path) {
		return new IOException("Unsupported HDR10+/Dolby Vision metadata; preservation is not available: " + path);
	}

	/**
	 * Minimal ITU-T H.265 SPS traversal, stopping at VUI field_seq_flag. Not a conformance parser: single-layer SPS
	 * only, bounded syntax counts, no timing/HRD/extensions parsing. Ambiguous PTL scan declarations fail closed.
	 */
	private static boolean hevcProgressive(byte[] prefix, boolean eof, Path path) throws IOException {
		try {
			for (int i = 2; i + 2 < prefix.length; i++) {
				if (prefix[i] != 1 || prefix[i - 1] != 0 || prefix[i - 2] != 0 || ((prefix[i + 1] & 0x7e) >> 1) != 33) {
					continue;
				}
				int header = i + 1;
				if ((prefix[header] & 0x81) != 0 || (prefix[header + 1] & 0xf8) != 0 || (prefix[header + 1] & 7) == 0) {
					throw new IllegalArgumentException("unsupported SPS NAL header/layer");
				}
				int end = prefix.length;
				for (int j = header + 4; j < prefix.length; j++) {
					if (prefix[j] == 1 && prefix[j - 1] == 0 && prefix[j - 2] == 0) {
						end = j - 2;
						break;
					}
				}
				if (end == prefix.length && (!eof || prefix.length == MAX_HEADER_BYTES)) {
					throw new IllegalArgumentException("incomplete SPS in captured prefix");
				}
				ByteArrayOutputStream rbsp = new ByteArrayOutputStream();
				int zeros = 0;
				for (int j = header + 2; j < end; j++) {
					int value = prefix[j] & 0xff;
					if (zeros >= 2 && value == 3) {
						if (j + 1 >= end || (prefix[j + 1] & 0xff) > 3) {
							throw new IllegalArgumentException("invalid SPS emulation prevention");
						}
						zeros = 0;
						continue;
					}
					rbsp.write(value);
					zeros = value == 0 ? zeros + 1 : 0;
				}
				return hevcSpsProgressive(new HeaderBits(rbsp.toByteArray()));
			}
			throw new IllegalArgumentException("no complete first SPS");
		} catch (IllegalArgumentException exception) {
			throw new IOException("Cannot establish HEVC scan mode within bounded first-header probe for " + path + ": "
					+ exception.getMessage(), exception);
		}
	}

	private static boolean hevcSpsProgressive(HeaderBits bits) {
		bits.skip(4); // sps_video_parameter_set_id
		int layers = bits.read(3);
		if (layers > 6) {
			throw new IllegalArgumentException("unsupported SPS sublayers");
		}
		bits.skip(1 + 8 + 32); // nesting, general profile and compatibility flags
		boolean progressiveSource = bits.flag();
		boolean interlacedSource = bits.flag();
		bits.skip(46 + 8); // remaining general constraints and level
		boolean[] profiles = new boolean[layers];
		boolean[] levels = new boolean[layers];
		for (int i = 0; i < layers; i++) {
			profiles[i] = bits.flag();
			levels[i] = bits.flag();
		}
		if (layers > 0) {
			bits.skip((8 - layers) * 2);
		}
		for (int i = 0; i < layers; i++) {
			bits.skip((profiles[i] ? 88 : 0) + (levels[i] ? 8 : 0));
		}
		bits.ue(15); // sps_seq_parameter_set_id
		int chroma = bits.ue(3);
		if (chroma == 3) {
			bits.skip(1);
		}
		bits.ue(8192);
		bits.ue(8192);
		if (bits.flag()) {
			for (int i = 0; i < 4; i++) {
				bits.ue(8192);
			}
		}
		bits.ue(8); // bit_depth_luma_minus8
		bits.ue(8); // bit_depth_chroma_minus8
		int pocBits = bits.ue(12) + 4;
		for (int i = bits.flag() ? 0 : layers; i <= layers; i++) {
			bits.ue(16);
			bits.ue(16);
			bits.ue(Integer.MAX_VALUE);
		}
		for (int i = 0; i < 6; i++) {
			bits.ue(30); // coding/transform block sizes and hierarchy depths
		}
		if (bits.flag() && bits.flag()) { // scaling_list_enabled and scaling_list_data_present
			for (int size = 0; size < 4; size++) {
				for (int matrix = 0; matrix < 6; matrix += size == 3 ? 3 : 1) {
					if (!bits.flag()) {
						bits.ue(matrix);
					} else {
						if (size > 1) {
							bits.ue(512); // signed Exp-Golomb has the same encoded length
						}
						for (int j = 0; j < Math.min(64, 1 << (4 + 2 * size)); j++) {
							bits.ue(512);
						}
					}
				}
			}
		}
		bits.skip(2); // amp_enabled_flag, sample_adaptive_offset_enabled_flag
		if (bits.flag()) { // pcm_enabled_flag
			bits.skip(8);
			bits.ue(30);
			bits.ue(30);
			bits.skip(1);
		}
		int[] deltaCounts = new int[bits.ue(64)];
		for (int i = 0; i < deltaCounts.length; i++) {
			if (i > 0 && bits.flag()) {
				bits.skip(1); // delta_rps_sign
				bits.ue(32767);
				for (int j = 0; j <= deltaCounts[i - 1]; j++) {
					if (bits.flag() || bits.flag()) {
						deltaCounts[i]++;
					}
				}
				if (deltaCounts[i] > 32) {
					throw new IllegalArgumentException("excessive predicted SPS reference count");
				}
			} else {
				deltaCounts[i] = bits.ue(16) + bits.ue(16);
				for (int j = 0; j < deltaCounts[i]; j++) {
					bits.ue(32767);
					bits.skip(1);
				}
			}
		}
		if (bits.flag()) {
			int count = bits.ue(32);
			bits.skip(count * (pocBits + 1));
		}
		bits.skip(2); // temporal_mvp_enabled, strong_intra_smoothing_enabled
		boolean fieldSequence = false; // field_seq_flag is inferred zero when VUI is absent.
		if (bits.flag()) {
			if (bits.flag() && bits.read(8) == 255) { // aspect_ratio_info_present, Extended_SAR
				bits.skip(32);
			}
			if (bits.flag()) { // overscan_info_present
				bits.skip(1);
			}
			if (bits.flag()) { // video_signal_type_present
				bits.skip(4);
				if (bits.flag()) {
					bits.skip(24);
				}
			}
			if (bits.flag()) { // chroma_loc_info_present
				bits.ue(5);
				bits.ue(5);
			}
			bits.skip(1); // neutral_chroma_indication_flag
			fieldSequence = bits.flag();
		}
		if (fieldSequence || (interlacedSource && !progressiveSource)) {
			return false;
		}
		if (!progressiveSource || interlacedSource) {
			throw new IllegalArgumentException("ambiguous SPS progressive/interlaced source flags");
		}
		return true;
	}

	private static final class HeaderBits {
		private final byte[] bytes;
		private int position;

		HeaderBits(byte[] bytes) {
			this.bytes = bytes;
		}

		void skip(int count) {
			if (count < 0 || count > bytes.length * 8 - position) {
				throw new IllegalArgumentException("truncated SPS syntax");
			}
			position += count;
		}

		int read(int count) {
			int value = 0;
			for (int i = 0; i < count; i++) {
				skip(1);
				value = (value << 1) | ((bytes[(position - 1) >> 3] >> (7 - ((position - 1) & 7))) & 1);
			}
			return value;
		}

		boolean flag() {
			return read(1) != 0;
		}

		int ue(int maximum) {
			int zeros = 0;
			while (!flag()) {
				if (++zeros > 30) {
					throw new IllegalArgumentException("oversized SPS Exp-Golomb value");
				}
			}
			long value = (1L << zeros) - 1 + read(zeros);
			if (value > maximum) {
				throw new IllegalArgumentException("SPS syntax value exceeds probe bounds");
			}
			return (int) value;
		}
	}

	private static final class HdrMetadata {
		private String masterDisplay;
		private String maxCll;

		void read(AVPacketSideData sides, int count, Path path) throws IOException {
			for (int i = 0; i < count; i++) {
				AVPacketSideData side = sides.getPointer(i);
				if (side.type() == AV_PKT_DATA_DOVI_CONF || side.type() == AV_PKT_DATA_DYNAMIC_HDR10_PLUS) {
					throw unsupportedHdr(path);
				}
				if (side.type() == AV_PKT_DATA_MASTERING_DISPLAY_METADATA) {
					master(side.data(), side.size());
				} else if (side.type() == AV_PKT_DATA_CONTENT_LIGHT_LEVEL) {
					light(side.data(), side.size());
				}
			}
		}

		void read(AVFrameSideData side, Path path) throws IOException {
			if (side == null || side.isNull()) {
				return;
			}
			int type = side.type();
			if (type == AV_FRAME_DATA_DYNAMIC_HDR_PLUS || type == AV_FRAME_DATA_DOVI_RPU_BUFFER
					|| type == AV_FRAME_DATA_DOVI_METADATA || type == AV_FRAME_DATA_DYNAMIC_HDR_VIVID) {
				throw unsupportedHdr(path);
			}
			if (type == AV_FRAME_DATA_MASTERING_DISPLAY_METADATA) {
				master(side.data(), side.size());
			} else if (type == AV_FRAME_DATA_CONTENT_LIGHT_LEVEL) {
				light(side.data(), side.size());
			}
		}

		private void master(BytePointer data, long size) {
			if (data == null || data.isNull() || size < Pointer.sizeof(AVMasteringDisplayMetadata.class)) {
				return;
			}
			try (AVMasteringDisplayMetadata metadata = new AVMasteringDisplayMetadata(data)) {
				master(metadata);
			}
		}

		private void master(AVMasteringDisplayMetadata metadata) {
			if (metadata.has_primaries() == 0 || metadata.has_luminance() == 0) {
				return;
			}
			long[] values = new long[10];
			int n = 0;
			// FFmpeg stores RGB; x265's master-display syntax is GBR.
			for (int primary : new int[] { 1, 2, 0 }) {
				values[n++] = scaled(metadata.display_primaries(primary, 0), 50000);
				values[n++] = scaled(metadata.display_primaries(primary, 1), 50000);
			}
			values[n++] = scaled(metadata.white_point(0), 50000);
			values[n++] = scaled(metadata.white_point(1), 50000);
			values[n++] = scaled(metadata.max_luminance(), 10000);
			values[n] = scaled(metadata.min_luminance(), 10000);
			for (long value : values) {
				if (value < 0) {
					return;
				}
			}
			masterDisplay = "G(%d,%d)B(%d,%d)R(%d,%d)WP(%d,%d)L(%d,%d)".formatted(values[0], values[1], values[2],
					values[3], values[4], values[5], values[6], values[7], values[8], values[9]);
		}

		private void light(BytePointer data, long size) {
			if (data != null && !data.isNull() && size >= Pointer.sizeof(AVContentLightMetadata.class)) {
				try (AVContentLightMetadata metadata = new AVContentLightMetadata(data)) {
					maxCll = Integer.toUnsignedString(metadata.MaxCLL()) + ","
							+ Integer.toUnsignedString(metadata.MaxFALL());
				}
			}
		}

		private static long scaled(AVRational value, int scale) {
			return value.den() > 0 && value.num() >= 0 ? Math.round((double) value.num() * scale / value.den()) : -1;
		}
	}

	/** Hard byte cap shared by open, header analysis and decoding, including native read-ahead. */
	private static final class BoundedReader extends Read_packet_Pointer_BytePointer_int {
		private final InputStream input;
		private final byte[] bytes = new byte[IO_BUFFER_SIZE];
		private final ByteArrayOutputStream prefix = new ByteArrayOutputStream();
		private long remaining = MAX_INPUT_BYTES;
		private boolean eof;
		private IOException failure;
		private int zeros;
		private boolean nalHeader;
		private boolean dolbyVisionNal;

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
					eof = true;
					return AVERROR_EOF;
				}
				prefix.write(bytes, 0, Math.min(count, MAX_HEADER_BYTES - prefix.size()));
				for (int i = 0; i < count; i++) {
					int value = bytes[i] & 0xff;
					if (nalHeader) {
						// HEVC reserved NAL types used by Dolby Vision RPU/enhancement layers. Conservatively reject.
						int type = (value >> 1) & 63;
						dolbyVisionNal |= (value & 0x80) == 0 && (type == 62 || type == 63);
						nalHeader = false;
					}
					if (value == 1 && zeros >= 2) {
						nalHeader = true;
					}
					zeros = value == 0 ? Math.min(zeros + 1, 3) : 0;
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
