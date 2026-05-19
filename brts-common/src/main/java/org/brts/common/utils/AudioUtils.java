package org.brts.common.utils;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_DTS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_EAC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MP2;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_BLURAY;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S24BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_TRUEHD;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;

import java.nio.file.Path;

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
				if (stream.codecpar().codec_type() != AVMEDIA_TYPE_AUDIO) {
					continue;
				}

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

			// No audio stream found — try container-level duration as a fallback
			double duration = fmtCtx.duration() > 0 ? fmtCtx.duration() / 1_000_000.0 : -1.0;
			log.debug("probeAudioInfo: no audio stream found in '{}', container duration={}s", audioPath, duration);
			return new AudioInfo(duration, 0, 0, 0, 0);
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
	private static int codecIdToStreamTypeByte(int codecId) {
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
