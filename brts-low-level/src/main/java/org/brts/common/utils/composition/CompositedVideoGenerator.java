package org.brts.common.utils.composition;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_free;
import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_open2;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_free;
import static org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer;
import static org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR;
import static org.bytedeco.ffmpeg.global.swscale.sws_freeContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_getContext;
import static org.bytedeco.ffmpeg.global.swscale.sws_scale;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.M2tsWriter;
import org.brts.common.m2ts.model.M2tsChapter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.Setter;

/**
 * Makes use of the {@link VideoCompositionBuffer} to generate composited videos.
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
public class CompositedVideoGenerator {

	private static final Logger log = LoggerFactory.getLogger(CompositedVideoGenerator.class);

	/** Standard Blu-ray video PID. */
	private static final int VIDEO_PID = 0x1011;

	/** Base PID for audio streams. */
	private static final int BASE_AUDIO_PID = 0x1100;

	/** H.264/AVC ISO 13818-1 stream_type byte. */
	private static final int H264_STREAM_TYPE = 0x1B;

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
		 * count; an {@link IllegalArgumentException} is thrown when the base is not a video and this value is not
		 * positive.
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
	 * <li>Resolve fps and frameCount (from config or base video).</li>
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
	 * @throws IOException              on any I/O or encoding failure
	 * @throws IllegalArgumentException if frameCount cannot be determined
	 */
	public void generate(ImagesComposition composition, Path outputDir, String clipName, Config config, Path baseDir)
			throws IOException {

		// 1. Resolve fps and frameCount ----------------------------------------
		double fps = config.getFps() > 0 ? config.getFps() : 0;
		int frameCount = config.getFrameCount();

		String baseVideoPath = resolveBaseVideoPath(composition, baseDir);
		if (baseVideoPath != null && (fps <= 0 || frameCount <= 0)) {
			M2tsInfo baseInfo = new M2tsParser().parse(Path.of(baseVideoPath));
			M2tsStreamInfo videoStream = findFirstVideoStream(baseInfo);
			if (videoStream != null && fps <= 0 && videoStream.getFrameRateFps() != null
					&& videoStream.getFrameRateFps() > 0) {
				fps = videoStream.getFrameRateFps();
			}
			if (frameCount <= 0) {
				VideoFrames vf = new M2tsVideoFrames(Path.of(baseVideoPath));
				try {
					frameCount = vf.getFrameCount();
				} finally {
					try {
						vf.close();
					} catch (Exception ignored) {
					}
				}
			}
		}

		if (frameCount <= 0) {
			throw new IllegalArgumentException("frameCount must be explicitly set when the base image is not a video");
		}
		if (fps <= 0) {
			fps = 24.0;
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
			videoEntry.setStreamTypeByte(H264_STREAM_TYPE);
			videoEntry.setFrameRateFps(fps);
			videoEntry.setBitrateKbps(config.getBitrateKbps());
			streams.add(videoEntry);

			// 4. Extract audio streams from base video -------------------------
			if (baseVideoPath != null) {
				appendAudioEntries(baseVideoPath, tempDir, streams);
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
			M2tsWriter m2tsWriter = new M2tsWriter();
			m2tsWriter.write(descriptor, m2tsPath);

			// 6. Write CLPI ---------------------------------------------------
			ClipInfo clipInfo = m2tsWriter.buildClipInfo(descriptor, clipName);
			Path clpiPath = outputDir.resolve(clipName + ".clpi");
			try (OutputStream clpiOut = Files.newOutputStream(clpiPath)) {
				new ClipInfoWriter().write(clipInfo, clpiOut);
			}

			log.info("Wrote {} and {}", m2tsPath.getFileName(), clpiPath.getFileName());

		} finally {
			deleteTempDir(tempDir);
		}
	}

	// -------------------------------------------------------------------------
	// MPEG-2 encoding
	// -------------------------------------------------------------------------

	/**
	 * Encodes all composited frames to a raw H.264 elementary stream file using bytedeco/FFmpeg.
	 *
	 * <p>
	 * Each frame is composited via {@link CompositionBuffer}, converted from BGR24 to YUV420P using {@code sws_scale},
	 * then handed to the H.264 encoder. The {@link MediaRepositoryImpl} is closed when encoding completes so that any
	 * opened {@link M2tsVideoFrames} FFmpeg contexts are released promptly.
	 */
	private void encodeToMpeg4(ImagesComposition composition, int frameCount, double fps, Config config,
			Path outputFile, Path baseDir) throws IOException {

		int width = config.getWidth();
		int height = config.getHeight();
		long bitrate = config.getBitrateKbps() * 1000L;
		int fpsInt = (int) Math.round(fps);

		var codec = avcodec_find_encoder(AV_CODEC_ID_H264);
		if (codec == null) {
			throw new IOException("H.264 video encoder not available in this FFmpeg build");
		}

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
			codecCtx.gop_size(12);
			codecCtx.max_b_frames(0); // B-frames reorder decode vs. display order; the
										// raw ES muxer
										// assigns PTS sequentially and writes PTS-only
										// PES headers, so
										// B-frames produce corrupt DTS and choppy
										// playback.
			codecCtx.pix_fmt(AV_PIX_FMT_YUV420P);

			int ret = avcodec_open2(codecCtx, codec, (org.bytedeco.ffmpeg.avutil.AVDictionary) null);
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
			bgrFrame.format(AV_PIX_FMT_BGR24);
			bgrFrame.width(width);
			bgrFrame.height(height);
			av_frame_get_buffer(bgrFrame, 1);

			SwsContext swsCtx = sws_getContext(width, height, AV_PIX_FMT_BGR24, width, height, AV_PIX_FMT_YUV420P,
					SWS_BILINEAR, null, null, (double[]) null);

			AVPacket packet = av_packet_alloc();

			try (OutputStream out = Files.newOutputStream(outputFile)) {
				MediaRepositoryImpl repo = new MediaRepositoryImpl();
				try {

					for (int i = 0; i < frameCount; i++) {
						CompositionContextImpl ctx = new CompositionContextImpl(i, composition, baseDir);
						CompositionBuffer cb = new CompositionBuffer(composition, repo, ctx);
						BufferedImage img = cb.compose();

						fillBgrFrame(img, bgrFrame, width, height);

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
	 * Fills a pre-allocated BGR24 {@link AVFrame} from a {@link BufferedImage}, scaling the image to the frame's
	 * dimensions if necessary.
	 */
	private static void fillBgrFrame(BufferedImage img, AVFrame bgrFrame, int width, int height) {
		if (img.getWidth() != width || img.getHeight() != height) {
			img = scaleImage(img, width, height);
		}
		if (img.getType() != BufferedImage.TYPE_3BYTE_BGR) {
			img = convertToBgr(img, width, height);
		}

		byte[] bgrBytes = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		int linesize = bgrFrame.linesize(0);
		ByteBuffer buf = bgrFrame.data(0).position(0).capacity((long) linesize * height).asByteBuffer();
		for (int y = 0; y < height; y++) {
			buf.position(y * linesize);
			buf.put(bgrBytes, y * width * 3, width * 3);
		}
	}

	// -------------------------------------------------------------------------
	// Audio extraction
	// -------------------------------------------------------------------------

	/**
	 * Extracts audio elementary streams from the background video and appends corresponding
	 * {@link M2tsDescriptor.StreamEntry} objects to {@code streams}.
	 *
	 * <p>
	 * Output files are produced by {@link M2tsExtractor} and are named {@code pid_<hex>.<ext>} inside a temporary
	 * subdirectory.
	 */
	private void appendAudioEntries(String baseVideoPath, Path tempDir, List<M2tsDescriptor.StreamEntry> streams)
			throws IOException {
		M2tsInfo info = new M2tsParser().parse(Path.of(baseVideoPath));
		List<M2tsStreamInfo> audioStreams = info.getStreams().stream()
				.filter(s -> s.getCodingType() != null && s.getCodingType().isAudio()).toList();

		if (audioStreams.isEmpty()) {
			log.debug("No audio streams found in '{}'", baseVideoPath);
			return;
		}

		Set<Integer> audioPids = new LinkedHashSet<>();
		for (M2tsStreamInfo as : audioStreams) {
			audioPids.add(as.getPid());
		}

		Path audioDir = tempDir.resolve("audio");
		Files.createDirectories(audioDir);
		new M2tsExtractor().extract(Path.of(baseVideoPath), info, audioDir, audioPids);

		int nextPid = BASE_AUDIO_PID;
		for (M2tsStreamInfo as : audioStreams) {
			// FilePacketHandler names files as "pid_<hex>.<ext>"
			String ext = extensionForStream(as);
			Path esFile = audioDir.resolve(String.format("pid_%04x.%s", as.getPid(), ext));
			if (!Files.exists(esFile)) {
				log.warn("Audio ES file not found for PID 0x{}: {}", Integer.toHexString(as.getPid()),
						esFile.getFileName());
				continue;
			}

			M2tsDescriptor.StreamEntry entry = new M2tsDescriptor.StreamEntry();
			entry.setFile(esFile.toString());
			entry.setPid(nextPid++);
			entry.setStreamTypeByte(as.getStreamTypeByte());
			entry.setLanguage(as.getLanguage());
			entry.setBitrateKbps(as.getBitrateKbps());
			entry.setChannels(as.getChannels());
			entry.setSampleRateHz(as.getSampleRateHz());
			streams.add(entry);

			log.debug("  Added audio PID 0x{} ({})", Integer.toHexString(as.getPid()), as.getCodingType());
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

	private static M2tsStreamInfo findFirstVideoStream(M2tsInfo info) {
		return info.getStreams().stream().filter(s -> s.getCodingType() != null && s.getCodingType().isVideo())
				.findFirst().orElse(null);
	}

	/**
	 * Returns the file extension used by {@link org.brts.common.m2ts.FilePacketHandler}.
	 */
	private static String extensionForStream(M2tsStreamInfo s) {
		if (s.getCodingType() == null) {
			return "bin";
		}
		return switch (s.getCodingType()) {
		case LPCM -> "lpcm";
		case DOLBY_AC3 -> "ac3";
		case DOLBY_AC3_PLUS -> "eac3";
		case DOLBY_TRUEHD -> "thd";
		case DTS -> "dts";
		case DTS_HD -> "dtshd";
		case DTS_HD_MASTER_AUDIO -> "dtsma";
		default -> "bin";
		};
	}

	private static BufferedImage scaleImage(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = dst.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return dst;
	}

	private static BufferedImage convertToBgr(BufferedImage src, int w, int h) {
		BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
		Graphics2D g = dst.createGraphics();
		g.drawImage(src, 0, 0, null);
		g.dispose();
		return dst;
	}

	private static void deleteTempDir(Path dir) {
		try (var stream = Files.walk(dir)) {
			stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
				try {
					Files.delete(p);
				} catch (IOException ignored) {
					// best-effort cleanup
				}
			});
		} catch (IOException ignored) {
			// best-effort cleanup
		}
	}

}
