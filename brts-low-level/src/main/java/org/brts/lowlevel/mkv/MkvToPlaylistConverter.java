package org.brts.lowlevel.mkv;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.brts.common.exception.BrtsException;
import org.brts.common.m2ts.model.M2tsChapter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.menu.TextStyle;
import org.brts.common.mkv.MkvDemuxer;
import org.brts.common.mkv.MkvDemuxerFactory;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.common.utils.FileUtils;
import org.brts.lowlevel.m2ts.M2tsClipWriter;
import org.brts.lowlevel.m2ts.M2tsClipWriterFactory;
import org.brts.lowlevel.m2ts.M2tsWriter;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.PlayItemStream;
import org.brts.lowlevel.model.mpls.PlayMark;
import org.brts.lowlevel.model.mpls.SubPath;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.pgs.PgsGenerator;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.brts.lowlevel.popupmenu.PopupMenuGenerator;
import org.brts.lowlevel.popupmenu.TrackDisplayNameResolver;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.brts.lowlevel.writer.MoviePlaylistWriter;

import lombok.extern.slf4j.Slf4j;

/**
 * Converts an MKV file into a Blu-ray clip triplet: M2TS + CLPI + MPLS.
 * <p>
 * Pipeline:
 * <ol>
 * <li>Parse MKV metadata ({@link MkvSourceMediaParser})</li>
 * <li>Validate Blu-ray compatibility ({@link BlurayCompatibilityValidator})</li>
 * <li>Demux MKV into elementary streams ({@link MkvDemuxer})</li>
 * <li>Convert text subtitles to PGS where needed ({@link PgsGenerator})</li>
 * <li>Build an {@link M2tsDescriptor} and write M2TS + CLPI ({@link M2tsWriter}, {@link ClipInfoWriter})</li>
 * <li>Build and write MPLS ({@link MoviePlaylistWriter})</li>
 * </ol>
 */
@Slf4j
public class MkvToPlaylistConverter {

	// Blu-ray conventional PID ranges
	private static final int VIDEO_PID_BASE = 0x1011; // 4113

	private static final int AUDIO_PID_BASE = 0x1100; // 4352

	private static final int PGS_PID_BASE = 0x1200; // 4608

	/** Standard Blu-ray IGS PID. */
	private static final int IGS_PID = 0x1400;

	private static final long MPLS_TICKS_PER_MILLISECOND = Timestamp.MPLS_TICKS_PER_SECOND / 1000L;

	/**
	 * Configuration for the conversion.
	 */
	@lombok.Data
	@lombok.AllArgsConstructor
	public static class Config {

		private final Path mkvFile;

		private final Path outputDir;

		private final String clipName;

		private Set<Integer> audioTrackFilter;

		private Set<Integer> subtitleTrackFilter;

		private PgsRenderConfig pgsConfig;

		/** 5-digit clip name for the popup menu IGS. When non-null, a popup menu is generated. */
		private String popupMenuClipName;

		/** Optional text style for the popup menu. Passed through to {@link PopupMenuConfig}. */
		private TextStyle popupMenuStyle;

		/** Button layout for the popup menu. */
		private PopupMenuConfig.Layout popupMenuLayout;

		/** Complete popup presentation template from a descriptor. */
		private PopupMenuConfig popupMenuConfig;

		public Config(Path mkvFile, Path outputDir, String clipName) {
			this(mkvFile, outputDir, clipName, null, null, null, null, null, PopupMenuConfig.Layout.VERTICAL_LIST,
					null);
		}

	}

	/**
	 * Result of the conversion.
	 */
	public record Result(Path m2tsFile, Path clpiFile, Path mplsFile) {
	}

	/**
	 * Runs the full MKV-to-playlist conversion pipeline.
	 *
	 * @param config conversion configuration
	 * @return paths to the generated files
	 * @throws IOException   on I/O error
	 * @throws BrtsException if any stream is incompatible with Blu-ray
	 */
	public Result convert(Config config) throws IOException {
		Path mkvFile = config.getMkvFile();
		Path outputDir = config.getOutputDir();
		String clipName = config.getClipName();

		// 1. Parse MKV metadata
		log.info("Parsing MKV: {}", mkvFile);
		MkvSourceMediaParser parser = new MkvSourceMediaParser();
		SourceMediaInfo mediaInfo = parser.parse(mkvFile);

		// 2. Apply track filters
		List<SourceMediaInfo.SourceTrack> selectedTracks = filterTracks(mediaInfo, config);

		// 3. Validate Blu-ray compatibility
		log.info("Validating Blu-ray compatibility for {} tracks", selectedTracks.size());
		BlurayCompatibilityValidator validator = new BlurayCompatibilityValidator();
		SourceMediaInfo filteredInfo = new SourceMediaInfo();
		filteredInfo.setSourcePath(mediaInfo.getSourcePath());
		filteredInfo.setDurationMs(mediaInfo.getDurationMs());
		filteredInfo.setTracks(selectedTracks);
		List<BlurayCompatibilityValidator.TrackValidation> validations = validator.validateOrThrow(filteredInfo);

		// 4. Determine which tracks need PGS conversion
		Set<Integer> textSubTrackNos = new HashSet<>();
		for (BlurayCompatibilityValidator.TrackValidation tv : validations) {
			if (tv.needsConversion()) {
				textSubTrackNos.add(tv.trackNumber());
			}
		}

		// 5. Determine the set of tracks to demux
		Set<Integer> demuxTrackNos = new LinkedHashSet<>();
		for (SourceMediaInfo.SourceTrack t : selectedTracks) {
			demuxTrackNos.add(t.getTrackNumber());
		}

		// 6. Demux MKV
		Path workDir = outputDir.resolve(".mkv_work_" + clipName);
		log.info("Demuxing MKV to {}", workDir);
		Map<Integer, Path> demuxedFiles = MkvDemuxerFactory.get().demux(mkvFile, workDir, demuxTrackNos);

		// 7. Convert text subtitles to PGS
		PgsRenderConfig pgsConfig = config.getPgsConfig() != null ? config.getPgsConfig() : new PgsRenderConfig();
		PgsGenerator pgsGenerator = new PgsGenerator(pgsConfig);

		Map<Integer, Path> pgsConvertedFiles = new HashMap<>();
		for (int trackNo : textSubTrackNos) {
			Path textSubFile = demuxedFiles.get(trackNo);
			if (textSubFile == null) {
				log.warn("Text subtitle track {} was not demuxed, skipping conversion", trackNo);
				continue;
			}
			Path pgsFile = workDir.resolve("track_" + trackNo + ".pgs");
			log.info("Converting text subtitle track {} to PGS: {} → {}", trackNo, textSubFile, pgsFile);
			pgsGenerator.generate(textSubFile, pgsFile);
			pgsConvertedFiles.put(trackNo, pgsFile);
		}

		// 8. Build M2tsDescriptor
		String popupMenuClipName = config.getPopupMenuConfig() != null
				&& config.getPopupMenuConfig().getOutputClipName() != null
						? config.getPopupMenuConfig().getOutputClipName()
						: config.getPopupMenuClipName();
		boolean needsPopupMenu = popupMenuClipName != null;
		M2tsDescriptor descriptor = buildDescriptor(clipName, selectedTracks, demuxedFiles, pgsConvertedFiles,
				textSubTrackNos);

		// 9. Write M2TS+CLPI
		Path streamDir = outputDir.resolve("STREAM");
		Path m2tsPath = streamDir.resolve(clipName + ".m2ts");

		Path clipinfDir = outputDir.resolve("CLIPINF");
		Path clpiPath = clipinfDir.resolve(clipName + ".clpi");

		M2tsClipWriter writer = M2tsClipWriterFactory.createWriter();
		writer.write(descriptor, m2tsPath, clpiPath);

		ClipInfo clipInfo = new ClipInfoParser().parse(clpiPath);
		// enrichClipInfo(clipInfo, selectedTracks, descriptor);

		// 9b. Generate popup menu if requested
		PopupMenuGenerator.Result popupResult = null;
		if (needsPopupMenu) {
			PopupMenuConfig popupConfig = buildPopupMenuConfig(popupMenuClipName, selectedTracks,
					config.getPopupMenuStyle(), config.getPopupMenuLayout(), config.getPopupMenuConfig());
			if (popupConfig != null) {
				PopupMenuGenerator popupGenerator = new PopupMenuGenerator();
				popupResult = popupGenerator.generate(popupConfig, outputDir);
			}
		}

		// 10. Write MPLS
		Path playlistDir = outputDir.resolve("PLAYLIST");
		Path mplsPath = playlistDir.resolve(clipName + ".mpls");
		log.info("Writing MPLS: {}", mplsPath);
		MoviePlaylist playlist = buildPlaylist(clipName, clipInfo, mediaInfo.getDurationMs(), popupResult,
				popupMenuClipName);
		new MoviePlaylistWriter().write(playlist, mplsPath);

		log.info("MKV-to-playlist conversion complete: M2TS={}, CLPI={}, MPLS={}", m2tsPath, clpiPath, mplsPath);
		FileUtils.deleteDir(workDir);
		return new Result(m2tsPath, clpiPath, mplsPath);
	}

	// ── Track filtering ─────────────────────────────────────────────────────

	private List<SourceMediaInfo.SourceTrack> filterTracks(SourceMediaInfo info, Config config) {

		List<SourceMediaInfo.SourceTrack> result = new ArrayList<>();
		for (SourceMediaInfo.SourceTrack track : info.getTracks()) {
			StreamCodingType ct = track.getCodingType();

			if (ct.isVideo()) {
				result.add(track);
			} else if (ct.isAudio()) {
				if (config.getAudioTrackFilter() == null
						|| config.getAudioTrackFilter().contains(track.getTrackNumber())) {
					result.add(track);
				} else {
					log.debug("Skipping audio track {} (not in filter)", track.getTrackNumber());
				}
			} else if (ct.isSubtitle() || ct == StreamCodingType.TEXT_SUBTITLE) {
				if (config.getSubtitleTrackFilter() == null
						|| config.getSubtitleTrackFilter().contains(track.getTrackNumber())) {
					result.add(track);
				} else {
					log.debug("Skipping subtitle track {} (not in filter)", track.getTrackNumber());
				}
			}
		}

		// Ensure at least one video track
		boolean hasVideo = result.stream().anyMatch(t -> t.getCodingType().isVideo());
		if (!hasVideo) {
			throw new BrtsException("No video track found in the MKV file");
		}

		return result;
	}

	// ── M2tsDescriptor builder ──────────────────────────────────────────────

	private M2tsDescriptor buildDescriptor(String clipName, List<SourceMediaInfo.SourceTrack> tracks,
			Map<Integer, Path> demuxedFiles, Map<Integer, Path> pgsConvertedFiles, Set<Integer> textSubTrackNos) {

		M2tsDescriptor desc = new M2tsDescriptor();
		desc.setOutputName(clipName);

		List<M2tsDescriptor.StreamEntry> entries = new ArrayList<>();
		int videoIdx = 0, audioIdx = 0, subIdx = 0;

		for (SourceMediaInfo.SourceTrack track : tracks) {
			StreamCodingType ct = track.getCodingType();
			M2tsDescriptor.StreamEntry entry = new M2tsDescriptor.StreamEntry();

			int trackNo = track.getTrackNumber();

			// Determine the ES file to use
			if (textSubTrackNos.contains(trackNo)) {
				// Use converted PGS file
				Path pgsFile = pgsConvertedFiles.get(trackNo);
				if (pgsFile == null)
					continue;
				entry.setFile(pgsFile.toAbsolutePath().toString());
				entry.setStreamTypeByte(StreamCodingType.PRESENTATION_GRAPHICS.getCodingTypeByte());
				entry.setPid(PGS_PID_BASE + subIdx++);
			} else {
				Path esFile = demuxedFiles.get(trackNo);
				if (esFile == null)
					continue;
				entry.setFile(esFile.toAbsolutePath().toString());
				entry.setStreamTypeByte(ct.getCodingTypeByte());

				if (ct.isVideo()) {
					entry.setPid(VIDEO_PID_BASE + videoIdx++);
					if (track.getFrameRateFps() != null) {
						entry.setFrameRateFps(track.getFrameRateFps());
					}
				} else if (ct.isAudio()) {
					entry.setPid(AUDIO_PID_BASE + audioIdx++);
					entry.setChannels(track.getChannels());
					entry.setSampleRateHz(track.getSampleRateHz());
					entry.setBitrateKbps(track.getBitrateKbps());
				} else if (ct == StreamCodingType.PRESENTATION_GRAPHICS) {
					entry.setPid(PGS_PID_BASE + subIdx++);
				}
			}

			entry.setLanguage(track.getLanguage());
			entries.add(entry);
		}

		desc.setStreams(entries);

		// Single chapter at position 0
		M2tsChapter ch = new M2tsChapter();
		ch.setIndex(0);
		ch.setPtsTicks(0);
		desc.setChapters(List.of(ch));

		return desc;
	}

	// ── Popup Menu Config builder ───────────────────────────────────────────

	private PopupMenuConfig buildPopupMenuConfig(String popupClipName,
			List<SourceMediaInfo.SourceTrack> selectedTracks) {

		List<PopupMenuConfig.TrackEntry> audioEntries = new ArrayList<>();
		List<PopupMenuConfig.TrackEntry> subtitleEntries = new ArrayList<>();

		int audioIdx = 1; // 1-based stream index for SET_STREAM
		int subIdx = 1;

		for (SourceMediaInfo.SourceTrack track : selectedTracks) {
			StreamCodingType ct = track.getCodingType();
			if (ct.isAudio()) {
				PopupMenuConfig.TrackEntry entry = new PopupMenuConfig.TrackEntry();
				entry.setStreamIndex(audioIdx++);
				entry.setDisplayName(TrackDisplayNameResolver.resolve(track));
				entry.setLanguage(track.getLanguage());
				audioEntries.add(entry);
			} else if (ct.isSubtitle() || ct == StreamCodingType.PRESENTATION_GRAPHICS) {
				PopupMenuConfig.TrackEntry entry = new PopupMenuConfig.TrackEntry();
				entry.setStreamIndex(subIdx++);
				entry.setDisplayName(TrackDisplayNameResolver.resolve(track));
				entry.setLanguage(track.getLanguage());
				subtitleEntries.add(entry);
			}
		}

		// Only generate a popup if there are selectable tracks
		if (audioEntries.size() <= 1 && subtitleEntries.size() <= 1) {
			log.info("Skipping popup menu: not enough tracks (audio={}, subtitle={})", audioEntries.size(),
					subtitleEntries.size());
			return null;
		}

		PopupMenuConfig config = new PopupMenuConfig();
		config.setOutputClipName(popupClipName);
		config.setAudioTracks(audioEntries);
		config.setSubtitleTracks(subtitleEntries);
		return config;
	}

	private PopupMenuConfig buildPopupMenuConfig(String popupClipName, List<SourceMediaInfo.SourceTrack> selectedTracks,
			TextStyle popupMenuStyle, PopupMenuConfig.Layout popupMenuLayout, PopupMenuConfig template) {
		PopupMenuConfig config = buildPopupMenuConfig(popupClipName, selectedTracks);
		if (config != null && template != null) {
			config.setScreenWidth(template.getScreenWidth());
			config.setScreenHeight(template.getScreenHeight());
			config.setStyle(template.getStyle());
			config.setLayout(template.getLayout());
			config.setBackgrounds(template.getBackgrounds());
		}
		if (config != null && template == null) {
			if (popupMenuStyle != null) {
				config.setStyle(popupMenuStyle);
			}
			if (popupMenuLayout != null) {
				config.setLayout(popupMenuLayout);
			}
		}
		return config;
	}

	// ── MPLS builder ────────────────────────────────────────────────────────

	private MoviePlaylist buildPlaylist(String clipName, ClipInfo clipInfo, long durationMs,
			PopupMenuGenerator.Result popupResult, String popupClipName) {
		boolean needsPopupMenu = popupResult != null && popupClipName != null;
		MoviePlaylist playlist = new MoviePlaylist();
		playlist.setPlaylistName(clipName);
		playlist.setMenu(needsPopupMenu);

		long mkvDurationTicks = toMplsTicksFromMilliseconds(durationMs);
		long inTime = 0;
		long outTime = mkvDurationTicks;
		boolean usedClpiTiming = false;

		if (clipInfo.getTsRecordingStartPts() != null && clipInfo.getTsRecordingEndPts() != null) {
			long clpiIn = toMplsTicksFrom90Khz(clipInfo.getTsRecordingStartPts().getTicks());
			long clpiOut = toMplsTicksFrom90Khz(clipInfo.getTsRecordingEndPts().getTicks());
			if (clpiOut > clpiIn) {
				inTime = clpiIn;
				outTime = clpiOut;
				usedClpiTiming = true;
			} else {
				log.warn("Ignoring invalid CLPI timing for clip {}: start={} end={}", clipName, clpiIn, clpiOut);
			}
		}

		if (usedClpiTiming && mkvDurationTicks > 0) {
			long clpiDurationTicks = outTime - inTime;
			long delta = Math.abs(clpiDurationTicks - mkvDurationTicks);
			if (delta > Timestamp.MPLS_TICKS_PER_SECOND) {
				log.warn(
						"Duration mismatch for clip {}: CLPI={} ticks ({} s), MKV metadata={} ticks ({} s); using CLPI timing",
						clipName, clpiDurationTicks, clpiDurationTicks / (double) Timestamp.MPLS_TICKS_PER_SECOND,
						mkvDurationTicks, mkvDurationTicks / (double) Timestamp.MPLS_TICKS_PER_SECOND);
			}
		}

		if (outTime <= inTime) {
			throw new BrtsException(String.format(
					"Invalid MPLS timing for clip %s (in=%d, out=%d). Check source duration and CLPI SequenceInfo.",
					clipName, inTime, outTime));
		}

		// PlayItem
		PlayItem playItem = new PlayItem();
		playItem.setClipName(clipName);
		playItem.setConnectionCondition(1);
		playItem.setInTimeTicks(inTime);
		playItem.setOutTimeTicks(outTime);

		// Build stream table from the CLPI streams
		List<PlayItemStream> streams = new ArrayList<>();
		List<ClipStream> clipStreams = clipInfo.getStreams() != null ? clipInfo.getStreams() : List.of();
		for (ClipStream cs : clipStreams) {
			PlayItemStream pis = new PlayItemStream();
			pis.setPid(cs.getPid());
			pis.setCodingType(cs.getCodingType());
			pis.setLanguage(cs.getLanguage());
			pis.setVideoFormat(cs.getVideoFormat());
			pis.setFrameRate(cs.getFrameRate());
			pis.setAudioChannelLayout(cs.getAudioChannelLayout());
			pis.setSampleRate(cs.getSampleRate());
			streams.add(pis);
		}
		playItem.setStreams(streams);
		playlist.setPlayItems(List.of(playItem));

		// Chapter mark at start
		PlayMark mark = new PlayMark();
		mark.setMarkType(0x01);
		mark.setPlayItemRef(0);
		mark.setMarkTimeTicks(inTime);
		mark.setEntryEsPid(0xFFFF);
		playlist.setPlayMarks(List.of(mark));

		// Attach popup menu SubPath if generated
		if (needsPopupMenu) {
			ClipInfo popupClipInfo = null;
			try {
				popupClipInfo = new ClipInfoParser().parse(popupResult.igsClpi());
			} catch (IOException e) {
				log.warn("Could not parse popup menu CLPI, skipping SubPath attachment: {}", e.getMessage());
			}
			if (popupClipInfo != null) {
				long popupIn = 0;
				long popupOut = outTime - inTime;
				if (popupClipInfo.getTsRecordingStartPts() != null && popupClipInfo.getTsRecordingEndPts() != null) {
					long pIn = toMplsTicksFrom90Khz(popupClipInfo.getTsRecordingStartPts().getTicks());
					long pOut = toMplsTicksFrom90Khz(popupClipInfo.getTsRecordingEndPts().getTicks());
					if (pOut > pIn) {
						popupIn = pIn;
						popupOut = pOut;
					}
				}

				SubPath.SubPlayItem subPlayItem = new SubPath.SubPlayItem();
				subPlayItem.setClipName(popupClipName);
				subPlayItem.setConnectionCondition(1);
				subPlayItem.setInTimeTicks(popupIn);
				subPlayItem.setOutTimeTicks(popupOut);
				subPlayItem.setSyncPlayItemId(0);
				// Must be 0 for immediate SubPath start; strict players (PowerDVD)
				// fail to overlay IGS when this equals the first frame PTS.
				subPlayItem.setSyncStartPtsTicks(0);

				SubPath menuSubPath = new SubPath();
				menuSubPath.setSubPathType(3);
				menuSubPath.setRepeatSubPath(false);
				menuSubPath.setSubPlayItems(List.of(subPlayItem));

				playlist.setSubPaths(List.of(menuSubPath));

				// add pseudo-stream

				PlayItemStream s = new PlayItemStream();
				s.setPid(IGS_PID);
				s.setStreamType(PlayItemStream.STREAM_TYPE_OUT_OF_MUX);
				s.setSubpathId(0); // index of menu SubPath
				s.setSubclipId(0); // first clip in that SubPath
				s.setCodingType(StreamCodingType.INTERACTIVE_GRAPHICS);
				s.setLanguage("eng");// TODO better
				streams.add(s);

				log.info("Popup menu SubPath attached: clip={}, timing=[{}, {}]", popupClipName, popupIn, popupOut);
			}
		}

		return playlist;
	}

	private long toMplsTicksFromMilliseconds(long durationMs) {
		return Math.max(0L, durationMs) * MPLS_TICKS_PER_MILLISECOND;
	}

	private long toMplsTicksFrom90Khz(long ticks90Khz) {
		return Math.max(0L, ticks90Khz / 2L);
	}

}
