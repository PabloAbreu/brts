package org.brts.middle.api;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.mkv.SourceMediaParser;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.descriptor.ClipDescriptor;
import org.brts.lowlevel.descriptor.PlaylistDescriptor;
import org.brts.middle.descriptor.TitleDescriptor;
import org.brts.middle.pid.PidAllocator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Middle-level API for creating a simple Blu-ray title from a single MKV file.
 * <p>
 * Given a {@link TitleDescriptor}, this service:
 * <ol>
 * <li>Auto-parses the source MKV to discover tracks</li>
 * <li>Auto-assigns PIDs using {@link PidAllocator}</li>
 * <li>Produces a {@link ClipDescriptor} and a {@link PlaylistDescriptor} that can be fed to the low-level writers
 * directly or written to JSON for manual inspection / re-use.</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleTitleBuilder {
	private final SourceMediaParser mediaParser;

	/**
	 * Builds low-level descriptors for a single title.
	 *
	 * @param descriptor the middle-level title description
	 * @return a {@link TitleBuildResult} containing the low-level descriptors
	 */
	public TitleBuildResult build(TitleDescriptor descriptor) throws IOException {
		log.info("Building title {} from {}", descriptor.getTitleId(), descriptor.getSourceMkv());

		Path sourcePath = Path.of(descriptor.getSourceMkv());
		SourceMediaInfo mediaInfo = mediaParser.parse(sourcePath);

		PidAllocator pids = new PidAllocator();

		// Build ClipDescriptor from parsed tracks
		ClipDescriptor clipDescriptor = new ClipDescriptor();
		String clipName = String.format("%05d", descriptor.getTitleId());
		clipDescriptor.setClipName(clipName);
		clipDescriptor.setSourceMkv(descriptor.getSourceMkv());

		List<ClipDescriptor.TrackMapping> mappings = new ArrayList<>();
		List<Integer> playlistPids = new ArrayList<>();

		for (SourceMediaInfo.SourceTrack track : mediaInfo.getTracks()) {
			if (track.getCodingType() == null)
				continue;

			// Filter audio/subtitle tracks by requested languages
			if (track.getCodingType().isAudio()
					&& !shouldIncludeByLanguage(track.getLanguage(), descriptor.getAudioLanguages()))
				continue;
			if (track.getCodingType().isSubtitle()
					&& !shouldIncludeByLanguage(track.getLanguage(), descriptor.getSubtitleLanguages()))
				continue;

			int pid = pids.allocate(track.getCodingType());
			ClipDescriptor.TrackMapping mapping = new ClipDescriptor.TrackMapping();
			mapping.setMkvTrackNumber(track.getTrackNumber());
			mapping.setTargetPid(pid);
			mapping.setLanguage(track.getLanguage());
			mapping.setIncludeInPlaylist(true);
			mappings.add(mapping);
			playlistPids.add(pid);
		}
		clipDescriptor.setTracks(mappings);

		// Build PlaylistDescriptor
		PlaylistDescriptor playlistDescriptor = new PlaylistDescriptor();
		String playlistName = clipName;
		playlistDescriptor.setPlaylistName(playlistName);

		PlaylistDescriptor.PlayItemDescriptor playItem = new PlaylistDescriptor.PlayItemDescriptor();
		playItem.setClipName(clipName);
		playItem.setInTimeTicks(0);
		// Duration in MPLS 45 kHz ticks from parsed duration (milliseconds → ticks)
		long durationTicks = mediaInfo.getDurationMs() * (Timestamp.MPLS_TICKS_PER_SECOND / 1000L);
		playItem.setOutTimeTicks(durationTicks);
		playItem.setStreamPids(playlistPids);
		playlistDescriptor.setPlayItems(List.of(playItem));

		// Convert chapter seconds → ticks
		List<PlaylistDescriptor.ChapterDescriptor> chapters = new ArrayList<>();
		if (descriptor.getChapters() != null) {
			for (TitleDescriptor.ChapterMarker cm : descriptor.getChapters()) {
				PlaylistDescriptor.ChapterDescriptor ch = new PlaylistDescriptor.ChapterDescriptor();
				ch.setPlayItemRef(0);
				ch.setMarkTimeTicks(Math.round(cm.getTimeSeconds() * Timestamp.MPLS_TICKS_PER_SECOND));
				chapters.add(ch);
			}
		}
		playlistDescriptor.setChapters(chapters);

		return new TitleBuildResult(clipDescriptor, playlistDescriptor, mediaInfo);
	}

	private boolean shouldIncludeByLanguage(String trackLang, List<String> filter) {
		if (filter == null || filter.isEmpty())
			return true;
		if (trackLang == null)
			return false;
		return filter.contains(trackLang);
	}

	// -------------------------------------------------------------------------

	/** Result of a title build: the low-level descriptors + parsed media info. */
	public record TitleBuildResult(ClipDescriptor clipDescriptor, PlaylistDescriptor playlistDescriptor,
			SourceMediaInfo mediaInfo) {
	}

}
