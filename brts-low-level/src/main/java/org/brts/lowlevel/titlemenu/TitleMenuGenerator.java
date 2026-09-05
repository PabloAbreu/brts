package org.brts.lowlevel.titlemenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.m2ts.AudioChannelLayoutConverter;
import org.brts.common.m2ts.IStreamInfo;
import org.brts.common.m2ts.M2tsClipWriter;
import org.brts.common.m2ts.M2tsClipWriterFactory;
import org.brts.common.m2ts.M2tsClipWriterImpl;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsIgsMuxer;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.Extensions;
import org.brts.common.utils.FileUtils;
import org.brts.common.utils.composition.CompositedVideoGenerator;
import org.brts.common.utils.composition.ImageReference;
import org.brts.common.utils.composition.ImagesComposition;
import org.brts.common.utils.paths.BrPath.BdmvPath;
import org.brts.common.utils.paths.BrPath.BrRoot;
import org.brts.common.utils.paths.BrPath.ClipinfPath;
import org.brts.common.utils.paths.BrPath.PlaylistPath;
import org.brts.common.utils.paths.BrPath.StreamPath;
import org.brts.lowlevel.clpi.M2tsClpiRegenBuilder;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.PlayItemStream;
import org.brts.lowlevel.model.mpls.SubPath;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.brts.lowlevel.titlemenu.layout.LayoutResult;
import org.brts.lowlevel.titlemenu.layout.TextListLayout;
import org.brts.lowlevel.titlemenu.layout.ThumbnailGridLayout;
import org.brts.lowlevel.titlemenu.layout.TitleMenuLayout;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.brts.lowlevel.writer.MoviePlaylistWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * Top-level orchestrator for title menu generation.
 * <p>
 * Coordinates the full pipeline:
 * <ol>
 * <li>Resolve the layout implementation from the descriptor's layout type</li>
 * <li>Compute button images and positions via the layout strategy</li>
 * <li>Generate or mux the background M2TS + CLPI</li>
 * <li>Build and encode the IGS display set into a menu M2TS + CLPI</li>
 * <li>Build the MPLS playlist with SubPath type 3 referencing the menu clip</li>
 * </ol>
 *
 * <h2>Output structure</h2>
 *
 * <pre>
 * outputDir/
 *   BDMV/
 *     STREAM/
 *       &lt;outputBackgroundName&gt;.m2ts
 *       &lt;outputMenuName&gt;.m2ts
 *     CLIPINF/
 *       &lt;outputBackgroundName&gt;.clpi
 *       &lt;outputMenuName&gt;.clpi
 *     PLAYLIST/
 *       &lt;outputPlaylistName&gt;.mpls
 * </pre>
 */
@Slf4j
public class TitleMenuGenerator {
	/** Standard Blu-ray IGS stream type byte (0x91). */
	private static final int IGS_STREAM_TYPE = 0x91;

	/** Standard Blu-ray IGS PID. */
	private static final int IGS_PID = 0x1400;

	private static final long DEFAULT_PLAYITEM_DURATION_TICKS = 10L * 45_000L;

	/** Duration (90 kHz) to pad the IGS clip timeline. 10 seconds is generous; commercial discs use ~2 s. */
	private static final long IGS_CLIP_DURATION_90KHZ = 10L * 90_000L;

	/** Low mux rate for IGS-only clips (2 Mbps). Keeps file size small during timeline padding. */
	private static final int IGS_TARGET_BITRATE_KBPS = 2_000;

	/**
	 * Generates a title menu from the given descriptor.
	 *
	 * @param descriptor the title menu descriptor
	 * @param outputDir  root output directory (typically the BDMV folder or its parent)
	 * @param baseDir    base directory for resolving relative paths in the descriptor
	 * @throws IOException on any I/O or generation error
	 */
	public void generate(TitleMenuDescriptor descriptor, Path outputDir, Path baseDir) throws IOException {
		BrRoot brRoot = BrRoot.root(outputDir, true);

		BdmvPath bdmv = brRoot.bdmv();
		StreamPath streamPath = bdmv.stream();
		ClipinfPath clipinfPath = bdmv.clipinf();

		// ── 1. Resolve layout ────────────────────────────────────────────────

		TitleMenuLayout layoutImpl = resolveLayout(descriptor.getLayout().getType());
		LayoutResult layoutResult = layoutImpl.layout(descriptor, baseDir);

		log.info("Layout computed: {} buttons, compositeBackground={}", layoutResult.getButtons().size(),
				layoutResult.isCompositeBackground());

		// ── 2. Generate/mux background M2TS + CLPI ──────────────────────────

		String bgName = descriptor.getOutputBackgroundName();
		M2tsDescriptor desc = null;
		if (layoutResult.isCompositeBackground() && layoutResult.getBackgroundComposition() != null) {
			desc = generateCompositedBackground(layoutResult.getBackgroundComposition(), descriptor, streamPath,
					clipinfPath, bgName, baseDir);
		} else {
			desc = generateSimpleBackground(descriptor.getBackgroundMedia(), descriptor, streamPath, clipinfPath,
					bgName, baseDir);
		}

		// ── 3. Build and encode IGS → menu M2TS + CLPI ─────────────────────

		String menuName = descriptor.getOutputMenuName();
		TitleMenuIgsBuilder igsBuilder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = igsBuilder.build(layoutResult, descriptor);

		// Match IGS video descriptor frame rate to the background video so
		// strict players accept the overlay.
		int bgFrameRateCode = deriveBackgroundFrameRateCode(desc);
		displaySet.getCompositionSegment().getVideoDescriptor().setFrameRateCode(bgFrameRateCode);

		IgsMuxer igsMuxer = new IgsMuxer();
		byte[] igsEs = igsMuxer.encodeDisplaySet(displaySet);

		Path workDir = Files.createTempDirectory("brts-title-menu-");
		try {
			Path igsEsFile = workDir.resolve("menu.igs");
			Files.write(igsEsFile, igsEs);
			log.info("IGS ES written: {} bytes", igsEs.length);
			Path menuM2ts = streamPath.m2ts(menuName);
			Path menuClpi = clipinfPath.clpi(menuName);
			boolean newImplementation = true;
			if (newImplementation) {
				M2tsIgsMuxer m2tsigsMuxer = new M2tsIgsMuxer();
				m2tsigsMuxer.mux(igsEsFile, menuM2ts);

				ClipInfo clipInfo = new M2tsClpiRegenBuilder().build(menuM2ts, menuName);

				new ClipInfoWriter().write(clipInfo, menuClpi);
				log.info("Menu M2TS and CLPI written: {}, {}", menuM2ts.getFileName(), menuClpi.getFileName());
			} else {
				// Mux IGS into its own M2TS + CLPI
				M2tsDescriptor menuDesc = new M2tsDescriptor();
				List<M2tsDescriptor.StreamEntry> menuStreams = new ArrayList<>();
				M2tsDescriptor.StreamEntry igsStream = new M2tsDescriptor.StreamEntry();
				igsStream.setFile(igsEsFile.toAbsolutePath().toString());
				igsStream.setPid(IGS_PID);
				igsStream.setStreamTypeByte(IGS_STREAM_TYPE);
				menuStreams.add(igsStream);
				menuDesc.setStreams(menuStreams);

				// Align IGS PTS origin with background video — strict players (e.g. PowerDVD)
				// require both clips to share the same PTS timeline for overlay to work.
				ClipTiming bgTimingForIgs = resolveClipTiming(bgName, clipinfPath);
				menuDesc.setInitialPtsOffsetTicks(bgTimingForIgs.inTimeTicks() * 2);
				// Extend IGS clip timeline by a short duration past its start PTS so the CLPI
				// reports a valid non-zero range. IGS display sets are persistent once decoded
				// (epoch start) — the clip does NOT need to span the full background duration.
				// Commercial discs typically use ~2 s for the IGS clip.
				menuDesc.setMinEndPtsTicks(bgTimingForIgs.inTimeTicks() * 2 + IGS_CLIP_DURATION_90KHZ);
				// IGS-only clips carry very little actual data; use a low mux rate to avoid
				// bloating the file with null packets during timeline padding.
				menuDesc.setTargetBitrateKbps(IGS_TARGET_BITRATE_KBPS);

				// TsMuxerM2tsClipWriter does not support IGS muxing; hardwire to M2tsClipWriterImpl.
				M2tsClipWriter writer = new M2tsClipWriterImpl();
				writer.write(menuDesc, menuM2ts, menuClpi);
				log.info("Menu M2TS written: {}", menuM2ts.getFileName());
			}

		} finally {
			FileUtils.deleteDir(workDir);
		}

		// ── 4. Build MPLS playlist ──────────────────────────────────────────

		generatePlaylist(descriptor, bdmv, desc);

		log.info("Title menu generation complete. Output: {}", outputDir);
	}

	// ── Background generation ───────────────────────────────────────────────

	private M2tsDescriptor generateCompositedBackground(ImagesComposition composition, TitleMenuDescriptor descriptor,
			StreamPath streamDir, ClipinfPath clipDir, String clipName, Path baseDir) throws IOException {
		CompositedVideoGenerator generator = new CompositedVideoGenerator();
		CompositedVideoGenerator.Config config = new CompositedVideoGenerator.Config();
		config.setWidth(descriptor.getScreenWidth());
		config.setHeight(descriptor.getScreenHeight());
		// Let the generator derive fps and frameCount from the base video
		// Output goes to STREAM dir, CLPI built by the generator
		// The generator writes <clipName>.m2ts and <clipName>.clpi to outputDir
		// But CompositedVideoGenerator writes to a flat dir, need to handle that
		Path tempOut = Files.createTempDirectory("brts-title-bg-");
		try {
			var desc = generator.generate(composition, tempOut, clipName, config, baseDir);
			// Move results to proper BDMV structure
			FileUtils.move(tempOut.resolve(clipName + ".m2ts"), streamDir.m2ts(clipName));
			FileUtils.move(tempOut.resolve(clipName + ".clpi"), clipDir.clpi(clipName));
			return desc;
		} finally {
			FileUtils.deleteDir(tempOut);
		}
	}

	private M2tsDescriptor generateSimpleBackground(BackgroundSource bgSource, TitleMenuDescriptor descriptor,
			StreamPath streamDir, ClipinfPath clipDir, String clipName, Path baseDir) throws IOException {
		if (bgSource == null) {
			throw new IllegalArgumentException("backgroundMedia must be specified in the descriptor");
		}

		if (bgSource.isVideo()) {
			// Extract video and audio from the source and mux into M2TS + CLPI
			String videoPath = bgSource.getVideoPath();
			if (!Path.of(videoPath).isAbsolute()) {
				videoPath = baseDir.resolve(videoPath).toString();
			}

			M2tsInfo info = new M2tsParser().parse(Path.of(videoPath));
			M2tsDescriptor desc = new M2tsDescriptor();
			desc.setOutputName(clipName);
			List<M2tsDescriptor.StreamEntry> streams = new ArrayList<>();

			// Video stream
			M2tsStreamInfo videoStream = info.getStreams().stream()
					.filter(s -> s.getCodingType() != null && s.getCodingType().isVideo()).findFirst().orElse(null);

			Path tempDir = Files.createTempDirectory("brts-bg-extract-");
			try {
				// Extract elementary streams
				var allPids = new java.util.LinkedHashSet<Integer>();
				for (M2tsStreamInfo s : info.getStreams()) {
					allPids.add(s.getPid());
				}
				new M2tsExtractor().extract(Path.of(videoPath), info, tempDir, allPids);

				// Video
				if (videoStream != null) {
					String ext = Extensions.extensionForStream(videoStream);
					Path esFile = tempDir.resolve(String.format("pid_%04x.%s", videoStream.getPid(), ext));
					if (Files.exists(esFile)) {
						M2tsDescriptor.StreamEntry entry = new M2tsDescriptor.StreamEntry();
						entry.setFile(esFile.toAbsolutePath().toString());
						entry.setPid(0x1011);
						entry.setStreamTypeByte(videoStream.getStreamTypeByte());
						entry.setFrameRateFps(videoStream.getFrameRateFps());
						entry.setVideoFormat(videoStream.getVideoFormat());
						streams.add(entry);
					}
				}

				// Audio streams
				int nextAudioPid = 0x1100;
				for (M2tsStreamInfo s : info.getStreams()) {
					if (s.getCodingType() != null && s.getCodingType().isAudio()) {
						String ext = Extensions.extensionForStream(s);
						Path esFile = tempDir.resolve(String.format("pid_%04x.%s", s.getPid(), ext));
						if (Files.exists(esFile)) {
							M2tsDescriptor.StreamEntry entry = new M2tsDescriptor.StreamEntry();
							entry.setFile(esFile.toAbsolutePath().toString());
							entry.setPid(nextAudioPid++);
							entry.setStreamTypeByte(s.getStreamTypeByte());
							entry.setLanguage(s.getLanguage());
							entry.setBitrateKbps(s.getBitrateKbps());
							entry.setChannels(s.getChannels());
							entry.setSampleRateHz(s.getSampleRateHz());
							streams.add(entry);
						}
					}
				}

				desc.setStreams(streams);
				Path m2tsPath = streamDir.m2ts(clipName);
				Path clpiPath = clipDir.clpi(clipName);
				M2tsClipWriter clipWriter = M2tsClipWriterFactory.createWriter();
				clipWriter.write(desc, m2tsPath, clpiPath);
				return desc;
			} finally {
				FileUtils.deleteDir(tempDir);
			}

		} else if (bgSource.isComposition() || bgSource.isImage()) {
			// Composition or static image: render via CompositedVideoGenerator
			ImagesComposition composition = bgSource.isComposition() ? bgSource.getComposition()
					: buildSingleImageComposition(bgSource.getImagePath(), baseDir);

			CompositedVideoGenerator.Config config = new CompositedVideoGenerator.Config();
			config.setWidth(descriptor.getScreenWidth());
			config.setHeight(descriptor.getScreenHeight());

			// If durationSeconds is set, derive frameCount from it (fps defaults to 24 when
			// the composition has no video base); the generator will override fps/frameCount
			// from the base video when one is present in the composition.
			if (bgSource.getDurationSeconds() != null && bgSource.getDurationSeconds() > 0) {
				double fps = config.getFps() > 0 ? config.getFps() : 24.0;
				config.setFrameCount((int) Math.round(bgSource.getDurationSeconds() * fps));
			}

			// Extra audio from a separate ES file
			if (bgSource.getAudioPath() != null && !bgSource.getAudioPath().isBlank()) {
				String audioPath = bgSource.getAudioPath();
				if (!Path.of(audioPath).isAbsolute()) {
					audioPath = baseDir.resolve(audioPath).toString();
				}
				config.setExtraAudioPath(audioPath);
			}

			Path tempOut = Files.createTempDirectory("brts-title-bg-comp-");
			try {
				M2tsDescriptor generatedDesc = new CompositedVideoGenerator().generate(composition, tempOut, clipName,
						config, baseDir);
				FileUtils.move(tempOut.resolve(clipName + ".m2ts"), streamDir.m2ts(clipName));
				FileUtils.move(tempOut.resolve(clipName + ".clpi"), clipDir.clpi(clipName));
				return generatedDesc;
			} finally {
				FileUtils.deleteDir(tempOut);
			}
		} else {
			throw new IllegalArgumentException(
					"backgroundMedia must specify one of: videoPath, imagePath, or composition");
		}
	}

	/**
	 * Forges a trivial {@link ImagesComposition} that renders a single static image as-is. Used when
	 * {@link BackgroundSource#getImagePath()} is set.
	 */
	private static ImagesComposition buildSingleImageComposition(String imagePath, Path baseDir) {
		ImageReference ref = new ImageReference();
		ref.setImageId("background");
		// Use absolute path to avoid baseDir resolution issues inside the generator
		Path resolved = Path.of(imagePath).isAbsolute() ? Path.of(imagePath) : baseDir.resolve(imagePath);
		ref.setSourcePath(resolved.toAbsolutePath().toString());

		ImagesComposition composition = new ImagesComposition();
		composition.setImages(List.of(ref));
		composition.setBaseImageId("background");
		composition.setCompositions(List.of());
		return composition;
	}

	// ── Playlist generation ─────────────────────────────────────────────────

	private void generatePlaylist(TitleMenuDescriptor descriptor, BdmvPath bdmv, M2tsDescriptor bgDescriptor)
			throws IOException {
		PlaylistPath playlistDir = bdmv.playlist();
		FileUtils.createDirectories(playlistDir.getPath());
		Path playlistPath = playlistDir.mpls(descriptor.getOutputPlaylistName());

		String bgName = descriptor.getOutputBackgroundName();
		String menuName = descriptor.getOutputMenuName();
		int loopCount = descriptor.getBackgroundLoopCount();

		// Resolve background clip timing from its CLPI
		ClipTiming bgTiming = resolveClipTiming(bgName, bdmv.clipinf());

		// Build PlayItems: background looped N times
		List<PlayItem> playItems = new ArrayList<>();
		for (int i = 0; i < loopCount; i++) {
			PlayItem item = new PlayItem();
			item.setClipName(bgName);
			// First PlayItem uses cc=1 (non-seamless start); subsequent use cc=5
			// (seamless continuation) — matches commercial disc convention.
			item.setConnectionCondition(i == 0 ? 1 : 5);
			item.setInTimeTicks(bgTiming.inTimeTicks());
			item.setOutTimeTicks(bgTiming.outTimeTicks());
			item.setStreams(new ArrayList<>());
			// streams from bgDescriptor
			// video
			bgDescriptor.getStreams().stream().filter(s -> StreamCodingType.fromByte(s.getStreamTypeByte()).isVideo())
					.findFirst().ifPresent(videoStream -> {
						PlayItemStream s = new PlayItemStream();
						s.setPid(videoStream.getPid());
						s.setCodingType(StreamCodingType.fromByte(videoStream.getStreamTypeByte()));
						s.setFrameRate(IStreamInfo.frameRateFromFps(videoStream.getFrameRateFps()));
						s.setVideoFormat(videoStream.getVideoFormat());
						item.getStreams().add(s);
					});
			// first audio
			bgDescriptor.getStreams().stream().filter(s -> StreamCodingType.fromByte(s.getStreamTypeByte()).isAudio())
					.findFirst().ifPresent(audioStream -> {
						PlayItemStream s = new PlayItemStream();
						s.setPid(audioStream.getPid());
						s.setCodingType(StreamCodingType.fromByte(audioStream.getStreamTypeByte()));
						s.setSampleRate(deriveSampleRateCode(audioStream.getSampleRateHz()));
						s.setAudioChannelLayout(
								AudioChannelLayoutConverter.channelsToLayout(audioStream.getChannels()));
						s.setLanguage(audioStream.getLanguage());
						item.getStreams().add(s);
					});
			// IGS stream referencing the menu SubPath (out-of-mux, stream_type 2)
			PlayItemStream s = new PlayItemStream();
			s.setPid(IGS_PID);
			s.setStreamType(PlayItemStream.STREAM_TYPE_OUT_OF_MUX);
			s.setSubpathId(0); // index of menu SubPath
			s.setSubclipId(0); // first clip in that SubPath
			s.setCodingType(StreamCodingType.INTERACTIVE_GRAPHICS);
			s.setLanguage("eng");// TODO better
			item.getStreams().add(s);
			playItems.add(item);
		}

		// Menu SubPath type 3 (out-of-mux IGS)
		ClipTiming menuTiming = resolveClipTiming(menuName, bdmv.clipinf());
		// sync_start_PTS_of_PlayItem must be 0 so that the SubPath starts
		// immediately when the first PlayItem begins playback. Commercial discs
		// use 0 here; strict players (PowerDVD) fail to trigger the IGS overlay
		// when this equals the first frame PTS (race condition on sync check).
		SubPath.SubPlayItem menuSubPlayItem = new SubPath.SubPlayItem();
		menuSubPlayItem.setClipName(menuName);
		menuSubPlayItem.setConnectionCondition(1);
		menuSubPlayItem.setInTimeTicks(menuTiming.inTimeTicks());
		menuSubPlayItem.setOutTimeTicks(menuTiming.outTimeTicks());
		menuSubPlayItem.setSyncPlayItemId(0); // sync to first PlayItem
		menuSubPlayItem.setSyncStartPtsTicks(0);

		SubPath menuSubPath = new SubPath();
		menuSubPath.setSubPathType(3);
		menuSubPath.setRepeatSubPath(false);
		menuSubPath.setSubPlayItems(List.of(menuSubPlayItem));

		// Assemble playlist
		MoviePlaylist playlist = new MoviePlaylist();
		playlist.setMenu(true);
		playlist.setPlaylistName(descriptor.getOutputPlaylistName());
		playlist.setPlayItems(playItems);
		playlist.setSubPaths(List.of(menuSubPath));

		new MoviePlaylistWriter().write(playlist, playlistPath);
		log.info("Playlist written: {}", playlistPath.getFileName());
	}

	private record ClipTiming(long inTimeTicks, long outTimeTicks) {
	}

	private ClipTiming resolveClipTiming(String clipName, ClipinfPath clipinf) {
		Path clpiPath = clipinf.clpi(clipName);
		if (!Files.exists(clpiPath)) {
			log.warn("CLPI not found for '{}', using default timing", clipName);
			return new ClipTiming(0, DEFAULT_PLAYITEM_DURATION_TICKS);
		}

		try {
			ClipInfo clipInfo = new ClipInfoParser().parse(clpiPath);
			if (clipInfo.getTsRecordingStartPts() != null && clipInfo.getTsRecordingEndPts() != null) {
				// ClipInfo stores PTS in 90 kHz; MPLS fields use 45 kHz
				long startPts = clipInfo.getTsRecordingStartPts().getTicks() / 2;
				long endPts = clipInfo.getTsRecordingEndPts().getTicks() / 2;
				if (endPts > startPts) {
					return new ClipTiming(startPts, endPts);
				}
			}
		} catch (IOException e) {
			log.warn("Could not parse CLPI for '{}': {}", clipName, e.getMessage());
		}
		return new ClipTiming(0, DEFAULT_PLAYITEM_DURATION_TICKS);
	}

	// ── Layout resolution ───────────────────────────────────────────────────

	private TitleMenuLayout resolveLayout(LayoutType type) {
		return switch (type) {
		case TEXT_LIST -> new TextListLayout();
		case THUMBNAIL_GRID -> new ThumbnailGridLayout();
		};
	}

	private static int deriveSampleRateCode(Integer sampleRateHz) {
		if (sampleRateHz == null || sampleRateHz <= 48000)
			return 1;
		if (sampleRateHz <= 96000)
			return 4;
		return 5;
	}

	/**
	 * Derives the Blu-ray frame_rate_id for the IGS video descriptor from the background M2tsDescriptor's first video
	 * stream. Falls back to 1 (23.976 fps) if no video stream is present.
	 */
	private static int deriveBackgroundFrameRateCode(M2tsDescriptor bgDesc) {
		if (bgDesc == null || bgDesc.getStreams() == null)
			return 1;
		return bgDesc.getStreams().stream().filter(s -> StreamCodingType.fromByte(s.getStreamTypeByte()).isVideo())
				.findFirst().map(s -> IStreamInfo.frameRateFromFps(s.getFrameRateFps())).orElse(1);
	}
}
