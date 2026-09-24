package org.brts.lowlevel.mkv;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/test/java/org/brts/lowlevel/mkv/MkvToPlaylistConverterBuildPlaylistTest.java' is part of BRTS.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import org.brts.common.exception.BrtsException;
import org.brts.common.menu.TextStyle;
import org.brts.common.mkv.SourceMediaInfo.SourceTrack;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.common.utils.composition.ImageReference;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.PlayMark;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.BackgroundLayer;
import org.brts.lowlevel.popupmenu.PopupMenuConfig.PageBackgrounds;
import org.junit.jupiter.api.Test;

class MkvToPlaylistConverterBuildPlaylistTest {

	private final MkvToPlaylistConverter converter = new MkvToPlaylistConverter();

	@Test
	void buildPlaylist_prefersValidClpiTimingOverMkvDuration() throws Exception {
		ClipInfo clipInfo = clipInfoWithTiming(90_000L, 270_000L);
		clipInfo.setStreams(List.of(videoStream(0x1011, "eng")));

		MoviePlaylist playlist = invokeBuildPlaylist("00001", clipInfo, 10_000L);

		PlayItem item = playlist.getPlayItems().get(0);
		assertThat(item.getInTimeTicks()).isEqualTo(45_000L);
		assertThat(item.getOutTimeTicks()).isEqualTo(135_000L);
		assertThat(item.getStreams()).hasSize(1);
		assertThat(item.getStreams().get(0).getPid()).isEqualTo(0x1011);
		assertThat(item.getStreams().get(0).getCodingType()).isEqualTo(StreamCodingType.H264_AVC);

		PlayMark mark = playlist.getPlayMarks().get(0);
		assertThat(mark.getMarkTimeTicks()).isEqualTo(45_000L);
	}

	@Test
	void buildPlaylist_usesMkvDurationWhenClpiTimingMissing() throws Exception {
		ClipInfo clipInfo = new ClipInfo();
		clipInfo.setStreams(null);

		MoviePlaylist playlist = invokeBuildPlaylist("00002", clipInfo, 2_000L);

		PlayItem item = playlist.getPlayItems().get(0);
		assertThat(item.getInTimeTicks()).isZero();
		assertThat(item.getOutTimeTicks()).isEqualTo(90_000L);
		assertThat(item.getStreams()).isEmpty();
		assertThat(playlist.getPlayMarks().get(0).getMarkTimeTicks()).isZero();
	}

	@Test
	void buildPlaylist_usesMkvDurationWhenClpiTimingIsInvalid() throws Exception {
		ClipInfo clipInfo = clipInfoWithTiming(270_000L, 90_000L);

		MoviePlaylist playlist = invokeBuildPlaylist("00003", clipInfo, 2_000L);

		PlayItem item = playlist.getPlayItems().get(0);
		assertThat(item.getInTimeTicks()).isZero();
		assertThat(item.getOutTimeTicks()).isEqualTo(90_000L);
	}

	@Test
	void buildPlaylist_throwsWhenTimelineIsNotPositive() {
		ClipInfo clipInfo = clipInfoWithTiming(270_000L, 90_000L);

		assertThatThrownBy(() -> invokeBuildPlaylist("00004", clipInfo, 0L)).isInstanceOf(BrtsException.class)
				.hasMessageContaining("Invalid MPLS timing");
	}

	@Test
	void buildPopupMenuConfig_preservesCompleteTemplate() throws Exception {
		PopupMenuConfig template = new PopupMenuConfig();
		template.setLayout(PopupMenuConfig.Layout.HORIZONTAL_BOTTOM);
		template.setScreenWidth(1280);
		template.setScreenHeight(720);
		TextStyle style = new TextStyle();
		style.setFontSize(32);
		template.setStyle(style);
		ImageReference source = new ImageReference();
		source.setSourcePath("background.png");
		BackgroundLayer layer = new BackgroundLayer();
		layer.setSource(source);
		PageBackgrounds backgrounds = new PageBackgrounds();
		backgrounds.setShared(List.of(layer));
		template.setBackgrounds(backgrounds);

		PopupMenuConfig result = invokeBuildPopupMenuConfig(List.of(videoTrack(1), audioTrack(2), audioTrack(3)),
				template);

		assertThat(result.getLayout()).isEqualTo(PopupMenuConfig.Layout.HORIZONTAL_BOTTOM);
		assertThat(result.getScreenWidth()).isEqualTo(1280);
		assertThat(result.getScreenHeight()).isEqualTo(720);
		assertThat(result.getStyle().getFontSize()).isEqualTo(32);
		assertThat(result.getBackgrounds()).isSameAs(backgrounds);
		assertThat(result.getAudioTracks()).hasSize(2);
	}

	private PopupMenuConfig invokeBuildPopupMenuConfig(List<SourceTrack> tracks, PopupMenuConfig template)
			throws Exception {
		return MkvToPlaylistConverter.buildPopupMenuConfig("00800", tracks, null, PopupMenuConfig.Layout.VERTICAL_LIST,
				template);
	}

	private static SourceTrack audioTrack(int trackNumber) {
		SourceTrack track = new SourceTrack();
		track.setTrackNumber(trackNumber);
		track.setCodingType(StreamCodingType.DOLBY_AC3);
		track.setLanguage("eng");
		return track;
	}

	private static SourceTrack videoTrack(int trackNumber) {
		SourceTrack track = new SourceTrack();
		track.setTrackNumber(trackNumber);
		track.setCodingType(StreamCodingType.H264_AVC);
		track.setLanguage("eng");
		return track;
	}

	private MoviePlaylist invokeBuildPlaylist(String clipName, ClipInfo clipInfo, long durationMs) throws Exception {
		Method method = MkvToPlaylistConverter.class.getDeclaredMethod("buildPlaylist", String.class, ClipInfo.class,
				long.class, org.brts.lowlevel.popupmenu.PopupMenuGenerator.Result.class, String.class);
		method.setAccessible(true);
		try {
			return (MoviePlaylist) method.invoke(converter, clipName, clipInfo, durationMs, null, null);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception exception) {
				throw exception;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw e;
		}
	}

	private ClipInfo clipInfoWithTiming(long startTicks90Khz, long endTicks90Khz) {
		ClipInfo clipInfo = new ClipInfo();
		clipInfo.setTsRecordingStartPts(Timestamp.ofTicks(startTicks90Khz));
		clipInfo.setTsRecordingEndPts(Timestamp.ofTicks(endTicks90Khz));
		return clipInfo;
	}

	private ClipStream videoStream(int pid, String language) {
		ClipStream stream = new ClipStream();
		stream.setPid(pid);
		stream.setCodingType(StreamCodingType.H264_AVC);
		stream.setLanguage(language);
		return stream;
	}

}
