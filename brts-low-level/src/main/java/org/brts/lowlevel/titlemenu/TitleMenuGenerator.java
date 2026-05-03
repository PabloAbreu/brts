package org.brts.lowlevel.titlemenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.m2ts.M2tsClipWriter;
import org.brts.common.m2ts.M2tsClipWriterFactory;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.utils.Extensions;
import org.brts.common.utils.FileUtils;
import org.brts.common.utils.composition.CompositedVideoGenerator;
import org.brts.common.utils.composition.ImagesComposition;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.SubPath;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;
import org.brts.lowlevel.titlemenu.descriptor.LayoutType;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.brts.lowlevel.titlemenu.layout.LayoutResult;
import org.brts.lowlevel.titlemenu.layout.TextListLayout;
import org.brts.lowlevel.titlemenu.layout.ThumbnailGridLayout;
import org.brts.lowlevel.titlemenu.layout.TitleMenuLayout;
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
 *   STREAM/
 *     &lt;outputBackgroundName&gt;.m2ts
 *     &lt;outputMenuName&gt;.m2ts
 *   CLIPINF/
 *     &lt;outputBackgroundName&gt;.clpi
 *     &lt;outputMenuName&gt;.clpi
 *   PLAYLIST/
 *     &lt;outputPlaylistName&gt;.mpls
 * </pre>
 */
@Slf4j
public class TitleMenuGenerator {
	/** Standard Blu-ray IGS stream type byte (0x91). */
	private static final int IGS_STREAM_TYPE = 0x91;

	/** Standard Blu-ray IGS PID. */
	private static final int IGS_PID = 0x1400;

	private static final long DEFAULT_PLAYITEM_DURATION_TICKS = 10L * 90_000L;

	/**
	 * Generates a title menu from the given descriptor.
	 *
	 * @param descriptor the title menu descriptor
	 * @param outputDir  root output directory (typically the BDMV folder or its parent)
	 * @param baseDir    base directory for resolving relative paths in the descriptor
	 * @throws IOException on any I/O or generation error
	 */
	public void generate(TitleMenuDescriptor descriptor, Path outputDir, Path baseDir) throws IOException {
		Files.createDirectories(outputDir);

		Path streamDir = outputDir.resolve("STREAM");
		Path clipDir = outputDir.resolve("CLIPINF");
		Path playlistDir = outputDir.resolve("PLAYLIST");
		Files.createDirectories(streamDir);
		Files.createDirectories(clipDir);
		Files.createDirectories(playlistDir);

		// ── 1. Resolve layout ────────────────────────────────────────────────

		TitleMenuLayout layoutImpl = resolveLayout(descriptor.getLayout().getType());
		LayoutResult layoutResult = layoutImpl.layout(descriptor, baseDir);

		log.info("Layout computed: {} buttons, compositeBackground={}", layoutResult.getButtons().size(),
				layoutResult.isCompositeBackground());

		// ── 2. Generate/mux background M2TS + CLPI ──────────────────────────

		String bgName = descriptor.getOutputBackgroundName();
		Path bgM2ts = streamDir.resolve(bgName + ".m2ts");
		Path bgClpi = clipDir.resolve(bgName + ".clpi");

		if (layoutResult.isCompositeBackground() && layoutResult.getBackgroundComposition() != null) {
			generateCompositedBackground(layoutResult.getBackgroundComposition(), descriptor, streamDir, clipDir,
					bgName, baseDir);
		} else {
			generateSimpleBackground(descriptor.getBackgroundMedia(), streamDir, clipDir, bgName, baseDir);
		}

		// ── 3. Build and encode IGS → menu M2TS + CLPI ─────────────────────

		String menuName = descriptor.getOutputMenuName();
		TitleMenuIgsBuilder igsBuilder = new TitleMenuIgsBuilder();
		IgsDisplaySet displaySet = igsBuilder.build(layoutResult, descriptor);

		IgsMuxer igsMuxer = new IgsMuxer();
		byte[] igsEs = igsMuxer.encodeDisplaySet(displaySet);

		Path workDir = Files.createTempDirectory("brts-title-menu-");
		try {
			Path igsEsFile = workDir.resolve("menu.igs");
			Files.write(igsEsFile, igsEs);
			log.info("IGS ES written: {} bytes", igsEs.length);

			// Mux IGS into its own M2TS + CLPI
			M2tsDescriptor menuDesc = new M2tsDescriptor();
			List<M2tsDescriptor.StreamEntry> menuStreams = new ArrayList<>();
			M2tsDescriptor.StreamEntry igsStream = new M2tsDescriptor.StreamEntry();
			igsStream.setFile(igsEsFile.toAbsolutePath().toString());
			igsStream.setPid(IGS_PID);
			igsStream.setStreamTypeByte(IGS_STREAM_TYPE);
			menuStreams.add(igsStream);
			menuDesc.setStreams(menuStreams);

			Path menuM2ts = streamDir.resolve(menuName + ".m2ts");
			Path menuClpi = clipDir.resolve(menuName + ".clpi");
			M2tsClipWriter writer = M2tsClipWriterFactory.createWriter();
			writer.write(menuDesc, menuM2ts, menuClpi);

			log.info("Menu M2TS written: {}", menuM2ts.getFileName());

		} finally {
			FileUtils.deleteDir(workDir);
		}

		// ── 4. Build MPLS playlist ──────────────────────────────────────────

		generatePlaylist(descriptor, outputDir);

		log.info("Title menu generation complete. Output: {}", outputDir);
	}

	// ── Background generation ───────────────────────────────────────────────

	private void generateCompositedBackground(ImagesComposition composition, TitleMenuDescriptor descriptor,
			Path streamDir, Path clipDir, String clipName, Path baseDir) throws IOException {
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
			generator.generate(composition, tempOut, clipName, config, baseDir);
			// Move results to proper BDMV structure
			Files.move(tempOut.resolve(clipName + ".m2ts"), streamDir.resolve(clipName + ".m2ts"));
			Files.move(tempOut.resolve(clipName + ".clpi"), clipDir.resolve(clipName + ".clpi"));
		} finally {
			FileUtils.deleteDir(tempOut);
		}
	}

	private void generateSimpleBackground(BackgroundSource bgSource, Path streamDir, Path clipDir, String clipName,
			Path baseDir) throws IOException {
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
				Path m2tsPath = streamDir.resolve(clipName + ".m2ts");
				Path clpiPath = clipDir.resolve(clipName + ".clpi");
				M2tsClipWriter clipWriter = M2tsClipWriterFactory.createWriter();
				clipWriter.write(desc, m2tsPath, clpiPath);

			} finally {
				FileUtils.deleteDir(tempDir);
			}

		} else if (bgSource.isImageWithAudio()) {
			// Static image + separate audio: construct M2TS from image frames + audio ES
			throw new UnsupportedOperationException(
					"Image+audio background source not yet implemented. Use a video source or THUMBNAIL_GRID layout.");
		} else {
			throw new IllegalArgumentException("backgroundMedia must specify either videoPath or imagePath+audioPath");
		}
	}

	// ── Playlist generation ─────────────────────────────────────────────────

	private void generatePlaylist(TitleMenuDescriptor descriptor, Path outputDir) throws IOException {
		Path playlistDir = outputDir.resolve("PLAYLIST");
		Files.createDirectories(playlistDir);
		Path playlistPath = playlistDir.resolve(descriptor.getOutputPlaylistName() + ".mpls");

		String bgName = descriptor.getOutputBackgroundName();
		String menuName = descriptor.getOutputMenuName();
		int loopCount = descriptor.getBackgroundLoopCount();

		// Resolve background clip timing from its CLPI
		ClipTiming bgTiming = resolveClipTiming(bgName, outputDir);

		// Build PlayItems: background looped N times
		List<PlayItem> playItems = new ArrayList<>();
		for (int i = 0; i < loopCount; i++) {
			PlayItem item = new PlayItem();
			item.setClipName(bgName);
			item.setConnectionCondition(1);
			item.setInTimeTicks(bgTiming.inTimeTicks());
			item.setOutTimeTicks(bgTiming.outTimeTicks());
			playItems.add(item);
		}

		// Menu SubPath type 3 (out-of-mux IGS)
		ClipTiming menuTiming = resolveClipTiming(menuName, outputDir);
		SubPath.SubPlayItem menuSubPlayItem = new SubPath.SubPlayItem();
		menuSubPlayItem.setClipName(menuName);
		menuSubPlayItem.setInTimeTicks(menuTiming.inTimeTicks());
		menuSubPlayItem.setOutTimeTicks(menuTiming.outTimeTicks());
		menuSubPlayItem.setSyncPlayItemId(0); // sync to first PlayItem
		menuSubPlayItem.setSyncStartPtsTicks(bgTiming.inTimeTicks());

		SubPath menuSubPath = new SubPath();
		menuSubPath.setSubPathType(3);
		menuSubPath.setRepeatSubPath(true);
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

	private ClipTiming resolveClipTiming(String clipName, Path outputDir) {
		Path clpiPath = outputDir.resolve("CLIPINF").resolve(clipName + ".clpi");
		if (!Files.exists(clpiPath)) {
			log.warn("CLPI not found for '{}', using default timing", clipName);
			return new ClipTiming(0, DEFAULT_PLAYITEM_DURATION_TICKS);
		}

		try {
			ClipInfo clipInfo = new ClipInfoParser().parse(clpiPath);
			if (clipInfo.getTsRecordingStartPts() != null && clipInfo.getTsRecordingEndPts() != null) {
				long startPts = clipInfo.getTsRecordingStartPts().getTicks();
				long endPts = clipInfo.getTsRecordingEndPts().getTicks();
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
}
