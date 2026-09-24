package org.brts.lowlevel.thumbnail;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/thumbnail/VideoFrameGrabber.java' is part of BRTS.
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

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import org.brts.common.utils.FfmpegFrameConverter;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;

import lombok.extern.slf4j.Slf4j;

/**
 * Grabs a single full-resolution video frame at a given timestamp from a media file, keeping the decoder open across
 * repeated calls for fast re-seeking.
 * <p>
 * Unlike {@link ChapterThumbnailExtractor}, this class performs no thumbnail scaling and no candidate rejection: it
 * always returns the first decoded frame at or after the requested timestamp.
 */
@Slf4j
public class VideoFrameGrabber implements AutoCloseable {

	private final Path source;

	private final AVFormatContext formatCtx;

	private final AVCodecContext codecCtx;

	private final int videoStreamIndex;

	private final double videoTimeBase;

	private final FfmpegFrameConverter converter = new FfmpegFrameConverter();

	public VideoFrameGrabber(Path source) throws IOException {
		this.source = source;
		formatCtx = new AVFormatContext(null);
		int ret = avformat_open_input(formatCtx, source.toString(), null, null);
		if (ret < 0) {
			throw new IOException("avformat_open_input failed for " + source + ": " + ret);
		}
		ret = avformat_find_stream_info(formatCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
		if (ret < 0) {
			avformat_close_input(formatCtx);
			throw new IOException("avformat_find_stream_info failed for " + source + ": " + ret);
		}

		int streamIdx = -1;
		for (int i = 0; i < formatCtx.nb_streams(); i++) {
			if (formatCtx.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
				streamIdx = i;
				break;
			}
		}
		if (streamIdx < 0) {
			avformat_close_input(formatCtx);
			throw new IOException("Could not locate video stream in " + source);
		}
		videoStreamIndex = streamIdx;

		AVStream videoStream = formatCtx.streams(videoStreamIndex);
		videoTimeBase = av_q2d(videoStream.time_base());

		var codec = avcodec_find_decoder(videoStream.codecpar().codec_id());
		if (codec == null) {
			avformat_close_input(formatCtx);
			throw new IOException(
					"Could not find decoder for codec id " + videoStream.codecpar().codec_id() + " in " + source);
		}
		codecCtx = avcodec_alloc_context3(codec);
		avcodec_parameters_to_context(codecCtx, videoStream.codecpar());
		ret = avcodec_open2(codecCtx, codec, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
		if (ret < 0) {
			avcodec_free_context(codecCtx);
			avformat_close_input(formatCtx);
			throw new IOException("avcodec_open2 failed for " + source + ": " + ret);
		}
	}

	/**
	 * Seeks to {@code targetSeconds} and decodes the first frame at or after that position, at the source's native
	 * resolution.
	 * <p>
	 * {@code targetSeconds} must be expressed in the same raw, non-rebased clock as the source's own PTS (e.g. derived
	 * directly from an MPEG-TS PES PTS) — it is used as-is, with no {@code start_time} adjustment, since
	 * {@link AVStream#start_time()} is already expressed in that same absolute domain.
	 *
	 * @return the decoded frame, or {@code null} if no frame could be decoded
	 */
	public BufferedImage grabFrameAt(double targetSeconds) throws IOException {
		long targetTs = Math.round(targetSeconds / videoTimeBase);
		int ret = av_seek_frame(formatCtx, videoStreamIndex, targetTs, AVSEEK_FLAG_BACKWARD);
		if (ret < 0) {
			throw new IOException("av_seek_frame to " + targetSeconds + "s failed for " + source + ": " + ret);
		}
		avcodec_flush_buffers(codecCtx);

		AVPacket packet = av_packet_alloc();
		AVFrame frame = av_frame_alloc();
		try {
			while (av_read_frame(formatCtx, packet) >= 0) {
				if (packet.stream_index() != videoStreamIndex) {
					av_packet_unref(packet);
					continue;
				}
				int sendRet = avcodec_send_packet(codecCtx, packet);
				av_packet_unref(packet);
				if (sendRet < 0) {
					continue;
				}
				while (avcodec_receive_frame(codecCtx, frame) >= 0) {
					long pts = frame.best_effort_timestamp();
					if (pts != AV_NOPTS_VALUE && pts < targetTs) {
						continue;
					}
					return converter.toBufferedImage(frame);
				}
			}

			// Flush the decoder in case the target lies in the trailing frames
			avcodec_send_packet(codecCtx, (AVPacket) null);
			while (avcodec_receive_frame(codecCtx, frame) >= 0) {
				long pts = frame.best_effort_timestamp();
				if (pts != AV_NOPTS_VALUE && pts < targetTs) {
					continue;
				}
				return converter.toBufferedImage(frame);
			}
			return null;
		} finally {
			av_frame_free(frame);
			av_packet_free(packet);
		}
	}

	@Override
	public void close() {
		converter.close();
		avcodec_free_context(codecCtx);
		avformat_close_input(formatCtx);
	}

}
