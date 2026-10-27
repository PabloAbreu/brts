/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 * This file 'brts-low-level/src/main/java/org/brts/lowlevel/m2ts/PreservationVideoTimeline.java' is part of BRTS.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVCodecParserContext;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;

import org.brts.common.m2ts.model.M2tsTimingManifest.StreamTiming;
import org.brts.common.model.StreamCodingType;

/** Restricted proof of one timestamped access unit per PES, not an assumption based on PES counts. */
final class PreservationVideoTimeline {

	private PreservationVideoTimeline() {
	}

	static void validate(Path es, StreamTiming timing) throws IOException {
		PreservingM2tsClipWriter.validateTiming(timing);
		boolean avc = timing.codingType() == StreamCodingType.H264_AVC;
		require(avc || timing.codingType() == StreamCodingType.H265_HEVC, "Unsupported video timeline codec");
		State state = new State(timing, avc);
		try (var in = new BufferedInputStream(Files.newInputStream(es))) {
			long position = 0;
			long nalOffset = -1;
			int zeros = 0;
			byte[] prefix = new byte[3];
			int used = 0;
			for (int value; (value = in.read()) >= 0; position++) {
				if (value == 0) {
					zeros++;
					continue;
				}
				if (value == 1 && zeros >= 2) {
					if (nalOffset >= 0)
						state.nal(nalOffset, prefix, used);
					nalOffset = position - Math.min(zeros, 3);
					used = 0;
				} else {
					require(nalOffset >= 0, "Video data before Annex-B start code");
					for (int i = 0; i < zeros && used < prefix.length; i++)
						prefix[used++] = 0;
					if (used < prefix.length)
						prefix[used++] = (byte) value;
				}
				zeros = 0;
			}
			if (nalOffset >= 0)
				state.nal(nalOffset, prefix, used);
		}
		state.finish();
		if (state.multislice)
			validateParsedAccessUnits(es, timing, avc);
	}

	/** Native H.26x parser proves picture boundaries across slices; it does not decode pixels. */
	private static void validateParsedAccessUnits(Path es, StreamTiming timing, boolean avc) throws IOException {
		AVCodecParserContext parser = av_parser_init(avc ? AV_CODEC_ID_H264 : AV_CODEC_ID_HEVC);
		AVCodecContext codec = avcodec_alloc_context3(null);
		try (BytePointer input = new BytePointer(65536 + AV_INPUT_BUFFER_PADDING_SIZE);
				BytePointer output = new BytePointer((org.bytedeco.javacpp.Pointer) null);
				IntPointer size = new IntPointer(1);
				var stream = Files.newInputStream(es)) {
			require(parser != null && !parser.isNull() && codec != null && !codec.isNull(),
					"Cannot allocate video access-unit parser");
			byte[] bytes = new byte[65536];
			long position = 0;
			int units = 0;
			for (int count; (count = stream.read(bytes)) >= 0;) {
				if (count == 0)
					continue;
				input.position(0).put(bytes, 0, count);
				for (int i = 0; i < AV_INPUT_BUFFER_PADDING_SIZE; i++)
					input.put(count + i, (byte) 0);
				int offset = 0;
				while (offset < count) {
					int consumed = av_parser_parse2(parser, codec, output, size, input.position(offset), count - offset,
							AV_NOPTS_VALUE, AV_NOPTS_VALUE, position + offset);
					require(consumed >= 0 && (consumed > 0 || size.get(0) > 0), "Video parser made no progress");
					if (size.get(0) > 0)
						units = parsedUnit(parser, timing, units);
					offset += consumed;
				}
				position += count;
			}
			av_parser_parse2(parser, codec, output, size, (BytePointer) null, 0, AV_NOPTS_VALUE, AV_NOPTS_VALUE,
					position);
			if (size.get(0) > 0)
				units = parsedUnit(parser, timing, units);
			require(units == timing.pesTimings().size(), "Parsed video picture/PES count differs");
		} finally {
			if (parser != null && !parser.isNull())
				av_parser_close(parser);
			if (codec != null && !codec.isNull())
				avcodec_free_context(codec);
		}
	}

	private static int parsedUnit(AVCodecParserContext parser, StreamTiming timing, int unit) throws IOException {
		require(unit < timing.pesTimings().size()
				&& parser.frame_offset() == timing.pesTimings().get(unit).outputOffset(),
				"Parsed video access-unit boundaries disagree with timestamped PES");
		return unit + 1;
	}

	private static final class State {
		private final StreamTiming timing;
		private final boolean avc;
		private int units;
		private int pictures;
		private long previousPesOffset = -1;
		private boolean multislice;

		State(StreamTiming timing, boolean avc) {
			this.timing = timing;
			this.avc = avc;
		}

		void nal(long offset, byte[] prefix, int used) throws IOException {
			require(used >= (avc ? 1 : 2) && (prefix[0] & 0x80) == 0, "Truncated/invalid video NAL header");
			int type = avc ? prefix[0] & 31 : (prefix[0] & 0x7e) >>> 1;
			if (!avc)
				require((prefix[0] & 1) == 0 && (prefix[1] & 0xf8) == 0 && (prefix[1] & 7) != 0,
						"Multilayer/invalid HEVC access units unsupported");
			if (type == (avc ? 9 : 35)) {
				if (units > 0)
					require(pictures == 1, "Video AUD does not delimit exactly one picture");
				require(units < timing.pesTimings().size(), "Multiple video access units in one PES unsupported");
				var pes = timing.pesTimings().get(units);
				require(pes.outputOffset() > previousPesOffset && pes.outputOffset() <= offset
						&& (units + 1 == timing.pesTimings().size()
								|| timing.pesTimings().get(units + 1).outputOffset() > offset),
						"Video PES/access-unit byte offsets do not prove one timestamp per access unit");
				previousPesOffset = pes.outputOffset();
				units++;
				pictures = 0;
				return;
			}
			if (avc)
				require(type != 2 && type != 3 && type != 4 && type != 19 && type != 20 && type != 21,
						"Partitioned/extended AVC access units unsupported");
			boolean vcl = avc ? type == 1 || type == 5 : type <= 31;
			if (!vcl)
				return;
			require(avc || type <= 9 || type >= 16 && type <= 21, "Reserved HEVC picture type unsupported");
			require(units > 0, "Video access-unit proof requires AUD-delimited pictures");
			require(used >= (avc ? 2 : 3), "Truncated video slice header");
			// AVC first_mb_in_slice=0 has ue(v) prefix 1. HEVC has an explicit first-slice flag.
			boolean firstSlice = (prefix[avc ? 1 : 2] & 0x80) != 0;
			if (!firstSlice) {
				require(pictures == 1, "Non-first slice precedes the first video slice");
				multislice = true;
				return;
			}
			pictures++;
			require(pictures == 1, "Video AUD contains missing/multiple pictures or unsupported slice order");
		}

		void finish() throws IOException {
			require(units > 0 && pictures == 1 && units == timing.pesTimings().size(),
					"Video requires exactly one AUD-delimited access unit per timestamped PES");
		}
	}
}
