package org.brts.middle.menu.media;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/menu/media/MkvMediaSource.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.brts.common.mkv.MkvDemuxerFactory;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.mkv.SourceMediaInfo;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link MediaSource} implementation that extracts elementary streams from an MKV (Matroska) container using the
 * configured demuxer strategy from {@link MkvDemuxerFactory}.
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

		// 3. Demux the selected tracks using the configured MKV demuxer
		Set<Integer> trackFilter = new LinkedHashSet<>();
		trackFilter.add(videoTrack.getTrackNumber());
		if (audioTrack != null) {
			trackFilter.add(audioTrack.getTrackNumber());
		}

		log.info("Demuxing ES from MKV: {} (tracks {})", mkvFile.getFileName(), trackFilter);
		Map<Integer, Path> demuxed = MkvDemuxerFactory.get().demux(mkvFile, workDir, trackFilter);

		Path videoEsFile = demuxed.get(videoTrack.getTrackNumber());
		if (videoEsFile == null) {
			throw new IOException("MKV demuxer did not produce output for video track " + videoTrack.getTrackNumber());
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
