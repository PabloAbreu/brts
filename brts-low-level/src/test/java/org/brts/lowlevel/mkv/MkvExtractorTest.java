package org.brts.lowlevel.mkv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/mkv/MkvExtractorTest.java' is part of BRTS.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.brts.common.mkv.EsDemuxer;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class MkvExtractorTest {

	@TempDir
	Path tempDir;

	@Test
	void resolveTrackSelection_selectsAllWhenNoFilter() {
		MkvExtractor extractor = new MkvExtractor(mock(MkvSourceMediaParser.class), mock(EsDemuxer.class));
		SourceMediaInfo info = mediaInfo(sampleTracks());

		MkvExtractor.Config config = new MkvExtractor.Config(Path.of("input.mkv"), tempDir);

		Set<Integer> selected = extractor.resolveTrackSelection(info, config);

		assertThat(selected).containsExactly(1, 2, 3);
	}

	@Test
	void resolveTrackSelection_rejectsUnknownTrackNumbers() {
		MkvExtractor extractor = new MkvExtractor(mock(MkvSourceMediaParser.class), mock(EsDemuxer.class));
		SourceMediaInfo info = mediaInfo(sampleTracks());

		MkvExtractor.Config config = new MkvExtractor.Config(Path.of("input.mkv"), tempDir);
		config.setTrackNumbers(new LinkedHashSet<>(Set.of(2, 99)));

		assertThatThrownBy(() -> extractor.resolveTrackSelection(info, config))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not found in source MKV");
	}

	@Test
	void resolveTrackSelection_intersectsTrackNumbersWithTypeFilter() {
		MkvExtractor extractor = new MkvExtractor(mock(MkvSourceMediaParser.class), mock(EsDemuxer.class));
		SourceMediaInfo info = mediaInfo(sampleTracks());

		MkvExtractor.Config config = new MkvExtractor.Config(Path.of("input.mkv"), tempDir);
		config.setTrackNumbers(new LinkedHashSet<>(Set.of(1, 2, 3)));
		config.setAudioOnly(true);

		Set<Integer> selected = extractor.resolveTrackSelection(info, config);

		assertThat(selected).containsExactly(2);
	}

	@Test
	void resolveTrackSelection_failsWhenFiltersSelectNothing() {
		MkvExtractor extractor = new MkvExtractor(mock(MkvSourceMediaParser.class), mock(EsDemuxer.class));
		SourceMediaInfo info = mediaInfo(sampleTracks());

		MkvExtractor.Config config = new MkvExtractor.Config(Path.of("input.mkv"), tempDir);
		config.setTrackNumbers(new LinkedHashSet<>(Set.of(2)));
		config.setSubtitlesOnly(true);

		assertThatThrownBy(() -> extractor.resolveTrackSelection(info, config))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No tracks selected");
	}

	@Test
	void extract_parsesResolvesAndDemuxes() throws Exception {
		Path input = Files.createFile(tempDir.resolve("sample.mkv"));
		Path output = tempDir.resolve("out");

		MkvSourceMediaParser parser = mock(MkvSourceMediaParser.class);
		EsDemuxer demuxer = mock(EsDemuxer.class);

		SourceMediaInfo info = mediaInfo(sampleTracks());
		when(parser.parse(input)).thenReturn(info);

		Map<Integer, Path> demuxed = new LinkedHashMap<>();
		demuxed.put(2, output.resolve("track_2.ac3"));
		when(demuxer.demux(eq(input), eq(output), any())).thenReturn(demuxed);

		MkvExtractor extractor = new MkvExtractor(parser, demuxer);
		MkvExtractor.Config config = new MkvExtractor.Config(input, output);
		config.setAudioOnly(true);

		MkvExtractor.Result result = extractor.extract(config);

		assertThat(result.selectedTrackNumbers()).containsExactly(2);
		assertThat(result.extractedFiles()).containsEntry(2, output.resolve("track_2.ac3"));
		assertThat(Files.isDirectory(output)).isTrue();

		verify(parser).parse(input);
		verify(demuxer).demux(eq(input), eq(output), eq(new LinkedHashSet<>(Set.of(2))));
	}

	private static SourceMediaInfo mediaInfo(List<SourceMediaInfo.SourceTrack> tracks) {
		SourceMediaInfo info = new SourceMediaInfo();
		info.setTracks(tracks);
		return info;
	}

	private static List<SourceMediaInfo.SourceTrack> sampleTracks() {
		SourceMediaInfo.SourceTrack video = new SourceMediaInfo.SourceTrack();
		video.setTrackNumber(1);
		video.setCodingType(StreamCodingType.H264_AVC);

		SourceMediaInfo.SourceTrack audio = new SourceMediaInfo.SourceTrack();
		audio.setTrackNumber(2);
		audio.setCodingType(StreamCodingType.DOLBY_AC3);

		SourceMediaInfo.SourceTrack subtitle = new SourceMediaInfo.SourceTrack();
		subtitle.setTrackNumber(3);
		subtitle.setCodingType(StreamCodingType.TEXT_SUBTITLE);

		return List.of(video, audio, subtitle);
	}

}
