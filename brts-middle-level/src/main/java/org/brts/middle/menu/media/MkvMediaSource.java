package org.brts.middle.menu.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.brts.common.mkv.MkvDemuxer;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.mkv.SourceMediaInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link MediaSource} implementation that extracts elementary streams from an MKV (Matroska) container using the
 * pure-Java {@link MkvDemuxer}.
 * <p>
 * The extraction is done by:
 * <ol>
 * <li>Parsing the MKV to discover track metadata</li>
 * <li>Demuxing the selected video and audio tracks directly in-process</li>
 * </ol>
 */
@Slf4j
public class MkvMediaSource implements MediaSource {

	private final Path mkvFile;

	private final Integer videoTrackNumber;

	private final Integer audioTrackNumber;

	/**
	 * @param mkvFile          path to the MKV file
	 * @param videoTrackNumber optional: force a specific video track number (null = first video)
	 * @param audioTrackNumber optional: force a specific audio track number (null = first audio)
	 */
	public MkvMediaSource(Path mkvFile, Integer videoTrackNumber, Integer audioTrackNumber) {
		this.mkvFile = mkvFile;
		this.videoTrackNumber = videoTrackNumber;
		this.audioTrackNumber = audioTrackNumber;
	}

	@Override
	public ExtractionResult extract(Path workDir) throws IOException {
		Files.createDirectories(workDir);

		// 1. Parse MKV metadata
		MkvSourceMediaParser parser = new MkvSourceMediaParser();
		SourceMediaInfo info = parser.parse(mkvFile);

		// 2. Find video and audio tracks
		SourceMediaInfo.SourceTrack videoTrack = null;
		SourceMediaInfo.SourceTrack audioTrack = null;

		for (SourceMediaInfo.SourceTrack track : info.getTracks()) {
			if (track.getCodingType() == null)
				continue;

			if (track.getCodingType().isVideo() && videoTrack == null) {
				if (videoTrackNumber == null || track.getTrackNumber() == videoTrackNumber) {
					videoTrack = track;
				}
			}
			if (track.getCodingType().isAudio() && audioTrack == null) {
				if (audioTrackNumber == null || track.getTrackNumber() == audioTrackNumber) {
					audioTrack = track;
				}
			}
		}

		if (videoTrack == null) {
			throw new IOException("No video track found in " + mkvFile);
		}

		// 3. Demux the selected tracks using pure-Java MkvDemuxer
		Set<Integer> trackFilter = new LinkedHashSet<>();
		trackFilter.add(videoTrack.getTrackNumber());
		if (audioTrack != null) {
			trackFilter.add(audioTrack.getTrackNumber());
		}

		log.info("Demuxing ES from MKV: {} (tracks {})", mkvFile.getFileName(), trackFilter);
		MkvDemuxer demuxer = new MkvDemuxer();
		Map<Integer, Path> demuxed = demuxer.demux(mkvFile, workDir, trackFilter);

		Path videoEsFile = demuxed.get(videoTrack.getTrackNumber());
		if (videoEsFile == null) {
			throw new IOException("MkvDemuxer did not produce output for video track " + videoTrack.getTrackNumber());
		}
		Path audioEsFile = audioTrack != null ? demuxed.get(audioTrack.getTrackNumber()) : null;

		log.info("ES extraction complete: video={}, audio={}", videoEsFile.getFileName(),
				audioEsFile != null ? audioEsFile.getFileName() : "none");

		return new ExtractionResult(videoEsFile, audioEsFile, videoTrack.getCodingType().getCodingTypeByte(),
				audioTrack != null ? audioTrack.getCodingType().getCodingTypeByte() : 0, videoTrack.getFrameRateFps(),
				audioTrack != null ? audioTrack.getLanguage() : null,
				audioTrack != null ? audioTrack.getSampleRateHz() : null,
				audioTrack != null ? audioTrack.getBitrateKbps() : null,
				audioTrack != null ? audioTrack.getChannels() : null);
	}

}
