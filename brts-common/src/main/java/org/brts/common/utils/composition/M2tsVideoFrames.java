package org.brts.common.utils.composition;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_flush_buffers;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_to_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet;
import static org.bytedeco.ffmpeg.global.avformat.AVSEEK_FLAG_BACKWARD;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_seek_frame;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;

/**
 * {@link VideoFrames} implementation that decodes frames from an M2TS file using
 * bytedeco/FFmpeg.
 * <p>
 * Frames are decoded sequentially on demand. An LRU cache avoids redundant decoding when
 * the same frame number is requested repeatedly.
 */
public class M2tsVideoFrames implements VideoFrames {

	private static final int CACHE_CAPACITY = 300;

	private final AVFormatContext formatCtx;

	private final AVCodecContext codecCtx;

	private final int videoStreamIndex;

	private final int frameCount;

	private SwsContext swsCtx;

	private int currentFrameIndex = -1;

	private final LinkedHashMap<Integer, BufferedImage> cache = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<Integer, BufferedImage> eldest) {
			return size() > CACHE_CAPACITY;
		}
	};

	public M2tsVideoFrames(Path m2tsPath) throws IOException {
		// 1. Parse M2TS to find first H264_AVC stream PID
		M2tsParser parser = new M2tsParser();
		M2tsInfo info = parser.parse(m2tsPath);
		int pid = info.getStreams()
			.stream()
			.filter(s -> s.getCodingType() == StreamCodingType.H264_AVC)
			.findFirst()
			.map(M2tsStreamInfo::getPid)
			.orElseThrow(() -> new IOException("No H264_AVC stream found in " + m2tsPath));

		// 2. Open file with FFmpeg
		formatCtx = new AVFormatContext(null);
		int ret = avformat_open_input(formatCtx, m2tsPath.toString(), null, null);
		if (ret < 0) {
			throw new IOException("avformat_open_input failed: " + ret);
		}

		ret = avformat_find_stream_info(formatCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
		if (ret < 0) {
			avformat_close_input(formatCtx);
			throw new IOException("avformat_find_stream_info failed: " + ret);
		}

		// 3. Find the video stream matching the PID, fall back to first H264 stream
		int streamIdx = -1;
		for (int i = 0; i < formatCtx.nb_streams(); i++) {
			AVStream stream = formatCtx.streams(i);
			if (stream.id() == pid) {
				streamIdx = i;
				break;
			}
		}
		if (streamIdx < 0) {
			// Fallback: first video stream with H264 codec
			for (int i = 0; i < formatCtx.nb_streams(); i++) {
				AVStream stream = formatCtx.streams(i);
				if (stream.codecpar().codec_id() == AV_CODEC_ID_H264) {
					streamIdx = i;
					break;
				}
			}
		}
		if (streamIdx < 0) {
			avformat_close_input(formatCtx);
			throw new IOException("Could not locate video stream for PID " + pid);
		}
		this.videoStreamIndex = streamIdx;

		// 4. Open codec
		AVStream videoStream = formatCtx.streams(videoStreamIndex);
		var codec = avcodec_find_decoder(videoStream.codecpar().codec_id());
		if (codec == null) {
			avformat_close_input(formatCtx);
			throw new IOException("Could not find decoder for codec id " + videoStream.codecpar().codec_id());
		}
		codecCtx = avcodec_alloc_context3(codec);
		avcodec_parameters_to_context(codecCtx, videoStream.codecpar());
		ret = avcodec_open2(codecCtx, codec, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
		if (ret < 0) {
			avcodec_free_context(codecCtx);
			avformat_close_input(formatCtx);
			throw new IOException("avcodec_open2 failed: " + ret);
		}

		// 5. Estimate frame count
		long nb = videoStream.nb_frames();
		if (nb > 0) {
			this.frameCount = (int) nb;
		}
		else {
			double duration = videoStream.duration() * av_q2d(videoStream.time_base());
			double fps = av_q2d(videoStream.r_frame_rate());
			if (duration > 0 && fps > 0) {
				this.frameCount = (int) (duration * fps);
			}
			else {
				this.frameCount = -1;
			}
		}
	}

	@Override
	public int getFrameCount() {
		return frameCount;
	}

	@Override
	public BufferedImage getFrame(int frameNumber) {
		BufferedImage cached = cache.get(frameNumber);
		if (cached != null) {
			return cached;
		}

		try {
			// Backward seek if needed
			if (frameNumber <= currentFrameIndex) {
				AVStream stream = formatCtx.streams(videoStreamIndex);
				av_seek_frame(formatCtx, videoStreamIndex, stream.start_time(), AVSEEK_FLAG_BACKWARD);
				avcodec_flush_buffers(codecCtx);
				currentFrameIndex = -1;
			}

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
						currentFrameIndex++;
						if (currentFrameIndex == frameNumber) {
							BufferedImage image = convertFrameToImage(frame);
							cache.put(frameNumber, image);
							return image;
						}
					}
				}

				// Flush remaining frames from the decoder
				avcodec_send_packet(codecCtx, (AVPacket) null);
				while (avcodec_receive_frame(codecCtx, frame) >= 0) {
					currentFrameIndex++;
					if (currentFrameIndex == frameNumber) {
						BufferedImage image = convertFrameToImage(frame);
						cache.put(frameNumber, image);
						return image;
					}
				}
			}
			finally {
				av_frame_free(frame);
				av_packet_free(packet);
			}
		}
		catch (Exception e) {
			throw new RuntimeException("Failed to decode frame " + frameNumber, e);
		}

		return null;
	}

	private BufferedImage convertFrameToImage(AVFrame frame) {
		int width = frame.width();
		int height = frame.height();

		if (swsCtx == null) {
			swsCtx = sws_getContext(width, height, frame.format(), width, height, AV_PIX_FMT_BGR24, SWS_BILINEAR, null,
					null, (double[]) null);
		}

		AVFrame bgrFrame = av_frame_alloc();
		bgrFrame.format(AV_PIX_FMT_BGR24);
		bgrFrame.width(width);
		bgrFrame.height(height);
		av_frame_get_buffer(bgrFrame, 0);

		sws_scale(swsCtx, frame.data(), frame.linesize(), 0, height, bgrFrame.data(), bgrFrame.linesize());

		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
		byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

		int linesize = bgrFrame.linesize(0);
		ByteBuffer buffer = bgrFrame.data(0).position(0).capacity((long) linesize * height).asByteBuffer();
		if (linesize == width * 3) {
			buffer.get(pixels);
		}
		else {
			// Handle padding in each row
			for (int y = 0; y < height; y++) {
				buffer.position(y * linesize);
				buffer.get(pixels, y * width * 3, width * 3);
			}
		}

		av_frame_free(bgrFrame);
		return image;
	}

	@Override
	public void close() {
		if (swsCtx != null) {
			sws_freeContext(swsCtx);
			swsCtx = null;
		}
		avcodec_free_context(codecCtx);
		avformat_close_input(formatCtx);
		cache.clear();
	}

}
