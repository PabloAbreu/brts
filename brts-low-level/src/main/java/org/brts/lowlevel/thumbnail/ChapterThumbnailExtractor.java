package org.brts.lowlevel.thumbnail;

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
import static org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_get;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_q2d;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.FfmpegFrameConverter;
import org.brts.common.utils.ImageUtils;
import org.brts.lowlevel.thumbnail.ChapterThumbnailsDescriptor.ChapterThumbnail;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVChapter;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionaryEntry;
import org.bytedeco.ffmpeg.avutil.AVFrame;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Extracts one PNG thumbnail per chapter of a source media file (typically an MKV) and writes a JSON descriptor
 * referencing the generated images together with the chapter name and start time.
 * <p>
 * Chapters and frames are read through the bytedeco/FFmpeg bindings already used across the project. Defaults come from
 * {@link BrtsFileConfig} ({@code brts.thumbnail.*}) and can be overridden per instance.
 */
@Slf4j
public class ChapterThumbnailExtractor {

	public static final String DESCRIPTOR_FILE_NAME = "chapter-thumbnails.json";

	private static final String WIDTH_PROPERTY = "brts.thumbnail.width";

	private static final String HEIGHT_PROPERTY = "brts.thumbnail.height";

	private static final String OFFSET_PROPERTY = "brts.thumbnail.offsetSeconds";

	private static final int DEFAULT_WIDTH = 240;

	private static final double DEFAULT_OFFSET_SECONDS = 2.0;

	private static final long PTS_TICKS_PER_SECOND = 90_000L;

	@Getter
	private final int thumbnailWidth;

	/** Requested height, or 0 to derive it from the source aspect ratio. */
	@Getter
	private final int thumbnailHeight;

	@Getter
	private final double captureOffsetSeconds;

	/** Uses the configured defaults for size and capture offset. */
	public ChapterThumbnailExtractor() {
		this(BrtsFileConfig.getInstance().parseIntProperty(WIDTH_PROPERTY, DEFAULT_WIDTH),
				BrtsFileConfig.getInstance().parseIntProperty(HEIGHT_PROPERTY, 0),
				BrtsFileConfig.getInstance().parseDoubleProperty(OFFSET_PROPERTY, DEFAULT_OFFSET_SECONDS));
	}

	/**
	 * @param thumbnailWidth       thumbnail width in pixels, must be positive
	 * @param thumbnailHeight      thumbnail height in pixels, or 0 to derive it from the source aspect ratio
	 * @param captureOffsetSeconds offset added to each chapter start time before grabbing the frame, which avoids the
	 *                             black frames usually found on chapter boundaries
	 */
	public ChapterThumbnailExtractor(int thumbnailWidth, int thumbnailHeight, double captureOffsetSeconds) {
		if (thumbnailWidth <= 0) {
			throw new IllegalArgumentException("Thumbnail width must be positive, got " + thumbnailWidth);
		}
		if (thumbnailHeight < 0) {
			throw new IllegalArgumentException("Thumbnail height must be positive or 0 (auto), got " + thumbnailHeight);
		}
		if (captureOffsetSeconds < 0) {
			throw new IllegalArgumentException("Capture offset must not be negative, got " + captureOffsetSeconds);
		}
		this.thumbnailWidth = thumbnailWidth;
		this.thumbnailHeight = thumbnailHeight;
		this.captureOffsetSeconds = captureOffsetSeconds;
	}

	/**
	 * Extracts a thumbnail for every chapter of {@code source} into {@code outputDir} and writes
	 * {@value #DESCRIPTOR_FILE_NAME} next to the images.
	 *
	 * @return the descriptor that was written to disk
	 * @throws IOException              if the source cannot be decoded or the outputs cannot be written
	 * @throws IllegalArgumentException if the source has no chapter
	 */
	public ChapterThumbnailsDescriptor extract(Path source, Path outputDir) throws IOException {
		if (!Files.isRegularFile(source)) {
			throw new IllegalArgumentException("Source media not found: " + source);
		}
		Files.createDirectories(outputDir);

		try (ChapterFrameGrabber grabber = new ChapterFrameGrabber(source)) {
			List<Chapter> chapters = grabber.readChapters();
			if (chapters.isEmpty()) {
				throw new IllegalArgumentException("No chapter found in " + source);
			}

			ChapterThumbnailsDescriptor descriptor = new ChapterThumbnailsDescriptor();
			descriptor.setSourceMedia(source.toAbsolutePath().toString());
			descriptor.setThumbnailWidth(thumbnailWidth);

			for (Chapter chapter : chapters) {
				double captureSeconds = captureTimeOf(chapter);
				BufferedImage frame = grabber.grabFrameAt(captureSeconds);
				if (frame == null) {
					throw new IOException("Could not decode a frame at " + captureSeconds + "s for chapter "
							+ chapter.index + " of " + source);
				}

				int height = descriptor.getThumbnailHeight() > 0 ? descriptor.getThumbnailHeight()
						: computeHeight(frame);
				descriptor.setThumbnailHeight(height);

				BufferedImage thumbnail = ImageUtils.scaleImage(frame, thumbnailWidth, height);
				String imageFile = String.format("chapter-%02d.png", chapter.index);
				Path imagePath = outputDir.resolve(imageFile);
				ImageIO.write(thumbnail, "png", imagePath.toFile());
				log.info("Chapter {} '{}' at {}s -> {}", chapter.index, chapter.title, chapter.startSeconds, imagePath);

				ChapterThumbnail entry = new ChapterThumbnail();
				entry.setIndex(chapter.index);
				entry.setTitle(chapter.title);
				entry.setStartTimeSeconds(chapter.startSeconds);
				entry.setStartTimeTicks(Math.round(chapter.startSeconds * PTS_TICKS_PER_SECOND));
				entry.setImageFile(imageFile);
				descriptor.getChapters().add(entry);
			}

			Path descriptorPath = outputDir.resolve(DESCRIPTOR_FILE_NAME);
			JsonMapperFactory.get().writeValue(descriptorPath.toFile(), descriptor);
			log.info("Wrote {} chapter thumbnails and descriptor {}", descriptor.getChapters().size(), descriptorPath);
			return descriptor;
		}
	}

	private double captureTimeOf(Chapter chapter) {
		double capture = chapter.startSeconds + captureOffsetSeconds;
		if (chapter.endSeconds > chapter.startSeconds && capture >= chapter.endSeconds) {
			return chapter.startSeconds;
		}
		return capture;
	}

	private int computeHeight(BufferedImage frame) {
		if (thumbnailHeight > 0) {
			return thumbnailHeight;
		}
		int height = (int) Math.round((double) thumbnailWidth * frame.getHeight() / frame.getWidth());
		return Math.max(1, height);
	}

	/** A chapter as described by the source container. */
	private record Chapter(int index, String title, double startSeconds, double endSeconds) {
	}

	/** Holds the native decoding state for a single source file. */
	private static final class ChapterFrameGrabber implements AutoCloseable {

		private final Path source;

		private final AVFormatContext formatCtx;

		private final AVCodecContext codecCtx;

		private final int videoStreamIndex;

		private final double videoTimeBase;

		private final long videoStartTime;

		private final FfmpegFrameConverter converter = new FfmpegFrameConverter();

		private ChapterFrameGrabber(Path source) throws IOException {
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
			videoStartTime = videoStream.start_time() == AV_NOPTS_VALUE ? 0 : videoStream.start_time();

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

		private List<Chapter> readChapters() {
			List<Chapter> chapters = new ArrayList<>();
			for (int i = 0; i < formatCtx.nb_chapters(); i++) {
				AVChapter chapter = formatCtx.chapters(i);
				double timeBase = av_q2d(chapter.time_base());
				double start = chapter.start() * timeBase;
				double end = chapter.end() * timeBase;
				chapters.add(new Chapter(i + 1, readTitle(chapter, i), start, end));
			}
			return chapters;
		}

		private String readTitle(AVChapter chapter, int zeroBasedIndex) {
			AVDictionaryEntry entry = av_dict_get(chapter.metadata(), "title", null, 0);
			if (entry != null && entry.value() != null) {
				String title = entry.value().getString();
				if (title != null && !title.isBlank()) {
					return title;
				}
			}
			return String.format("Chapter %02d", zeroBasedIndex + 1);
		}

		/**
		 * Seeks to {@code targetSeconds} and decodes the first frame at or after that position.
		 *
		 * @return the decoded frame, or {@code null} if no frame could be decoded
		 */
		private BufferedImage grabFrameAt(double targetSeconds) throws IOException {
			long targetTs = (long) (targetSeconds / videoTimeBase) + videoStartTime;
			int ret = av_seek_frame(formatCtx, videoStreamIndex, targetTs, AVSEEK_FLAG_BACKWARD);
			if (ret < 0) {
				throw new IOException("av_seek_frame to " + targetSeconds + "s failed for " + source + ": " + ret);
			}
			avcodec_flush_buffers(codecCtx);

			AVPacket packet = av_packet_alloc();
			AVFrame frame = av_frame_alloc();
			BufferedImage fallback = null;
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
						if (pts == AV_NOPTS_VALUE || pts >= targetTs) {
							return converter.toBufferedImage(frame);
						}
						if (fallback == null) {
							fallback = converter.toBufferedImage(frame);
						}
					}
				}

				// Flush the decoder in case the target lies in the trailing frames
				avcodec_send_packet(codecCtx, (AVPacket) null);
				while (avcodec_receive_frame(codecCtx, frame) >= 0) {
					fallback = converter.toBufferedImage(frame);
				}
				return fallback;
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

}
