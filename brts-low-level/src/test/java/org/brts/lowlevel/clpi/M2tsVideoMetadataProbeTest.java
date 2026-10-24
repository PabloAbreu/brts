/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/test/java/org/brts/lowlevel/clpi/M2tsVideoMetadataProbeTest.java' is part of BRTS.
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
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.brts.common.model.StreamCodingType;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class M2tsVideoMetadataProbeTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({ "1,0", "4,0", "5,0", "6,0", "7,0", "8,0", "9,0", "10,0", "11,0", "12,0", "13,0", "14,0", "15,0",
			"16,1" })
	void mapsOnlyKnownTransferFunctions(int transfer, int expected) {
		assertEquals(expected, M2tsVideoMetadataProbe.dynamicRange(transfer, false));
	}

	@ParameterizedTest
	@ValueSource(ints = { -1, 0, 2, 3, 17, 18, 19, 255 })
	void unknownReservedHlgAndCinemaTransferRemainUnknown(int transfer) {
		assertNull(M2tsVideoMetadataProbe.dynamicRange(transfer, false));
	}

	@ParameterizedTest
	@ValueSource(ints = { 1, 2, 16, 18, 255 })
	void positiveDolbyVisionOverridesTransfer(int transfer) {
		assertEquals(2, M2tsVideoMetadataProbe.dynamicRange(transfer, true));
	}

	@Test
	void sideDataMappingsKeepPacketAndFrameEnumsSeparateAndAbsenceUnknown() {
		assertTrue(M2tsVideoMetadataProbe.dolbyVisionSideData(AV_PKT_DATA_DOVI_CONF, false));
		assertTrue(M2tsVideoMetadataProbe.dolbyVisionSideData(AV_FRAME_DATA_DOVI_METADATA, true));
		assertTrue(M2tsVideoMetadataProbe.dolbyVisionSideData(AV_FRAME_DATA_DOVI_RPU_BUFFER, true));
		assertFalse(M2tsVideoMetadataProbe.dolbyVisionSideData(AV_PKT_DATA_MASTERING_DISPLAY_METADATA, false));
		assertFalse(M2tsVideoMetadataProbe.dolbyVisionSideData(AV_FRAME_DATA_MASTERING_DISPLAY_METADATA, true));
		assertEquals(Boolean.TRUE, M2tsVideoMetadataProbe.hdrPlusFlag(AV_PKT_DATA_DYNAMIC_HDR10_PLUS, false));
		assertEquals(Boolean.TRUE, M2tsVideoMetadataProbe.hdrPlusFlag(AV_FRAME_DATA_DYNAMIC_HDR_PLUS, true));
		assertNull(M2tsVideoMetadataProbe.hdrPlusFlag(AV_FRAME_DATA_DYNAMIC_HDR_VIVID, true));
		assertNull(M2tsVideoMetadataProbe.hdrPlusFlag(AV_PKT_DATA_CONTENT_LIGHT_LEVEL, false));
		assertNull(M2tsVideoMetadataProbe.hdrPlusFlag(-1, false));
		assertFalse(M2tsVideoMetadataProbe.dolbyVisionSideData(-1, true));
	}

	@Test
	void mapsPrimariesRatherThanMatrixCoefficients() {
		assertEquals(1, M2tsVideoMetadataProbe.colorSpace(AVCOL_PRI_BT709));
		assertEquals(2, M2tsVideoMetadataProbe.colorSpace(AVCOL_PRI_BT2020));
		for (int unknown : new int[] { -1, 0, 2, 3, 4, 5, 6, 7, 8, 10, 12, 255 }) {
			assertNull(M2tsVideoMetadataProbe.colorSpace(unknown));
		}
	}

	@ParameterizedTest
	@CsvSource({ "720,480,false,1", "704,480,true,3", "720,576,false,2", "720,576,true,7", "1280,720,true,5",
			"1920,1080,false,4", "1920,1080,true,6", "1440,1080,true,6", "3840,2160,true,8" })
	void mapsCompatibleBluRayDimensions(int width, int height, boolean progressive, int expected) {
		assertEquals(expected, M2tsVideoMetadataProbe.videoFormat(width, height, progressive));
	}

	@Test
	void unknownScanAndIncompatibleDimensionsDoNotBecome1080p() {
		assertNull(M2tsVideoMetadataProbe.videoFormat(1920, 1080, null));
		assertNull(M2tsVideoMetadataProbe.videoFormat(3840, 2160, false));
		assertNull(M2tsVideoMetadataProbe.videoFormat(1280, 720, false));
		assertNull(M2tsVideoMetadataProbe.videoFormat(4096, 2160, true));
		assertNull(M2tsVideoMetadataProbe.videoFormat(1920, 800, true));
		assertNull(M2tsVideoMetadataProbe.videoFormat(0, 0, true));
	}

	@Test
	void fourKIsIndependentOfCompatibleFormatAndEitherDimensionSuffices() {
		assertTrue(M2tsVideoMetadataProbe.fourK(3840, 1600));
		assertTrue(M2tsVideoMetadataProbe.fourK(2880, 2160));
		assertTrue(M2tsVideoMetadataProbe.fourK(7680, 4320));
		assertFalse(M2tsVideoMetadataProbe.fourK(3839, 2159));
		assertFalse(M2tsVideoMetadataProbe.fourK(0, 0));
	}

	@Test
	void codecMatchingDoesNotConfusePidWithStreamIndexOrDifferentCodecs() {
		assertTrue(M2tsVideoMetadataProbe.matchesCodec(StreamCodingType.MPEG2_VIDEO, AV_CODEC_ID_MPEG2VIDEO));
		assertTrue(M2tsVideoMetadataProbe.matchesCodec(StreamCodingType.H264_AVC, AV_CODEC_ID_H264));
		assertTrue(M2tsVideoMetadataProbe.matchesCodec(StreamCodingType.H265_HEVC, AV_CODEC_ID_HEVC));
		assertTrue(M2tsVideoMetadataProbe.matchesCodec(StreamCodingType.VC1, AV_CODEC_ID_VC1));
		assertFalse(M2tsVideoMetadataProbe.matchesCodec(StreamCodingType.H265_HEVC, AV_CODEC_ID_H264));
		assertFalse(M2tsVideoMetadataProbe.matchesCodec(StreamCodingType.DOLBY_AC3, AV_CODEC_ID_AC3));
		assertFalse(M2tsVideoMetadataProbe.matchesCodec(null, AV_CODEC_ID_HEVC));
	}

	@Test
	void emptyRequestNeedsNeitherFileNorNativeProbe() throws Exception {
		assertTrue(new M2tsVideoMetadataProbe().probe(null, Map.of()).isEmpty());
	}

	@Test
	void rejectsNonLocalRegularInput() {
		var probe = new M2tsVideoMetadataProbe();
		var expected = Map.of(0x1011, StreamCodingType.H265_HEVC);
		assertThrows(IOException.class, () -> probe.probe(null, expected));
		assertThrows(IOException.class, () -> probe.probe(directory, expected));
		assertThrows(IOException.class, () -> probe.probe(directory.resolve("missing.m2ts"), expected));
	}

	@Test
	void callbackHasOneHardBudgetIncludingReadAheadAndRepeatedCalls() throws Exception {
		AtomicLong consumed = new AtomicLong();
		InputStream endless = new InputStream() {
			@Override
			public int read() {
				throw new AssertionError("Bulk reads only");
			}

			@Override
			public int read(byte[] bytes, int offset, int length) {
				consumed.addAndGet(length);
				return length;
			}
		};
		try (var reader = new M2tsVideoMetadataProbe.BoundedReader(endless); var destination = new BytePointer(32768)) {
			assertEquals(17, reader.call(null, destination, 17));
			long total = 17;
			int count;
			while ((count = reader.call(null, destination, Integer.MAX_VALUE)) > 0) {
				total += count;
			}
			assertEquals(AVERROR_EOF, count);
			assertEquals(16L * 1024 * 1024, total);
			assertEquals(total, consumed.get());
			assertEquals(AVERROR_EOF, reader.call(null, destination, 32768));
			assertEquals(total, consumed.get());
		}
	}

	@Test
	void callbackContainsIoFailureInsteadOfThrowingAcrossNativeBoundary() throws Exception {
		InputStream broken = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("synthetic read failure");
			}
		};
		try (var reader = new M2tsVideoMetadataProbe.BoundedReader(broken); var destination = new BytePointer(32)) {
			assertEquals(AVERROR_EXTERNAL, reader.call(null, destination, 32));
			assertEquals(AVERROR_EOF, reader.call(null, destination, 32));
		}
	}

	@Test
	void nativeSyntheticM2tsMatchesExactPidsAndPreservesPartialResults() throws Exception {
		Path fixture = syntheticM2ts();
		var expected = Map.of(0x1011, StreamCodingType.MPEG2_VIDEO, 0x1012, StreamCodingType.MPEG2_VIDEO, 0,
				StreamCodingType.MPEG2_VIDEO, 1, StreamCodingType.MPEG2_VIDEO, 0x1200, StreamCodingType.H265_HEVC);
		var result = new M2tsVideoMetadataProbe().probe(fixture, expected);
		assertEquals(2, result.size());
		assertTrue(result.containsKey(0x1011));
		assertTrue(result.containsKey(0x1012));
		for (var metadata : result.values()) {
			assertEquals(3, metadata.videoFormat());
			assertFalse(metadata.fourK());
			assertEquals(0, metadata.dynamicRangeType());
			assertEquals(1, metadata.colorSpace());
			assertNull(metadata.hdrPlusFlag(), "Absence is not evidence of false");
		}
		assertTrue(new M2tsVideoMetadataProbe().probe(fixture, Map.of(0x1011, StreamCodingType.H265_HEVC)).isEmpty());
		// Repeat to exercise allocation/free and custom-AVIO ownership more than once.
		assertEquals(result, new M2tsVideoMetadataProbe().probe(fixture, expected));
	}

	@Test
	void malformedInputFailsAsIoWithoutHanging() throws Exception {
		Path path = Files.write(directory.resolve("invalid.m2ts"), new byte[8192]);
		assertThrows(IOException.class,
				() -> new M2tsVideoMetadataProbe().probe(path, Map.of(0x1011, StreamCodingType.H265_HEVC)));
	}

	/** Original black pictures encoded and muxed entirely through JavaCPP, never an external executable. */
	private Path syntheticM2ts() throws IOException {
		Path path = directory.resolve("original synthetic fixture.m2ts");
		AVFormatContext output = new AVFormatContext(null);
		AVIOContext io = new AVIOContext(null);
		AVCodecContext encoder = null;
		AVFrame frame = null;
		AVPacket packet = null;
		AVDictionary options = new AVDictionary(null);
		try {
			ok(avformat_alloc_output_context2(output, null, "mpegts", path.toString()));
			var codec = avcodec_find_encoder(AV_CODEC_ID_MPEG2VIDEO);
			assertNotNull(codec);
			assertFalse(codec.isNull());
			encoder = avcodec_alloc_context3(codec);
			encoder.width(720).height(480).pix_fmt(AV_PIX_FMT_YUV420P).bit_rate(400_000).gop_size(1).max_b_frames(0)
					.thread_count(1).color_primaries(AVCOL_PRI_BT709).color_trc(AVCOL_TRC_BT709)
					.colorspace(AVCOL_SPC_BT709);
			encoder.time_base().num(1).den(25);
			encoder.framerate().num(25).den(1);
			ok(avcodec_open2(encoder, codec, (AVDictionary) null));
			for (int i = 0; i < 2; i++) {
				var stream = avformat_new_stream(output, null);
				stream.id(0x1011 + i);
				stream.time_base().num(1).den(25);
				ok(avcodec_parameters_from_context(stream.codecpar(), encoder));
			}
			ok(avio_open(io, path.toString(), AVIO_FLAG_WRITE));
			output.pb(io);
			ok(av_dict_set(options, "mpegts_m2ts_mode", "1", 0));
			ok(avformat_write_header(output, options));
			frame = av_frame_alloc();
			packet = av_packet_alloc();
			frame.width(720).height(480).format(AV_PIX_FMT_YUV420P);
			ok(av_frame_get_buffer(frame, 32));
			for (int plane = 0; plane < 3; plane++) {
				int size = frame.linesize(plane) * (plane == 0 ? 480 : 240);
				byte[] pixels = new byte[size];
				java.util.Arrays.fill(pixels, (byte) (plane == 0 ? 16 : 128));
				frame.data(plane).put(pixels);
			}
			for (int i = 0; i < 4; i++) {
				frame.pts(i);
				ok(avcodec_send_frame(encoder, frame));
				muxPackets(output, encoder, packet);
			}
			ok(avcodec_send_frame(encoder, null));
			muxPackets(output, encoder, packet);
			ok(av_write_trailer(output));
			return path;
		} finally {
			if (packet != null) {
				av_packet_free(packet);
			}
			if (frame != null) {
				av_frame_free(frame);
			}
			if (encoder != null) {
				avcodec_free_context(encoder);
			}
			av_dict_free(options);
			if (!output.isNull()) {
				if (output.pb() != null && !output.pb().isNull()) {
					avio_closep(output.pb());
				}
				avformat_free_context(output);
			}
		}
	}

	private static void muxPackets(AVFormatContext output, AVCodecContext encoder, AVPacket packet) {
		while (avcodec_receive_packet(encoder, packet) >= 0) {
			try {
				for (int index = 0; index < 2; index++) {
					AVPacket copy = av_packet_clone(packet);
					try {
						copy.stream_index(index);
						av_packet_rescale_ts(copy, encoder.time_base(), output.streams(index).time_base());
						ok(av_interleaved_write_frame(output, copy));
					} finally {
						av_packet_free(copy);
					}
				}
			} finally {
				av_packet_unref(packet);
			}
		}
	}

	private static void ok(int result) {
		assertTrue(result >= 0, "FFmpeg fixture operation failed: " + result);
	}
}
