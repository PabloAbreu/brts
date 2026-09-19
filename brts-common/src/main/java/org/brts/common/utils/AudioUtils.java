package org.brts.common.utils;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-common/src/main/java/org/brts/common/utils/AudioUtils.java' is part of BRTS.
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;

import lombok.extern.slf4j.Slf4j;

/**
 * Utilities for probing audio elementary-stream files via FFmpeg.
 */
@Slf4j
public class AudioUtils {

	/**
	 * Result of probing an audio file.
	 *
	 * <ul>
	 * <li>{@link #durationSeconds} — total duration; {@code -1.0} if it could not be determined.</li>
	 * <li>{@link #streamTypeByte} — ISO 13818-1 / Blu-ray stream_type byte; {@code 0} if the codec is unknown or
	 * unmapped.</li>
	 * <li>{@link #channels} — number of audio channels; {@code 0} if unknown.</li>
	 * <li>{@link #sampleRateHz} — sample rate in Hz; {@code 0} if unknown.</li>
	 * <li>{@link #bitrateKbps} — bitrate in kbps; {@code 0} if unknown.</li>
	 * </ul>
	 */
	public record AudioInfo(double durationSeconds, int streamTypeByte, int channels, int sampleRateHz,
			int bitrateKbps) {
	}

	/** Sentinel returned when the probe fails completely. */
	private static final AudioInfo PROBE_FAILED = new AudioInfo(-1.0, 0, 0, 0, 0);

	/**
	 * Probes an audio file and returns format/codec information.
	 *
	 * <p>
	 * Uses FFmpeg's {@code avformat_open_input} / {@code avformat_find_stream_info} to inspect the first audio stream
	 * in the file. All fields are filled with best-effort values; absent information is represented as {@code -1.0}
	 * (duration) or {@code 0} (integer fields). This method never throws.
	 *
	 * @param audioPath path to the audio file (elementary stream or container)
	 * @return probed audio info, or a sentinel with {@code durationSeconds=-1.0} and all-zero integer fields on failure
	 */
	public static AudioInfo probeAudioInfo(Path audioPath) {
		AVFormatContext fmtCtx = new AVFormatContext(null);
		int ret = avformat_open_input(fmtCtx, audioPath.toString(), null, null);
		if (ret < 0) {
			log.debug("probeAudioInfo: avformat_open_input failed for '{}': {}", audioPath, ret);
			return PROBE_FAILED;
		}
		try {
			ret = avformat_find_stream_info(fmtCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
			if (ret < 0) {
				log.debug("probeAudioInfo: avformat_find_stream_info failed for '{}': {}", audioPath, ret);
				return PROBE_FAILED;
			}

			for (int i = 0; i < fmtCtx.nb_streams(); i++) {
				AVStream stream = fmtCtx.streams(i);
				if (stream.codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
					double duration = -1.0;
					if (stream.duration() > 0) {
						duration = stream.duration() * av_q2d(stream.time_base());
					} else if (fmtCtx.duration() > 0) {
						duration = fmtCtx.duration() / 1_000_000.0;
					}

					int codecId = stream.codecpar().codec_id();
					int streamTypeByte = codecIdToStreamTypeByte(codecId);
					int channels = stream.codecpar().ch_layout().nb_channels();
					int sampleRate = stream.codecpar().sample_rate();
					long bitRate = stream.codecpar().bit_rate();
					int bitrateKbps = bitRate > 0 ? (int) (bitRate / 1000) : 0;

					log.debug("probeAudioInfo: '{}' codecId={} -> streamType=0x{} ch={} sampleRate={} bitrateKbps={}",
							audioPath, codecId, Integer.toHexString(streamTypeByte), channels, sampleRate, bitrateKbps);

					return new AudioInfo(duration, streamTypeByte, channels, sampleRate, bitrateKbps);
				}
			}

			// No audio stream found — try container-level duration as a fallback
			double duration = fmtCtx.duration() > 0 ? fmtCtx.duration() / 1_000_000.0 : -1.0;
			log.debug("probeAudioInfo: no audio stream found in '{}', container duration={}s", audioPath, duration);
			return new AudioInfo(duration, 0, 0, 0, 0);
		} finally {
			avformat_close_input(fmtCtx);
		}
	}

	/**
	 * Copies the first audio stream of {@code sourceEs} into {@code destEs}, stopping once the packet timestamp reaches
	 * {@code maxDurationSeconds}. No re-encoding is performed (raw packet copy), so the cut lands on the nearest packet
	 * boundary rather than an exact sample.
	 *
	 * @param sourceEs           path to the source audio elementary-stream file
	 * @param destEs             path to write the trimmed elementary-stream file to
	 * @param maxDurationSeconds maximum duration to keep, in seconds; must be {@code > 0}
	 * @throws IOException if the source cannot be opened/demuxed or the destination cannot be written
	 */
	public static void trimToDuration(Path sourceEs, Path destEs, double maxDurationSeconds) throws IOException {
		AVFormatContext fmtCtx = new AVFormatContext(null);
		int ret = avformat_open_input(fmtCtx, sourceEs.toString(), null, null);
		if (ret < 0) {
			throw new IOException("avformat_open_input failed for '" + sourceEs + "': " + ret);
		}
		try {
			ret = avformat_find_stream_info(fmtCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
			if (ret < 0) {
				throw new IOException("avformat_find_stream_info failed for '" + sourceEs + "': " + ret);
			}

			int audioStreamIndex = -1;
			for (int i = 0; i < fmtCtx.nb_streams(); i++) {
				if (fmtCtx.streams(i).codecpar().codec_type() == AVMEDIA_TYPE_AUDIO) {
					audioStreamIndex = i;
					break;
				}
			}
			if (audioStreamIndex < 0) {
				throw new IOException("No audio stream found in '" + sourceEs + "'");
			}

			AVStream stream = fmtCtx.streams(audioStreamIndex);
			double timeBase = av_q2d(stream.time_base());

			AVPacket packet = av_packet_alloc();
			try (OutputStream out = Files.newOutputStream(destEs)) {
				while (av_read_frame(fmtCtx, packet) >= 0) {
					if (packet.stream_index() == audioStreamIndex) {
						double ptsSeconds = packet.pts() * timeBase;
						if (ptsSeconds >= maxDurationSeconds) {
							av_packet_unref(packet);
							break;
						}
						int size = packet.size();
						if (size > 0) {
							byte[] data = new byte[size];
							packet.data().position(0).get(data);
							out.write(data);
						}
					}
					av_packet_unref(packet);
				}
			} finally {
				av_packet_free(packet);
			}
			log.debug("trimToDuration: wrote '{}' truncated to {}s from '{}'", destEs, maxDurationSeconds, sourceEs);
		} finally {
			avformat_close_input(fmtCtx);
		}
	}

	/**
	 * Maps an FFmpeg {@code AVCodecID} to the corresponding Blu-ray / ISO 13818-1 stream_type byte.
	 *
	 * @param codecId FFmpeg codec identifier
	 * @return the stream_type byte, or {@code 0} if the codec is not mapped
	 */
	static int codecIdToStreamTypeByte(int codecId) {
		if (codecId == AV_CODEC_ID_AC3) {
			return 0x81; // Dolby Digital AC-3
		}
		if (codecId == AV_CODEC_ID_EAC3) {
			return 0x84; // Dolby Digital Plus / AC-3+
		}
		if (codecId == AV_CODEC_ID_TRUEHD) {
			return 0x83; // Dolby TrueHD
		}
		if (codecId == AV_CODEC_ID_DTS) {
			return 0x82; // DTS (including DTS-HD — no profile-level distinction here)
		}
		if (codecId == AV_CODEC_ID_PCM_BLURAY || codecId == AV_CODEC_ID_PCM_S16BE || codecId == AV_CODEC_ID_PCM_S24BE) {
			return 0x80; // LPCM
		}
		if (codecId == AV_CODEC_ID_MP2) {
			return 0x04; // MPEG-2 Audio Layer II
		}
		return 0;
	}

}
