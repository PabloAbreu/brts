package org.brts.lowlevel.thumbnail;

import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;
import static org.bytedeco.ffmpeg.global.avutil.*;

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
import lombok.RequiredArgsConstructor;
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

	private static final String BASE_PROPERTY = "brts.thumbnail.";

	private static final String WIDTH_PROPERTY = BASE_PROPERTY + "width";

	private static final String HEIGHT_PROPERTY = BASE_PROPERTY + "height";

	private static final String MINIMUM_LUMINANCE_VARIATION_PROPERTY = BASE_PROPERTY + "minimumLuminanceVariation";

	private static final int DEFAULT_WIDTH = 240;

	private static final double DEFAULT_MINIMUM_LUMINANCE_VARIATION = 20.0;

	private static final int FRAMES_BETWEEN_ATTEMPTS = 24;

	public static final int MAX_CAPTURE_ATTEMPTS = 10;

	private static final long PTS_TICKS_PER_SECOND = 90_000L;

	@Getter
	private final int thumbnailWidth;

	/** Requested height, or 0 to derive it from the source aspect ratio. */
	@Getter
	private final int thumbnailHeight;

	@Getter
	private final double minimumLuminanceVariation;

	/** Uses the configured defaults for size and thumbnail quality. */
	public ChapterThumbnailExtractor() {
		this(BrtsFileConfig.getInstance().parseIntProperty(WIDTH_PROPERTY, DEFAULT_WIDTH),
				BrtsFileConfig.getInstance().parseIntProperty(HEIGHT_PROPERTY, 0),
				BrtsFileConfig.getInstance().parseDoubleProperty(MINIMUM_LUMINANCE_VARIATION_PROPERTY,
						DEFAULT_MINIMUM_LUMINANCE_VARIATION));
	}

	/**
	 * @param thumbnailWidth            thumbnail width in pixels, must be positive
	 * @param thumbnailHeight           thumbnail height in pixels, or 0 to derive it from the source aspect ratio
	 * @param minimumLuminanceVariation minimum luminance standard deviation (RMS contrast) across the image, in the
	 *                                  range 0-255
	 */
	public ChapterThumbnailExtractor(int thumbnailWidth, int thumbnailHeight, double minimumLuminanceVariation) {
		if (thumbnailWidth <= 0) {
			throw new IllegalArgumentException("Thumbnail width must be positive, got " + thumbnailWidth);
		}
		if (thumbnailHeight < 0) {
			throw new IllegalArgumentException("Thumbnail height must be positive or 0 (auto), got " + thumbnailHeight);
		}
		if (!Double.isFinite(minimumLuminanceVariation) || minimumLuminanceVariation < 0
				|| minimumLuminanceVariation > 255) {
			throw new IllegalArgumentException(
					"Minimum luminance variation must be between 0 and 255, got " + minimumLuminanceVariation);
		}
		this.thumbnailWidth = thumbnailWidth;
		this.thumbnailHeight = thumbnailHeight;
		this.minimumLuminanceVariation = minimumLuminanceVariation;
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
				BufferedImage thumbnail = grabber.grabThumbnailAt(chapter.startSeconds, thumbnailWidth, thumbnailHeight,
						minimumLuminanceVariation);
				if (thumbnail == null) {
					throw new IOException("Could not decode a frame at " + chapter.startSeconds + "s for chapter "
							+ chapter.index + " of " + source);
				}

				descriptor.setThumbnailHeight(thumbnail.getHeight());
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

	// Standard deviation of luminance (RMS contrast) rather than local adjacent-pixel differences: a large flat area
	// next to another flat area of a different tone (e.g. a black object on a white background) has almost no
	// adjacent-pixel difference on average, but is a visually good, high-contrast thumbnail.
	static double luminanceVariation(BufferedImage image) {
		int pixelCount = image.getWidth() * image.getHeight();
		if (pixelCount == 0) {
			return 0;
		}
		double sum = 0;
		double sumSquares = 0;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				double luminance = luminance(image.getRGB(x, y));
				sum += luminance;
				sumSquares += luminance * luminance;
			}
		}
		double mean = sum / pixelCount;
		double variance = sumSquares / pixelCount - mean * mean;
		return Math.sqrt(Math.max(0, variance));
	}

	@RequiredArgsConstructor
	static final class ThumbnailCandidateSelector {

		private final double minimumLuminanceVariation;

		private int attempts;

		private BufferedImage lastCandidate;

		boolean consider(BufferedImage candidate) {
			lastCandidate = candidate;
			attempts++;
			return luminanceVariation(candidate) >= minimumLuminanceVariation || attempts == MAX_CAPTURE_ATTEMPTS;
		}

		int attempts() {
			return attempts;
		}

		BufferedImage lastCandidate() {
			return lastCandidate;
		}
	}

	private static double luminance(int rgb) {
		int red = (rgb >>> 16) & 0xff;
		int green = (rgb >>> 8) & 0xff;
		int blue = rgb & 0xff;
		return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
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
		private BufferedImage grabThumbnailAt(double targetSeconds, int width, int requestedHeight,
				double minimumLuminanceVariation) throws IOException {
			// Round to the nearest tick: truncating would bias targetTs below the true chapter start whenever it
			// doesn't land exactly on a tick, causing a frame before the chapter boundary to be accepted.
			long targetTs = Math.round(targetSeconds / videoTimeBase) + videoStartTime;
			int ret = av_seek_frame(formatCtx, videoStreamIndex, targetTs, AVSEEK_FLAG_BACKWARD);
			if (ret < 0) {
				throw new IOException("av_seek_frame to " + targetSeconds + "s failed for " + source + ": " + ret);
			}
			avcodec_flush_buffers(codecCtx);

			AVPacket packet = av_packet_alloc();
			AVFrame frame = av_frame_alloc();
			ThumbnailCandidateSelector selector = new ThumbnailCandidateSelector(minimumLuminanceVariation);
			int framesToSkip = 0;
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
						if (framesToSkip > 0) {
							framesToSkip--;
							continue;
						}

						BufferedImage candidate = reduceFrame(frame, width, requestedHeight);
						if (selector.consider(candidate)) {
							return candidate;
						}
						log.debug("Rejected thumbnail candidate {} at frame PTS {} with luminance variation {}",
								selector.attempts(), pts, luminanceVariation(candidate));
						framesToSkip = FRAMES_BETWEEN_ATTEMPTS;
					}
				}

				// Flush the decoder in case the target lies in the trailing frames
				avcodec_send_packet(codecCtx, (AVPacket) null);
				while (avcodec_receive_frame(codecCtx, frame) >= 0) {
					long pts = frame.best_effort_timestamp();
					if (pts != AV_NOPTS_VALUE && pts < targetTs) {
						continue;
					}
					if (framesToSkip > 0) {
						framesToSkip--;
						continue;
					}
					BufferedImage candidate = reduceFrame(frame, width, requestedHeight);
					if (selector.consider(candidate)) {
						return candidate;
					}
					framesToSkip = FRAMES_BETWEEN_ATTEMPTS;
				}
				return selector.lastCandidate();
			} finally {
				av_frame_free(frame);
				av_packet_free(packet);
			}
		}

		private BufferedImage reduceFrame(AVFrame frame, int width, int requestedHeight) {
			BufferedImage decoded = converter.toBufferedImage(frame);
			int height = requestedHeight > 0 ? requestedHeight
					: Math.max(1, (int) Math.round((double) width * decoded.getHeight() / decoded.getWidth()));
			return ImageUtils.scaleImage(decoded, width, height);
		}

		@Override
		public void close() {
			converter.close();
			avcodec_free_context(codecCtx);
			avformat_close_input(formatCtx);
		}

	}

}
