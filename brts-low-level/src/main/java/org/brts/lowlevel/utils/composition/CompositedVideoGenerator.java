package org.brts.lowlevel.utils.composition;



import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.swscale.*;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.m2ts.IStreamInfo;
import org.brts.common.m2ts.model.M2tsChapter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.AudioUtils;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.FfmpegAudioExtractor;
import org.brts.common.utils.FileUtils;
import org.brts.common.utils.composition.CompositionBuffer;
import org.brts.common.utils.composition.CompositionContextImpl;
import org.brts.common.utils.composition.ImageFrame;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.ImagesComposition;
import org.brts.common.utils.composition.MediaRepository;
import org.brts.common.utils.composition.MediaRepositoryImpl;
import org.brts.common.utils.composition.sources.video.VideoFrames;
import org.brts.common.utils.composition.sources.video.VideoFramesFactory;
import org.brts.lowlevel.m2ts.M2tsClipWriterFactory;
import org.brts.lowlevel.m2ts.M2tsWriter;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.javacpp.IntPointer;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Makes use of the {@link CompositionBuffer} to generate composited videos.
 *
 * <p>
 * Outputs an H.264 M2TS video file (with the corresponding CLPI) by compositing frames according to the provided
 * {@link ImagesComposition} configuration.
 *
 * <p>
 * If the background image is a video, its audio streams are extracted and muxed into the output M2TS file.
 *
 * <p>
 * This class lives in {@code brt-low-level} so it can use {@link M2tsWriter} and {@link ClipInfoWriter}.
 */
@Slf4j
public class CompositedVideoGenerator {
	/** Standard Blu-ray video PID. */
	private static final int VIDEO_PID = 0x1011;

	/** Base PID for audio streams. */
	private static final int BASE_AUDIO_PID = 0x1100;

	/** Config key enabling ordered dithering before the BGRA to YUV420P conversion. */
	private static final String DITHER_PROPERTY = "brts.composition.video.dither";

	private static final boolean DEFAULT_DITHER = true;

	/**
	 * 8x8 Bayer threshold matrix, values 0..63, used to spread the rounding error of the 8-bit limited-range YUV
	 * quantisation over a pixel neighbourhood instead of letting it form contours.
	 */
	private static final int[] BAYER_8X8 = { 0, 32, 8, 40, 2, 34, 10, 42, 48, 16, 56, 24, 50, 18, 58, 26, 12, 44, 4, 36,
			14, 46, 6, 38, 60, 28, 52, 20, 62, 30, 54, 22, 3, 35, 11, 43, 1, 33, 9, 41, 51, 19, 59, 27, 49, 17, 57, 25,
			15, 47, 7, 39, 13, 45, 5, 37, 63, 31, 55, 23, 61, 29, 53, 21 };

	// -------------------------------------------------------------------------
	// Configuration
	// -------------------------------------------------------------------------

	/**
	 * Generation parameters passed to
	 * {@link CompositedVideoGenerator#generate(ImagesComposition, Path, String, Config)}.
	 */
	@Getter
	@Setter
	public static class Config {
		/**
		 * Number of frames to generate. When ≤ 0, the generator tries to derive the count from the base video's frame
		 * count, then from {@link Config#extraAudioPath} duration. When neither is available, defaults to 1 minute of
		 * frames at the configured (or defaulted) fps.
		 */
		private int frameCount = -1;

		/**
		 * Output frame rate in frames per second. When ≤ 0, derived from the base video stream info or defaults to
		 * 24.0.
		 */
		private double fps = 0;

		/** Output frame width in pixels. Defaults to 1920. */
		private int width = 1920;

		/** Output frame height in pixels. Defaults to 1080. */
		private int height = 1080;

		/**
		 * Average MPEG-2 video target bitrate in kbps. Defaults to 20 000 kbps (~20 Mbit/s), a safe value for Blu-ray.
		 */
		private int bitrateKbps = 20_000;

		/**
		 * Path to an extra audio elementary-stream file to mux into the output M2TS alongside the encoded video. Useful
		 * when the composition has a static image base (no embedded audio). When {@code null}, no extra audio is added.
		 */
		private String extraAudioPath;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Generates a composited M2TS video file and the matching CLPI in {@code outputDir}.
	 *
	 * <p>
	 * Steps performed:
	 * <ol>
	 * <li>Resolve fps and frameCount (from config, base video, or extra audio duration).</li>
	 * <li>Composite each frame via {@link CompositionBuffer} and encode to an MPEG-2 ES file.</li>
	 * <li>Optionally extract audio streams from the base video.</li>
	 * <li>Mux everything into a Blu-ray 192-byte source-packet M2TS using {@link M2tsWriter}.</li>
	 * <li>Write the corresponding CLPI using {@link ClipInfoWriter}.</li>
	 * </ol>
	 *
	 * @param composition the images composition recipe
	 * @param outputDir   destination directory; {@code <clipName>.m2ts} and {@code <clipName>.clpi} are written here
	 * @param clipName    5-digit clip name (e.g. {@code "00001"})
	 * @param config      generation parameters
	 * @returns the M2TS descriptor for the generated video
	 * @throws IOException on any I/O or encoding failure
	 */
	public M2tsDescriptor generate(ImagesComposition composition, Path outputDir, String clipName, Config config,
			Path baseDir) throws IOException {

		// 1. Resolve fps and frameCount ----------------------------------------
		double fps = config.getFps() > 0 ? config.getFps() : 0;
		int frameCount = config.getFrameCount();
		log.debug("fps : {} , frameCount : {}", fps, frameCount);

		String baseVideoPath = resolveBaseVideoPath(composition, baseDir);
		if (baseVideoPath != null && (fps <= 0 || frameCount <= 0)) {
			try (VideoFrames vf = VideoFramesFactory.create(Path.of(baseVideoPath))) {
				if (fps <= 0 && vf.getFps() > 0) {
					fps = vf.getFps();
				}
				if (frameCount <= 0) {
					frameCount = vf.getFrameCount();
				}
			} catch (Exception e) {
				throw new IOException("Failed to read base video: " + baseVideoPath, e);
			}
		}

		if (frameCount <= 0 && config.getExtraAudioPath() != null && !config.getExtraAudioPath().isBlank()) {
			double audioDuration = probeAudioDurationSeconds(Path.of(config.getExtraAudioPath()));
			if (audioDuration > 0) {
				double effectiveFps = fps > 0 ? fps : 24.0;
				frameCount = (int) Math.ceil(audioDuration * effectiveFps);
				log.debug("Derived frameCount={} from audio duration {}s @ {} fps", frameCount, audioDuration,
						effectiveFps);
			}
		}
		if (fps <= 0) {
			fps = 24.0;
		}
		if (frameCount <= 0) {
			frameCount = (int) Math.round(fps * 60);
			log.warn("Could not determine frameCount from base video or audio; defaulting to 1 minute = {} frames",
					frameCount);
		}

		log.info("Generating composited video '{}': {} frames @ {} fps ({}×{})", clipName, frameCount, fps,
				config.getWidth(), config.getHeight());

		Files.createDirectories(outputDir);
		Path tempDir = Files.createTempDirectory("brt-composition-");
		try {
			// 2. Encode composited frames to an H.264 ES file -----------------
			Path videoEsFile = tempDir.resolve("video.h264");
			encodeToMpeg4(composition, frameCount, fps, config, videoEsFile, baseDir);

			// 3. Build stream list: video entry --------------------------------
			List<M2tsDescriptor.StreamEntry> streams = new ArrayList<>();
			M2tsDescriptor.StreamEntry videoEntry = new M2tsDescriptor.StreamEntry();
			videoEntry.setFile(videoEsFile.toString());
			videoEntry.setPid(VIDEO_PID);
			videoEntry.setStreamTypeByte(StreamCodingType.H264_AVC.getCodingTypeByte());
			videoEntry.setFrameRateFps(fps);
			videoEntry.setVideoFormat(IStreamInfo.VIDEO_FORMAT_1080P);
			videoEntry.setBitrateKbps(config.getBitrateKbps());
			streams.add(videoEntry);

			// 4. Extract audio streams from base video -------------------------
			if (baseVideoPath != null) {
				appendAudioEntries(baseVideoPath, tempDir, streams);
			}

			// 4b. Append extra audio ES (static-base compositions with separate audio)
			if (config.getExtraAudioPath() != null && !config.getExtraAudioPath().isBlank()) {
				Path extraAudioPath = Path.of(config.getExtraAudioPath());
				var audioInfo = AudioUtils.probeAudioInfo(extraAudioPath);
				int extraStreamTypeByte = audioInfo.streamTypeByte();

				// Cut audio that outlasts the video so the two tracks end together; never extend
				// audio that is already shorter than or equal to the video.
				double effectiveDurationSeconds = frameCount / fps;
				String extraAudioFile = extraAudioPath.toString();
				if (audioInfo.durationSeconds() > effectiveDurationSeconds + 0.05) {
					String fileName = extraAudioPath.getFileName().toString();
					int dot = fileName.lastIndexOf('.');
					String ext = dot >= 0 ? fileName.substring(dot + 1) : "es";
					Path trimmedAudioPath = tempDir.resolve("extra_audio_trimmed." + ext);
					AudioUtils.trimToDuration(extraAudioPath, trimmedAudioPath, effectiveDurationSeconds);
					extraAudioFile = trimmedAudioPath.toString();
					log.debug("  Trimmed extra audio ES from {}s to {}s", audioInfo.durationSeconds(),
							effectiveDurationSeconds);
				}

				M2tsDescriptor.StreamEntry audioEntry = new M2tsDescriptor.StreamEntry();
				audioEntry.setFile(extraAudioFile);
				audioEntry.setPid(BASE_AUDIO_PID + (streams.size() - 1)); // offset past video + any base audio
				audioEntry.setStreamTypeByte(extraStreamTypeByte);
				audioEntry.setChannels(audioInfo.channels());
				audioEntry.setSampleRateHz(audioInfo.sampleRateHz());
				audioEntry.setBitrateKbps(audioInfo.bitrateKbps());

				streams.add(audioEntry);
				log.debug("  Appended extra audio ES: {} (type=0x{})", extraAudioFile,
						Integer.toHexString(extraStreamTypeByte));
			}

			// 5. Mux into M2TS ------------------------------------------------
			M2tsDescriptor descriptor = new M2tsDescriptor();
			descriptor.setOutputName(clipName);
			descriptor.setStreams(streams);
			descriptor.setChapters(List.of(new M2tsChapter(0, 0L)));
			// Initial PTS offset = VBV buffer duration (2 s at 90 kHz).
			// The H264 encoder is configured with rc_buffer_size = 2 × bitrate,
			// so the decoder must pre-buffer 2 seconds of data before it starts
			// to decode the first frame. Setting this offset guarantees that the
			// first (and subsequent) I-frames are fully delivered in the transport
			// stream before their DTS is reached per the PCR clock.
			long vbvBufferSeconds = 2L;
			descriptor.setInitialPtsOffsetTicks(vbvBufferSeconds * 90_000L);

			Path m2tsPath = outputDir.resolve(clipName + ".m2ts");
			Path clpiPath = outputDir.resolve(clipName + ".clpi");
			M2tsClipWriterFactory.createWriter().write(descriptor, m2tsPath, clpiPath);

			log.info("Wrote {} and {}", m2tsPath.getFileName(), clpiPath.getFileName());
			return descriptor;
		} finally {
			FileUtils.deleteDir(tempDir);
		}
	}

	// -------------------------------------------------------------------------
	// MPEG-4 encoding
	// -------------------------------------------------------------------------

	/**
	 * Encodes all composited frames to a raw H.264 elementary stream file using bytedeco/FFmpeg.
	 *
	 * <p>
	 * Each frame is composited via {@link CompositionBuffer}, converted from BGR24 to YUV420P using {@code sws_scale},
	 * then handed to the H.264 encoder. The {@link MediaRepositoryImpl} is closed when encoding completes so that any
	 * opened {@link VideoFrames} FFmpeg contexts are released promptly.
	 */
	private void encodeToMpeg4(ImagesComposition composition, int frameCount, double fps, Config config,
			Path outputFile, Path baseDir) throws IOException {

		int width = config.getWidth();
		int height = config.getHeight();
		long bitrate = config.getBitrateKbps() * 1000L;
		int fpsInt = (int) Math.round(fps);

		var codec = avcodec_find_encoder_by_name("libx264");
		if (codec == null) {
			codec = avcodec_find_encoder(AV_CODEC_ID_H264); // fall back to whatever is built in
		}
		if (codec == null) {
			throw new IOException("H.264 video encoder not available in this FFmpeg build");
		}
		log.info("H.264 encoder: {}", codec.name().getString());
		AVCodecContext codecCtx = avcodec_alloc_context3(codec);
		if (codecCtx == null) {
			throw new IOException("Could not allocate AVCodecContext");
		}

		try {
			codecCtx.codec_id(AV_CODEC_ID_H264);
			codecCtx.bit_rate(bitrate);
			// VBV (Video Buffering Verifier) constraints: limit frame sizes so that the
			// muxer can deliver each access unit before its DTS according to the PCR
			// clock.
			// Without these, the encoder may produce I-frames that take longer to deliver
			// at the target bitrate than the next frame's DTS, causing "Packet corrupt"
			// warnings in demuxers. A 2-second VBV buffer is a safe choice for Blu-ray.
			codecCtx.rc_max_rate(bitrate);
			codecCtx.rc_buffer_size((int) (bitrate * 2));
			codecCtx.width(width);
			codecCtx.height(height);
			codecCtx.time_base().num(1);
			codecCtx.time_base().den(fpsInt);
			codecCtx.framerate().num(fpsInt);
			codecCtx.framerate().den(1);
			codecCtx.gop_size(24);
			codecCtx.max_b_frames(0); // B-frames reorder decode vs. display order; the
										// raw ES muxer
										// assigns PTS sequentially and writes PTS-only
										// PES headers, so
										// B-frames produce corrupt DTS and choppy
										// playback.
			codecCtx.pix_fmt(AV_PIX_FMT_YUV420P);
			// Blu-ray HD video is BT.709 limited range; tagging it keeps players from
			// guessing (and usually assuming BT.601, which tints greys).
			codecCtx.colorspace(AVCOL_SPC_BT709);
			codecCtx.color_primaries(AVCOL_PRI_BT709);
			codecCtx.color_trc(AVCOL_TRC_BT709);
			codecCtx.color_range(AVCOL_RANGE_MPEG);

			// as this class is used for menus (short videos, very possibly with gradients), we consider
			// enabling x264 grain tuning to preserve fine gradient detail.
			// and we do not care about speed.
			AVDictionary opts = new AVDictionary(null);
			av_dict_set(opts, "preset", "slow", 0);
			av_dict_set(opts, "tune", "grain", 0); // preserves dither/fine gradient detail
			av_dict_set(opts, "x264-params",
					"aq-mode=3:aq-strength=1.0:psy-rd=1.0,0.15:deadzone-intra=0:deadzone-inter=0:no-dct-decimate=1:qpmin=6",
					0);
			int ret = avcodec_open2(codecCtx, codec, opts);
			// not clear if i should free this now or after encoding is done,
			// but freeing it here seems to work.
			av_dict_free(opts);
			if (ret < 0) {
				throw new IOException("avcodec_open2 failed: " + ret);
			}

			// Allocate reusable frames
			AVFrame yuvFrame = av_frame_alloc();
			yuvFrame.format(AV_PIX_FMT_YUV420P);
			yuvFrame.width(width);
			yuvFrame.height(height);
			av_frame_get_buffer(yuvFrame, 0);

			AVFrame bgrFrame = av_frame_alloc();
			bgrFrame.format(AV_PIX_FMT_BGRA);
			bgrFrame.width(width);
			bgrFrame.height(height);
			av_frame_get_buffer(bgrFrame, 0);

			// SWS_ACCURATE_RND and full chroma interpolation cost a little speed but avoid
			// the coarse rounding that turns smooth gradients into visible bands.
			SwsContext swsCtx = sws_getContext(width, height, AV_PIX_FMT_BGRA, width, height, AV_PIX_FMT_YUV420P,
					SWS_BILINEAR | SWS_ACCURATE_RND | SWS_FULL_CHR_H_INT | SWS_FULL_CHR_H_INP, null, null,
					(double[]) null);
			if (swsCtx == null) {
				throw new IOException("Could not allocate SwsContext for " + width + "x" + height + " BGRA to YUV420P");
			}
			// Composited frames are full-range RGB; the output must be BT.709 limited range.
			IntPointer bt709 = sws_getCoefficients(SWS_CS_ITU709);
			int csRet = sws_setColorspaceDetails(swsCtx, bt709, 1, bt709, 0, 0, 1 << 16, 1 << 16);
			if (csRet < 0) {
				log.warn("sws_setColorspaceDetails failed ({}); falling back to swscale defaults (BT.601)", csRet);
			}

			boolean dither = BrtsFileConfig.getInstance().parseBooleanProperty(DITHER_PROPERTY, DEFAULT_DITHER);

			AVPacket packet = av_packet_alloc();

			try (OutputStream out = Files.newOutputStream(outputFile)) {
				MediaRepository repo = new MediaRepositoryImpl();
				try {

					for (int i = 0; i < frameCount; i++) {
						CompositionContextImpl ctx = new CompositionContextImpl(i, composition, baseDir);
						CompositionBuffer cb = new CompositionBuffer(composition, repo, ctx);
						try (ImageFrame frame = cb.compose()) {
							fillBgraFrame(frame.toBufferedImage(), bgrFrame, width, height, dither);
						}
						sws_scale(swsCtx, bgrFrame.data(), bgrFrame.linesize(), 0, height, yuvFrame.data(),
								yuvFrame.linesize());

						yuvFrame.pts(i);
						ret = avcodec_send_frame(codecCtx, yuvFrame);
						if (ret < 0) {
							throw new IOException("avcodec_send_frame failed at frame " + i + ": " + ret);
						}

						drainPackets(codecCtx, packet, out);

						if (i > 0 && i % 50 == 0) {
							log.debug("  encoded {}/{} frames", i, frameCount);
						}
					}

					// Flush encoder
					avcodec_send_frame(codecCtx, null);
					drainPackets(codecCtx, packet, out);

				} finally {
					try {
						repo.close();
					} catch (Exception ignored) {
					}
				}
			} finally {
				av_frame_free(yuvFrame);
				av_frame_free(bgrFrame);
				av_packet_free(packet);
				sws_freeContext(swsCtx);
			}

		} finally {
			avcodec_free_context(codecCtx);
		}
	}

	/** Drains all currently available encoded packets from the codec into {@code out}. */
	private static void drainPackets(AVCodecContext codecCtx, AVPacket packet, OutputStream out) throws IOException {
		while (avcodec_receive_packet(codecCtx, packet) >= 0) {
			int size = packet.size();
			byte[] data = new byte[size];
			packet.data().position(0).get(data);
			out.write(data);
			av_packet_unref(packet);
		}
	}

	/**
	 * Fills a pre-allocated BGRA {@link AVFrame} from a {@link BufferedImage}.
	 *
	 * <p>
	 * {@link org.brts.common.utils.composition.CompositionBuffer#compose()} always returns a {@code TYPE_INT_ARGB}
	 * image. On a little-endian (x86-64) JVM, the {@code int} value {@code 0xAARRGGBB} is stored in memory as bytes
	 * {@code BB GG RR AA}, which is exactly {@code AV_PIX_FMT_BGRA}. We therefore copy the {@code int[]} pixel array
	 * directly into the native frame buffer via a native-order {@link IntBuffer}, with no intermediate
	 * {@link java.awt.Graphics2D} conversion.
	 *
	 * <p>
	 * The only case that falls back to a Graphics2D redraw is when the image dimensions do not match the target size
	 * (resize path).
	 *
	 * @param dither when {@code true}, applies an ordered +/-1 LSB pattern to the RGB channels
	 */
	private static void fillBgraFrame(BufferedImage img, AVFrame bgraFrame, int width, int height, boolean dither) {
		if (img.getWidth() != width || img.getHeight() != height) {
			// After scaling, scaleImage returns TYPE_3BYTE_BGR; convert to ARGB for the
			// common copy path below.
			BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			scaled.createGraphics().drawImage(img, 0, 0, width, height, null);
			img = scaled;
		}
		if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
			BufferedImage argb = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			argb.createGraphics().drawImage(img, 0, 0, null);
			img = argb;
		}

		// TYPE_INT_ARGB int[] on LE: 0xAARRGGBB → bytes BB GG RR AA = AV_PIX_FMT_BGRA.
		int[] argbPixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
		int byteStride = bgraFrame.linesize(0);
		int linestride = byteStride / 4; // stride in ints (4 bytes per BGRA pixel)
		IntBuffer buf = bgraFrame.data(0).position(0).capacity((long) byteStride * height).asByteBuffer()
				.order(ByteOrder.nativeOrder()).asIntBuffer();

		if (!dither) {
			for (int y = 0; y < height; y++) {
				buf.position(y * linestride);
				buf.put(argbPixels, y * width, width);
			}
			return;
		}

		// Dither into a scratch row: argbPixels belongs to the composited image, which may
		// be cached and reused across frames.
		int[] row = new int[width];
		for (int y = 0; y < height; y++) {
			int bayerRow = (y & 7) << 3;
			int srcOffset = y * width;
			for (int x = 0; x < width; x++) {
				row[x] = ditherPixel(argbPixels[srcOffset + x], BAYER_8X8[bayerRow + (x & 7)]);
			}
			buf.position(y * linestride);
			buf.put(row, 0, width);
		}
	}

	/**
	 * Offsets the R, G and B channels of one {@code 0xAARRGGBB} pixel by -1, 0 or +1 depending on the pixel's Bayer
	 * threshold, leaving alpha untouched.
	 */
	private static int ditherPixel(int argb, int threshold) {
		int offset = threshold < 21 ? -1 : (threshold < 43 ? 0 : 1);
		if (offset == 0) {
			return argb;
		}
		int r = clampChannel(((argb >> 16) & 0xFF) + offset);
		int g = clampChannel(((argb >> 8) & 0xFF) + offset);
		int b = clampChannel((argb & 0xFF) + offset);
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	private static int clampChannel(int value) {
		return value < 0 ? 0 : (value > 255 ? 255 : value);
	}

	// -------------------------------------------------------------------------
	// Audio extraction
	// -------------------------------------------------------------------------

	/**
	 * Extracts audio elementary streams from the background video and appends corresponding
	 * {@link M2tsDescriptor.StreamEntry} objects to {@code streams}.
	 *
	 * <p>
	 * Delegates to {@link FfmpegAudioExtractor}, which supports any container format that FFmpeg can demux (MKV, MP4,
	 * M2TS, …).
	 */
	private void appendAudioEntries(String baseVideoPath, Path tempDir, List<M2tsDescriptor.StreamEntry> streams)
			throws IOException {
		Path audioDir = tempDir.resolve("audio");
		Files.createDirectories(audioDir);

		List<FfmpegAudioExtractor.ExtractedAudio> audioStreams = new FfmpegAudioExtractor()
				.extract(Path.of(baseVideoPath), audioDir);

		if (audioStreams.isEmpty()) {
			log.debug("No audio streams found in '{}'", baseVideoPath);
			return;
		}

		int nextPid = BASE_AUDIO_PID;
		for (FfmpegAudioExtractor.ExtractedAudio as : audioStreams) {
			M2tsDescriptor.StreamEntry entry = new M2tsDescriptor.StreamEntry();
			entry.setFile(as.esFile().toString());
			entry.setPid(nextPid++);
			entry.setStreamTypeByte(as.streamTypeByte());
			entry.setLanguage(as.language());
			entry.setBitrateKbps(as.bitrateKbps());
			entry.setChannels(as.channels());
			entry.setSampleRateHz(as.sampleRateHz());
			streams.add(entry);

			log.debug("  Added audio stream from '{}' (type=0x{})", as.esFile().getFileName(),
					Integer.toHexString(as.streamTypeByte()));
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Returns the video path of the base {@link ImageReference}, or {@code null} if the base is a static image or
	 * synthetic.
	 */
	private static String resolveBaseVideoPath(ImagesComposition composition, Path baseDir) {
		// TODO more or less same logic exists in CompositionBuffer
		List<ImageReference> images = composition.getImages();
		if (images == null || images.isEmpty()) {
			return null;
		}
		String baseId = composition.getBaseImageId();
		ImageReference ref;
		if (baseId != null && !baseId.isBlank()) {
			ref = images.stream().filter(r -> baseId.equals(r.getImageId())).findFirst().orElse(null);
		} else {
			ref = images.get(0);
		}
		String videoPath = (ref != null && ref.isVideo()) ? ref.getVideoPath() : null;
		// somehow circumvents the base dir check. TODO mutualize code again
		if (videoPath != null && !Paths.get(videoPath).isAbsolute()) {
			videoPath = baseDir.resolve(videoPath).toString();
		}

		return videoPath;
	}

	/**
	 * Probes an audio file and returns its duration in seconds, or {@code -1.0} if the duration cannot be determined.
	 *
	 * <p>
	 * Delegates to {@link AudioUtils#probeAudioInfo(Path)}.
	 */
	private static double probeAudioDurationSeconds(Path audioPath) {
		return AudioUtils.probeAudioInfo(audioPath).durationSeconds();
	}

}
