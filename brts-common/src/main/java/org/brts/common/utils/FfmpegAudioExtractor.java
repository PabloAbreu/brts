package org.brts.common.utils;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_DTS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_EAC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MP2;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_BLURAY;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S24BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_TRUEHD;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_get;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry;

import lombok.extern.slf4j.Slf4j;

/**
 * Extracts audio elementary streams from any container format supported by FFmpeg (MKV, MP4, M2TS, …).
 *
 * <p>
 * Uses a single-pass {@code av_read_frame} loop to write the raw packet data for every audio stream into separate
 * output files. No decoding is performed; the packets are written as-is.
 *
 * <p>
 * This replaces the M2TS-specific {@code M2tsParser} + {@code M2tsExtractor} path in {@code CompositedVideoGenerator}
 * and supports any format that FFmpeg can demux.
 */
@Slf4j
public class FfmpegAudioExtractor {

	/**
	 * Metadata and path of a single extracted audio elementary stream.
	 *
	 * @param esFile         path to the written elementary-stream file
	 * @param streamTypeByte Blu-ray / ISO 13818-1 stream_type byte; {@code 0} if the codec is not mapped
	 * @param language       ISO 639-2 language tag from the container metadata, or {@code null} if absent
	 * @param channels       number of audio channels; {@code 0} if unknown
	 * @param sampleRateHz   sample rate in Hz; {@code 0} if unknown
	 * @param bitrateKbps    bitrate in kbps; {@code 0} if unknown
	 */
	public record ExtractedAudio(Path esFile, int streamTypeByte, String language, int channels, int sampleRateHz,
			int bitrateKbps) {
	}

	/**
	 * Extracts all audio streams from {@code sourceVideo} into individual elementary-stream files inside
	 * {@code outputDir}.
	 *
	 * <p>
	 * Output files are named {@code audio_N.<ext>} where {@code N} is a zero-based audio stream index and {@code ext}
	 * is derived from the codec (e.g. {@code ac3}, {@code dts}, {@code lpcm}).
	 *
	 * <p>
	 * Streams whose codec is not mapped to a Blu-ray stream_type byte are skipped with a warning.
	 *
	 * @param sourceVideo path to the source video file (any FFmpeg-supported format)
	 * @param outputDir   directory where extracted ES files will be written; must exist
	 * @return list of extracted audio stream descriptors, in stream order; may be empty
	 * @throws IOException if opening the file or writing output fails
	 */
	public List<ExtractedAudio> extract(Path sourceVideo, Path outputDir) throws IOException {
		AVFormatContext fmtCtx = new AVFormatContext(null);
		int ret = avformat_open_input(fmtCtx, sourceVideo.toString(), null, null);
		if (ret < 0) {
			throw new IOException("avformat_open_input failed for '" + sourceVideo + "': " + ret);
		}

		try {
			ret = avformat_find_stream_info(fmtCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
			if (ret < 0) {
				throw new IOException("avformat_find_stream_info failed for '" + sourceVideo + "': " + ret);
			}

			// Enumerate audio streams and open one output file per stream
			Map<Integer, OutputStream> outputsByIndex = new HashMap<>();
			List<ExtractedAudio> results = new ArrayList<>();
			int audioIndex = 0;

			for (int i = 0; i < fmtCtx.nb_streams(); i++) {
				AVStream stream = fmtCtx.streams(i);
				if (stream.codecpar().codec_type() != AVMEDIA_TYPE_AUDIO) {
					continue;
				}

				int codecId = stream.codecpar().codec_id();
				int streamTypeByte = AudioUtils.codecIdToStreamTypeByte(codecId);
				if (streamTypeByte == 0) {
					log.warn("Skipping audio stream {} in '{}': codec id {} has no Blu-ray stream_type mapping", i,
							sourceVideo.getFileName(), codecId);
					audioIndex++;
					continue;
				}

				String ext = extensionForCodecId(codecId);
				Path esFile = outputDir.resolve("audio_" + audioIndex + "." + ext);

				String language = null;
				AVDictionaryEntry langEntry = av_dict_get(stream.metadata(), "language", null, 0);
				if (langEntry != null && langEntry.value() != null) {
					language = langEntry.value().getString();
				}

				int channels = stream.codecpar().ch_layout().nb_channels();
				int sampleRate = stream.codecpar().sample_rate();
				long bitRate = stream.codecpar().bit_rate();
				int bitrateKbps = bitRate > 0 ? (int) (bitRate / 1000) : 0;

				double duration = -1.0;
				if (stream.duration() > 0) {
					duration = stream.duration() * av_q2d(stream.time_base());
				} else if (fmtCtx.duration() > 0) {
					duration = fmtCtx.duration() / 1_000_000.0;
				}

				log.debug(
						"Audio stream {} in '{}': codecId={} -> type=0x{} lang={} ch={} sampleRate={} bitrate={}kbps duration={}s",
						i, sourceVideo.getFileName(), codecId, Integer.toHexString(streamTypeByte), language, channels,
						sampleRate, bitrateKbps, duration);

				outputsByIndex.put(i, Files.newOutputStream(esFile));
				results.add(new ExtractedAudio(esFile, streamTypeByte, language, channels, sampleRate, bitrateKbps));
				audioIndex++;
			}

			if (outputsByIndex.isEmpty()) {
				log.debug("No extractable audio streams found in '{}'", sourceVideo.getFileName());
				return results;
			}

			// Single-pass demux: write raw packet bytes to each stream's output file
			AVPacket packet = av_packet_alloc();
			try {
				while (av_read_frame(fmtCtx, packet) >= 0) {
					OutputStream out = outputsByIndex.get(packet.stream_index());
					if (out != null) {
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
				for (OutputStream out : outputsByIndex.values()) {
					try {
						out.close();
					} catch (IOException ignored) {
					}
				}
			}

			log.info("Extracted {} audio stream(s) from '{}'", results.size(), sourceVideo.getFileName());
			return results;

		} finally {
			avformat_close_input(fmtCtx);
		}
	}

	/**
	 * Returns a file extension appropriate for the given FFmpeg codec ID.
	 */
	private static String extensionForCodecId(int codecId) {
		if (codecId == AV_CODEC_ID_AC3) {
			return "ac3";
		}
		if (codecId == AV_CODEC_ID_EAC3) {
			return "eac3";
		}
		if (codecId == AV_CODEC_ID_TRUEHD) {
			return "thd";
		}
		if (codecId == AV_CODEC_ID_DTS) {
			return "dts";
		}
		if (codecId == AV_CODEC_ID_PCM_BLURAY || codecId == AV_CODEC_ID_PCM_S16BE || codecId == AV_CODEC_ID_PCM_S24BE) {
			return "lpcm";
		}
		if (codecId == AV_CODEC_ID_MP2) {
			return "mp2";
		}
		return "bin";
	}

}
