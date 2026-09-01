package org.brts.common.utils.composition.sources.video;

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
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.brts.common.utils.CacheUtils;
import org.brts.common.utils.FfmpegFrameConverter;
import org.brts.common.utils.composition.CompositionEngineFactory;
import org.brts.common.utils.composition.ImageFrame;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVFrame;

import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for {@link VideoFrames} implementations that decode frames using bytedeco/FFmpeg.
 * <p>
 * Handles format/codec opening, sequential frame decoding with LRU caching, color-space conversion, and resource
 * cleanup. Subclasses only need to supply stream-selection logic via the constructor.
 */
@Slf4j
public abstract class FfmpegVideoFrames implements VideoFrames {

	private static final int CACHE_CAPACITY = 30;

	private final AVFormatContext formatCtx;

	private final AVCodecContext codecCtx;

	private final int videoStreamIndex;

	private final int frameCount;

	private final double fps;

	private final FfmpegFrameConverter frameConverter = new FfmpegFrameConverter();

	private int currentFrameIndex = -1;

	private final Map<Integer, ImageFrame> cache = CacheUtils.lruCache(CACHE_CAPACITY, ImageFrame::close);

	/**
	 * Opens the video file and selects a stream.
	 *
	 * @param path         path to the video file
	 * @param preferredPid if &ge; 0, attempts to match a stream whose {@code id()} equals this value; falls back to the
	 *                     first video stream. If &lt; 0, selects the first video stream directly.
	 */
	protected FfmpegVideoFrames(Path path, int preferredPid) throws IOException {
		formatCtx = new AVFormatContext(null);
		int ret = avformat_open_input(formatCtx, path.toString(), null, null);
		if (ret < 0) {
			throw new IOException("avformat_open_input failed: " + ret);
		}

		ret = avformat_find_stream_info(formatCtx, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
		if (ret < 0) {
			avformat_close_input(formatCtx);
			throw new IOException("avformat_find_stream_info failed: " + ret);
		}

		// Find video stream
		int streamIdx = -1;
		if (preferredPid >= 0) {
			for (int i = 0; i < formatCtx.nb_streams(); i++) {
				AVStream stream = formatCtx.streams(i);
				if (stream.id() == preferredPid) {
					streamIdx = i;
					break;
				}
			}
		}
		if (streamIdx < 0) {
			// Fallback: first video stream
			for (int i = 0; i < formatCtx.nb_streams(); i++) {
				AVStream stream = formatCtx.streams(i);
				if (stream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
					streamIdx = i;
					break;
				}
			}
		}
		if (streamIdx < 0) {
			avformat_close_input(formatCtx);
			throw new IOException("Could not locate video stream in " + path);
		}
		this.videoStreamIndex = streamIdx;

		// Open codec
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

		// Compute fps
		double detectedFps = av_q2d(videoStream.r_frame_rate());
		this.fps = detectedFps > 0 ? detectedFps : 0;

		// Estimate frame count
		long nb = videoStream.nb_frames();
		log.debug("videoStream.nb_frames() = {}", nb);
		if (nb > 0) {
			this.frameCount = (int) nb;
			log.debug("Using nb_frames for frameCount: {}", this.frameCount);
		} else {
			double timeBase = av_q2d(videoStream.time_base());
			double duration = videoStream.duration() * timeBase;
			log.debug("videoStream.duration() = {}, time_base = {}, detected fps = {}", videoStream.duration(),
					timeBase, this.fps);
			log.debug("Computed duration (seconds) = {}", duration);
			if (duration > 0 && this.fps > 0) {
				this.frameCount = (int) (duration * this.fps);
				log.debug("Estimated frameCount from duration * fps = {}", this.frameCount);
			} else {
				this.frameCount = -1;
				log.debug("Could not estimate frameCount; set to -1");
			}
		}
	}

	/**
	 * Opens the video file and selects the first video stream.
	 */
	protected FfmpegVideoFrames(Path path) throws IOException {
		this(path, -1);
	}

	@Override
	public int getFrameCount() {
		return frameCount;
	}

	@Override
	public double getFps() {
		return fps;
	}

	@Override
	public ImageFrame getFrame(int frameNumber) {
		ImageFrame cached = cache.get(frameNumber);
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
							ImageFrame imageFrame = convertFrameToImageFrame(frame);
							cache.put(frameNumber, imageFrame);
							return imageFrame;
						}
					}
				}

				// Flush remaining frames from the decoder
				avcodec_send_packet(codecCtx, (AVPacket) null);
				while (avcodec_receive_frame(codecCtx, frame) >= 0) {
					currentFrameIndex++;
					if (currentFrameIndex == frameNumber) {
						ImageFrame imageFrame = convertFrameToImageFrame(frame);
						cache.put(frameNumber, imageFrame);
						return imageFrame;
					}
				}
			} finally {
				av_frame_free(frame);
				av_packet_free(packet);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to decode frame " + frameNumber, e);
		}

		return null;
	}

	private ImageFrame convertFrameToImageFrame(AVFrame frame) {
		return CompositionEngineFactory.get().fromBufferedImage(frameConverter.toBufferedImage(frame));
	}

	@Override
	public void close() {
		frameConverter.close();
		avcodec_free_context(codecCtx);
		avformat_close_input(formatCtx);
		cache.values().forEach(ImageFrame::close);
		cache.values().forEach(ImageFrame::close);
		cache.clear();
	}

}
