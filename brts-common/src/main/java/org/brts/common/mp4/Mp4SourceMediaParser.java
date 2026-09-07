package org.brts.common.mp4;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.exception.ParseException;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.mkv.SourceMediaParser;
import org.brts.common.model.StreamCodingType;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry;
import org.bytedeco.javacpp.BytePointer;

import lombok.extern.slf4j.Slf4j;

/**
 * MP4 (and MOV) implementation of {@link SourceMediaParser} using the ByteDeco FFmpeg bindings.
 * <p>
 * Probes the container via {@code avformat_open_input} / {@code avformat_find_stream_info} to extract per-stream
 * metadata (codec, resolution, frame rate, audio channels, sample rate, language) without demuxing frames.
 * <p>
 * Codec mapping from FFmpeg codec IDs to Blu-ray {@link StreamCodingType}:
 * <ul>
 * <li>AV_CODEC_ID_H264 → H264_AVC</li>
 * <li>AV_CODEC_ID_HEVC → H265_HEVC</li>
 * <li>AV_CODEC_ID_MPEG2VIDEO → MPEG2_VIDEO</li>
 * <li>AV_CODEC_ID_VC1 → VC1</li>
 * <li>AV_CODEC_ID_AC3 → DOLBY_AC3</li>
 * <li>AV_CODEC_ID_EAC3 → DOLBY_AC3_PLUS</li>
 * <li>AV_CODEC_ID_TRUEHD → DOLBY_TRUEHD</li>
 * <li>AV_CODEC_ID_DTS + AV_PROFILE_DTS_HD_MA → DTS_HD_MASTER_AUDIO</li>
 * <li>AV_CODEC_ID_DTS + AV_PROFILE_DTS_HD_HRA → DTS_HD</li>
 * <li>AV_CODEC_ID_DTS (other profiles) → DTS</li>
 * <li>AV_CODEC_ID_PCM_S16BE / PCM_S24BE / PCM_BLURAY → LPCM</li>
 * <li>AV_CODEC_ID_HDMV_PGS_SUBTITLE → PRESENTATION_GRAPHICS</li>
 * <li>AV_CODEC_ID_ASS / SUBRIP / MOV_TEXT / TEXT → TEXT_SUBTITLE</li>
 * </ul>
 */
@Slf4j
public class Mp4SourceMediaParser implements SourceMediaParser {

	@Override
	public String[] supportedExtensions() {
		return new String[] { "mp4", "m4v", "m4a", "mov" };
	}

	@Override
	public SourceMediaInfo parse(Path path) throws IOException {
		log.info("Parsing MP4: {}", path);

		AVFormatContext fmtCtx = new AVFormatContext(null);
		int ret = avformat_open_input(fmtCtx, path.toAbsolutePath().toString(), null, null);
		if (ret < 0) {
			throw new IOException("avformat_open_input failed for '" + path + "': " + ret);
		}

		try {
			ret = avformat_find_stream_info(fmtCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
			if (ret < 0) {
				throw new IOException("avformat_find_stream_info failed for '" + path + "': " + ret);
			}

			SourceMediaInfo info = new SourceMediaInfo();
			info.setSourcePath(path.toAbsolutePath().toString());

			// AV_TIME_BASE is 1,000,000 µs/s → divide by 1000 to get ms
			long durationMs = fmtCtx.duration() > 0 ? fmtCtx.duration() / 1000L : 0L;
			info.setDurationMs(durationMs);
			log.debug("Duration: {} µs = {} ms", fmtCtx.duration(), durationMs);

			List<SourceMediaInfo.SourceTrack> tracks = new ArrayList<>();

			for (int i = 0; i < fmtCtx.nb_streams(); i++) {
				AVStream stream = fmtCtx.streams(i);
				int codecId = stream.codecpar().codec_id();
				int profile = stream.codecpar().profile();
				int mediaType = stream.codecpar().codec_type();

				StreamCodingType codingType;
				try {
					codingType = mapCodecId(codecId, profile);
				} catch (ParseException e) {
					log.warn("Stream {}: unsupported codec id {} (mediaType={}), skipping", i, codecId, mediaType);
					continue;
				}

				SourceMediaInfo.SourceTrack track = new SourceMediaInfo.SourceTrack();
				// MP4 streams are 0-based internally; expose as 1-based to match MKV convention
				track.setTrackNumber(i + 1);
				track.setCodingType(codingType);

				// Language tag
				String language = readTag(stream, "language");
				if (language != null && !language.isBlank()) {
					track.setLanguage(language);
				}

				// Track name
				String title = readTag(stream, "title");
				if (title != null && !title.isBlank()) {
					track.setTrackName(title);
				}

				// Codec private / extradata
				BytePointer extradata = stream.codecpar().extradata();
				int extradataSize = stream.codecpar().extradata_size();
				if (extradata != null && !extradata.isNull() && extradataSize > 0) {
					byte[] cpBytes = new byte[extradataSize];
					extradata.get(cpBytes);
					track.setCodecPrivate(cpBytes);
				}

				if (mediaType == AVMEDIA_TYPE_VIDEO) {
					track.setWidthPixels(stream.codecpar().width());
					track.setHeightPixels(stream.codecpar().height());
					double fps = av_q2d(stream.avg_frame_rate());
					if (fps > 0) {
						track.setFrameRateFps(fps);
					}
					log.debug("Video stream {}: codec={}, {}×{}, fps={}", i, codecId, track.getWidthPixels(),
							track.getHeightPixels(), track.getFrameRateFps());

				} else if (mediaType == AVMEDIA_TYPE_AUDIO) {
					track.setChannels(stream.codecpar().ch_layout().nb_channels());
					track.setSampleRateHz(stream.codecpar().sample_rate());
					long bitRate = stream.codecpar().bit_rate();
					if (bitRate > 0) {
						track.setBitrateKbps((int) (bitRate / 1000));
					}
					log.debug("Audio stream {}: codec={}, lang={}, {}ch@{}Hz, {} kbps", i, codecId, track.getLanguage(),
							track.getChannels(), track.getSampleRateHz(), track.getBitrateKbps());

				} else if (mediaType == AVMEDIA_TYPE_SUBTITLE) {
					track.setSubtitleFormat(deriveSubtitleFormat(codecId));
					log.debug("Subtitle stream {}: codec={}, lang={}, format={}", i, codecId, track.getLanguage(),
							track.getSubtitleFormat());
				}

				tracks.add(track);
			}

			info.setTracks(tracks);
			log.info("Parsed {} tracks from {}", tracks.size(), path.getFileName());
			return info;

		} finally {
			avformat_close_input(fmtCtx);
		}
	}

	// -------------------------------------------------------------------------
	// Codec mapping
	// -------------------------------------------------------------------------

	static StreamCodingType mapCodecId(int codecId, int profile) {
		if (codecId == AV_CODEC_ID_H264) {
			return StreamCodingType.H264_AVC;
		}
		if (codecId == AV_CODEC_ID_HEVC) {
			return StreamCodingType.H265_HEVC;
		}
		if (codecId == AV_CODEC_ID_MPEG2VIDEO) {
			return StreamCodingType.MPEG2_VIDEO;
		}
		if (codecId == AV_CODEC_ID_VC1) {
			return StreamCodingType.VC1;
		}
		if (codecId == AV_CODEC_ID_AC3) {
			return StreamCodingType.DOLBY_AC3;
		}
		if (codecId == AV_CODEC_ID_EAC3) {
			return StreamCodingType.DOLBY_AC3_PLUS;
		}
		if (codecId == AV_CODEC_ID_TRUEHD) {
			return StreamCodingType.DOLBY_TRUEHD;
		}
		if (codecId == AV_CODEC_ID_DTS) {
			if (profile == AV_PROFILE_DTS_HD_MA) {
				return StreamCodingType.DTS_HD_MASTER_AUDIO;
			}
			if (profile == AV_PROFILE_DTS_HD_HRA) {
				return StreamCodingType.DTS_HD;
			}
			return StreamCodingType.DTS;
		}
		if (codecId == AV_CODEC_ID_PCM_S16BE || codecId == AV_CODEC_ID_PCM_S24BE || codecId == AV_CODEC_ID_PCM_BLURAY) {
			return StreamCodingType.LPCM;
		}
		if (codecId == AV_CODEC_ID_HDMV_PGS_SUBTITLE) {
			return StreamCodingType.PRESENTATION_GRAPHICS;
		}
		if (codecId == AV_CODEC_ID_ASS || codecId == AV_CODEC_ID_SUBRIP || codecId == AV_CODEC_ID_MOV_TEXT
				|| codecId == AV_CODEC_ID_TEXT) {
			return StreamCodingType.TEXT_SUBTITLE;
		}
		throw new ParseException("Unsupported MP4 codec ID: " + codecId);
	}

	/**
	 * Maps an FFmpeg subtitle codec ID to a short human-readable format string stored in
	 * {@link SourceMediaInfo.SourceTrack#getSubtitleFormat()}.
	 */
	static String deriveSubtitleFormat(int codecId) {
		if (codecId == AV_CODEC_ID_HDMV_PGS_SUBTITLE) {
			return "PGS";
		}
		if (codecId == AV_CODEC_ID_ASS) {
			return "ASS";
		}
		if (codecId == AV_CODEC_ID_SUBRIP) {
			return "SRT";
		}
		if (codecId == AV_CODEC_ID_MOV_TEXT || codecId == AV_CODEC_ID_TEXT) {
			return "TX3G";
		}
		return "UNKNOWN";
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	/**
	 * Reads a metadata tag from a stream's dictionary. Returns {@code null} if the tag is absent or the dictionary is
	 * null.
	 */
	private static String readTag(AVStream stream, String key) {
		if (stream.metadata() == null || stream.metadata().isNull()) {
			return null;
		}
		AVDictionaryEntry entry = av_dict_get(stream.metadata(), key, null, 0);
		if (entry == null || entry.isNull()) {
			return null;
		}
		BytePointer valuePtr = entry.value();
		if (valuePtr == null || valuePtr.isNull()) {
			return null;
		}
		return valuePtr.getString();
	}

}
