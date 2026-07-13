package org.brts.common.mkv;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_ASS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_DTS;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_EAC3;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG2VIDEO;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_BLURAY;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S24BE;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_SSA;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_SUBRIP;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_TRUEHD;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VC1;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.brts.common.utils.StringUtils;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.javacpp.BytePointer;

import lombok.extern.slf4j.Slf4j;

/**
 * Demuxes MKV containers into elementary streams using FFmpeg.
 */
@Slf4j
public class FfmpegDemuxer implements EsDemuxer {

	private static final byte[] ANNEX_B_START_CODE = { 0x00, 0x00, 0x00, 0x01 };

	private static final byte[] SSA_DIALOGUE = "Dialogue: ".getBytes(StandardCharsets.UTF_8);

	@Override
	public Map<Integer, Path> demux(Path mkvPath, Path outputDir) throws IOException {
		return demux(mkvPath, outputDir, null);
	}

	@Override
	public Map<Integer, Path> demux(Path mkvPath, Path outputDir, Set<Integer> trackFilter) throws IOException {
		Files.createDirectories(outputDir);
		Map<Integer, Path> result = new LinkedHashMap<>();

		AVFormatContext fmtCtx = new AVFormatContext(null);
		int ret = avformat_open_input(fmtCtx, mkvPath.toString(), null, null);
		if (ret < 0) {
			throw new IOException("avformat_open_input failed for '" + mkvPath + "': " + ret);
		}

		Map<Integer, OutputStream> outputsByStreamIndex = new HashMap<>();
		Map<Integer, Integer> trackNoByStreamIndex = new HashMap<>();
		Map<Integer, Integer> h264NalLenSizeByStreamIndex = new HashMap<>();

		try {
			ret = avformat_find_stream_info(fmtCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
			if (ret < 0) {
				throw new IOException("avformat_find_stream_info failed for '" + mkvPath + "': " + ret);
			}

			for (int i = 0; i < fmtCtx.nb_streams(); i++) {
				AVStream stream = fmtCtx.streams(i);
				int trackNo = stream.id() > 0 ? stream.id() : i + 1;
				if (trackFilter != null && !trackFilter.contains(trackNo)) {
					continue;
				}

				int codecId = stream.codecpar().codec_id();
				String ext = extensionForCodecId(codecId);
				Path outFile = outputDir.resolve("track_" + trackNo + "." + ext);
				OutputStream os = new BufferedOutputStream(Files.newOutputStream(outFile), 1 << 16);

				result.put(trackNo, outFile);
				outputsByStreamIndex.put(i, os);
				trackNoByStreamIndex.put(i, trackNo);

				if (codecId == AV_CODEC_ID_H264) {
					byte[] extradata = getExtradata(stream);
					int nalLenSize = parseNalLengthSizeFromAvcc(extradata);
					h264NalLenSizeByStreamIndex.put(i, nalLenSize);
					writeAvcParameterSets(extradata, os);
				} else if (isTextSubtitleCodec(codecId)) {
					byte[] extradata = getExtradata(stream);
					if (extradata.length > 0) {
						os.write(extradata);
					}
				}
			}

			long packetCount = 0;
			AVPacket packet = av_packet_alloc();
			try {
				while (av_read_frame(fmtCtx, packet) >= 0) {
					int streamIndex = packet.stream_index();
					OutputStream os = outputsByStreamIndex.get(streamIndex);
					if (os == null) {
						av_packet_unref(packet);
						continue;
					}

					AVStream stream = fmtCtx.streams(streamIndex);
					int codecId = stream.codecpar().codec_id();
					int size = packet.size();
					if (size > 0 && packet.data() != null) {
						byte[] data = toByteArray(packet.data(), size);
						if (codecId == AV_CODEC_ID_H264) {
							int nalLenSize = h264NalLenSizeByStreamIndex.getOrDefault(streamIndex, 4);
							writeH264AsAnnexB(data, nalLenSize, os);
						} else if (codecId == AV_CODEC_ID_ASS || codecId == AV_CODEC_ID_SSA) {
							writeSsaPacket(packet, stream, data, os);
						} else {
							os.write(data);
						}
					}

					packetCount++;
					av_packet_unref(packet);
				}
			} finally {
				av_packet_free(packet);
			}

			log.info("Demuxed {} packets from {} tracks in {}", packetCount, result.size(), mkvPath.getFileName());
			return result;
		} finally {
			for (Map.Entry<Integer, OutputStream> entry : outputsByStreamIndex.entrySet()) {
				try {
					entry.getValue().close();
				} catch (IOException closeException) {
					Integer trackNo = trackNoByStreamIndex.get(entry.getKey());
					log.warn("Failed to close output stream for track {}: {}", trackNo, closeException.getMessage());
				}
			}
			avformat_close_input(fmtCtx);
		}
	}

	private static byte[] toByteArray(BytePointer data, int size) {
		byte[] out = new byte[size];
		data.position(0).get(out);
		return out;
	}

	private static byte[] getExtradata(AVStream stream) {
		if (stream.codecpar() == null || stream.codecpar().extradata() == null
				|| stream.codecpar().extradata_size() <= 0) {
			return new byte[0];
		}
		return toByteArray(stream.codecpar().extradata(), stream.codecpar().extradata_size());
	}

	private static int parseNalLengthSizeFromAvcc(byte[] extradata) {
		if (extradata.length >= 6 && (extradata[0] & 0xFF) == 1) {
			return (extradata[4] & 0x03) + 1;
		}
		return 4;
	}

	private void writeAvcParameterSets(byte[] extradata, OutputStream os) throws IOException {
		if (extradata.length < 7 || (extradata[0] & 0xFF) != 1) {
			return;
		}

		ByteBuffer cp = ByteBuffer.wrap(extradata);
		cp.get();
		cp.get();
		cp.get();
		cp.get();
		cp.get();

		int numSps = cp.get() & 0x1F;
		for (int i = 0; i < numSps; i++) {
			if (cp.remaining() < 2) {
				return;
			}
			int spsLen = ((cp.get() & 0xFF) << 8) | (cp.get() & 0xFF);
			if (spsLen <= 0 || cp.remaining() < spsLen) {
				return;
			}
			byte[] sps = new byte[spsLen];
			cp.get(sps);
			os.write(ANNEX_B_START_CODE);
			os.write(sps);
		}

		if (!cp.hasRemaining()) {
			return;
		}
		int numPps = cp.get() & 0xFF;
		for (int i = 0; i < numPps; i++) {
			if (cp.remaining() < 2) {
				return;
			}
			int ppsLen = ((cp.get() & 0xFF) << 8) | (cp.get() & 0xFF);
			if (ppsLen <= 0 || cp.remaining() < ppsLen) {
				return;
			}
			byte[] pps = new byte[ppsLen];
			cp.get(pps);
			os.write(ANNEX_B_START_CODE);
			os.write(pps);
		}
	}

	private void writeH264AsAnnexB(byte[] packetData, int nalLenSize, OutputStream os) throws IOException {
		ByteBuffer buf = ByteBuffer.wrap(packetData);
		if (isAnnexBFormat(buf)) {
			os.write(packetData);
			return;
		}

		while (buf.remaining() >= nalLenSize) {
			int nalLen = 0;
			for (int i = 0; i < nalLenSize; i++) {
				nalLen = (nalLen << 8) | (buf.get() & 0xFF);
			}
			if (nalLen <= 0 || nalLen > buf.remaining()) {
				break;
			}
			byte[] nalData = new byte[nalLen];
			buf.get(nalData);
			os.write(ANNEX_B_START_CODE);
			os.write(nalData);
		}
	}

	private void writeSsaPacket(AVPacket packet, AVStream stream, byte[] data, OutputStream os) throws IOException {
		if (StringUtils.startsWith(data, SSA_DIALOGUE)) {
			os.write(data);
			return;
		}

		int pos = StringUtils.pos(data, (byte) ',', 2);
		if (pos < 0) {
			os.write(data);
			return;
		}

		long startMs = toMillis(packet.pts(), stream);
		long durationMs = packet.duration() > 0 ? toMillis(packet.duration(), stream) : 0;
		long endMs = startMs + durationMs;

		os.write(SSA_DIALOGUE);
		os.write('0');
		os.write(',');
		os.write(timestampFrom(startMs).getBytes(StandardCharsets.UTF_8));
		os.write(',');
		os.write(timestampFrom(endMs).getBytes(StandardCharsets.UTF_8));
		os.write(data, pos, data.length - pos);
		os.write('\n');
	}

	private static long toMillis(long value, AVStream stream) {
		if (value <= 0) {
			return 0;
		}
		double ms = value * av_q2d(stream.time_base()) * 1000.0;
		return (long) ms;
	}

	private static String timestampFrom(long timecode) {
		long totalSeconds = timecode / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		long milliseconds = (timecode % 1000) / 10;
		return String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, milliseconds);
	}

	private static boolean isAnnexBFormat(ByteBuffer buf) {
		int pos = buf.position();
		int rem = buf.remaining();
		if (rem >= 4 && (buf.get(pos) & 0xFF) == 0x00 && (buf.get(pos + 1) & 0xFF) == 0x00
				&& (buf.get(pos + 2) & 0xFF) == 0x00 && (buf.get(pos + 3) & 0xFF) == 0x01) {
			return true;
		}
		if (rem >= 3 && (buf.get(pos) & 0xFF) == 0x00 && (buf.get(pos + 1) & 0xFF) == 0x00
				&& (buf.get(pos + 2) & 0xFF) == 0x01) {
			return true;
		}
		return false;
	}

	private static boolean isTextSubtitleCodec(int codecId) {
		return codecId == AV_CODEC_ID_ASS || codecId == AV_CODEC_ID_SSA || codecId == AV_CODEC_ID_SUBRIP;
	}

	private static String extensionForCodecId(int codecId) {
		if (codecId == AV_CODEC_ID_H264) {
			return "h264";
		}
		if (codecId == AV_CODEC_ID_HEVC) {
			return "h265";
		}
		if (codecId == AV_CODEC_ID_MPEG2VIDEO) {
			return "m2v";
		}
		if (codecId == AV_CODEC_ID_VC1) {
			return "vc1";
		}
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
		if (codecId == AV_CODEC_ID_SUBRIP) {
			return "srt";
		}
		if (codecId == AV_CODEC_ID_ASS) {
			return "ass";
		}
		if (codecId == AV_CODEC_ID_SSA) {
			return "ssa";
		}
		return "bin";
	}
}
