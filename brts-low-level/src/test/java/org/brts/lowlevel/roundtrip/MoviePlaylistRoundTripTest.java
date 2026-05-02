package org.brts.lowlevel.roundtrip;

import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.mpls.*;
import org.brts.lowlevel.parser.MoviePlaylistParser;
import org.brts.lowlevel.writer.MoviePlaylistWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test for MPLS (Movie Playlist) binary format. Writes a {@link MoviePlaylist} to bytes, re-parses, and
 * asserts field-level consistency.
 */
class MoviePlaylistRoundTripTest {

	private final MoviePlaylistWriter writer = new MoviePlaylistWriter();

	private final MoviePlaylistParser parser = new MoviePlaylistParser();

	// -------------------------------------------------------------------------
	// Tests
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_singlePlayItem_basicFieldsPreserved() throws Exception {
		MoviePlaylist original = buildSimplePlaylist();

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.getPlayItems()).hasSize(1);

		PlayItem item = reparsed.getPlayItems().get(0);
		assertThat(item.getClipName()).isEqualTo("00001");
		assertThat(item.getInTimeTicks()).isEqualTo(0L);
		assertThat(item.getOutTimeTicks()).isEqualTo(8_100_000L);
		assertThat(item.getConnectionCondition()).isEqualTo(1);
	}

	@Test
	void roundTrip_playItemStreams_videoAndAudioPreserved() throws Exception {
		MoviePlaylist original = buildSimplePlaylist();

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		List<PlayItemStream> streams = reparsed.getPlayItems().get(0).getStreams();
		assertThat(streams).hasSize(2);

		PlayItemStream video = streams.stream().filter(s -> s.getCodingType().isVideo()).findFirst().orElseThrow();
		assertThat(video.getPid()).isEqualTo(0x1011);
		assertThat(video.getCodingType()).isEqualTo(StreamCodingType.H264_AVC);
		assertThat(video.getVideoFormat()).isEqualTo(0x06); // 1080p
		assertThat(video.getFrameRate()).isEqualTo(0x04); // 30000/1001

		PlayItemStream audio = streams.stream().filter(s -> s.getCodingType().isAudio()).findFirst().orElseThrow();
		assertThat(audio.getPid()).isEqualTo(0x1100);
		assertThat(audio.getCodingType()).isEqualTo(StreamCodingType.DOLBY_TRUEHD);
		assertThat(audio.getAudioChannelLayout()).isEqualTo(0x06); // multi-channel
		assertThat(audio.getSampleRate()).isEqualTo(0x01); // 48 kHz
		assertThat(audio.getLanguage()).isEqualTo("eng");
	}

	@Test
	void roundTrip_multipleChapterMarks_allPreserved() throws Exception {
		MoviePlaylist original = buildSimplePlaylist();

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		List<PlayMark> marks = reparsed.getPlayMarks();
		assertThat(marks).hasSize(3);

		assertThat(marks.get(0).getMarkType()).isEqualTo(0x01);
		assertThat(marks.get(0).getMarkTimeTicks()).isEqualTo(0L);
		assertThat(marks.get(0).getPlayItemRef()).isEqualTo(0);

		assertThat(marks.get(1).getMarkTimeTicks()).isEqualTo(2_700_000L);
		assertThat(marks.get(2).getMarkTimeTicks()).isEqualTo(5_400_000L);
		assertThat(marks.get(2).getEntryEsPid()).isEqualTo(0x1011);
	}

	@Test
	void roundTrip_multiplePlayItems_allClipNamesPreserved() throws Exception {
		MoviePlaylist original = buildMultiItemPlaylist();

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.getPlayItems()).hasSize(3);
		assertThat(reparsed.getPlayItems().get(0).getClipName()).isEqualTo("00001");
		assertThat(reparsed.getPlayItems().get(1).getClipName()).isEqualTo("00002");
		assertThat(reparsed.getPlayItems().get(2).getClipName()).isEqualTo("00003");
	}

	@Test
	void roundTrip_subtitleStream_languagePreserved() throws Exception {
		MoviePlaylist original = buildPlaylistWithSubtitle();

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		List<PlayItemStream> streams = reparsed.getPlayItems().get(0).getStreams();
		PlayItemStream pg = streams.stream().filter(s -> s.getCodingType() == StreamCodingType.PRESENTATION_GRAPHICS)
				.findFirst().orElseThrow();
		assertThat(pg.getPid()).isEqualTo(0x1200);
		assertThat(pg.getLanguage()).isEqualTo("fra");
	}

	@Test
	void roundTrip_emptyPlaylist_noExceptions() throws Exception {
		MoviePlaylist original = new MoviePlaylist();
		original.setPlaylistName("00002");
		original.setPlayItems(List.of());
		original.setSubPaths(List.of());
		original.setPlayMarks(List.of());

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.getPlayItems()).isEmpty();
		assertThat(reparsed.getPlayMarks()).isEmpty();
	}

	@Test
	void roundTrip_playlistWithSubPath_subPathFieldsPreserved() throws Exception {
		MoviePlaylist original = buildPlaylistWithSubPath();

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.getSubPaths()).hasSize(1);
		SubPath sp = reparsed.getSubPaths().get(0);
		assertThat(sp.getSubPathType()).isEqualTo(3);
		assertThat(sp.isRepeatSubPath()).isFalse();
		assertThat(sp.getSubPlayItems()).hasSize(1);
		SubPath.SubPlayItem spi = sp.getSubPlayItems().get(0);
		assertThat(spi.getClipName()).isEqualTo("00001");
		assertThat(spi.getInTimeTicks()).isEqualTo(0L);
		assertThat(spi.getOutTimeTicks()).isEqualTo(900_000L);
		assertThat(spi.getSyncPlayItemId()).isEqualTo(0);
		assertThat(spi.getSyncStartPtsTicks()).isEqualTo(0L);
	}

	// ─── Menu detection tests ───────────────────────────────────────────────

	@Test
	void menuDetection_igStream_flaggedAsMenu() throws Exception {
		PlayItemStream video = new PlayItemStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setVideoFormat(0x06);
		video.setFrameRate(0x04);

		PlayItemStream ig = new PlayItemStream();
		ig.setPid(0x1400);
		ig.setCodingType(StreamCodingType.INTERACTIVE_GRAPHICS);
		ig.setLanguage("eng");

		PlayItem item = new PlayItem();
		item.setClipName("00001");
		item.setConnectionCondition(1);
		item.setInTimeTicks(0L);
		item.setOutTimeTicks(900_000L);
		item.setStreams(List.of(video, ig));

		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlaylistName("00800");
		pl.setPlayItems(List.of(item));
		pl.setSubPaths(List.of());
		pl.setPlayMarks(List.of());

		byte[] bytes = writeToBytes(pl);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.isMenu()).isTrue();
	}

	@Test
	void menuDetection_noIgStream_notFlaggedAsMenu() throws Exception {
		MoviePlaylist original = buildSimplePlaylist();

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.isMenu()).isFalse();
	}

	@Test
	void menuDetection_repeatedClips_flaggedAsMenu() throws Exception {
		// 5 play-items all referencing the same clip → ≥80 % repetition → menu
		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlaylistName("00900");
		pl.setPlayItems(List.of(simpleItem("00001", 0L, 900_000L), simpleItem("00001", 0L, 900_000L),
				simpleItem("00001", 0L, 900_000L), simpleItem("00001", 0L, 900_000L),
				simpleItem("00001", 0L, 900_000L)));
		pl.setSubPaths(List.of());
		pl.setPlayMarks(List.of());

		byte[] bytes = writeToBytes(pl);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.isMenu()).isTrue();
	}

	@Test
	void menuDetection_distinctClips_notFlaggedAsMenu() throws Exception {
		MoviePlaylist original = buildMultiItemPlaylist(); // 3 distinct clips

		byte[] bytes = writeToBytes(original);
		MoviePlaylist reparsed = parser.parse(new ByteArrayInputStream(bytes));

		assertThat(reparsed.isMenu()).isFalse();
	}

	// -------------------------------------------------------------------------
	// Builders
	// -------------------------------------------------------------------------

	private MoviePlaylist buildSimplePlaylist() {
		PlayItemStream video = new PlayItemStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setVideoFormat(0x06);
		video.setFrameRate(0x04);

		PlayItemStream audio = new PlayItemStream();
		audio.setPid(0x1100);
		audio.setCodingType(StreamCodingType.DOLBY_TRUEHD);
		audio.setAudioChannelLayout(0x06);
		audio.setSampleRate(0x01);
		audio.setLanguage("eng");

		PlayItem item = new PlayItem();
		item.setClipName("00001");
		item.setConnectionCondition(1);
		item.setInTimeTicks(0L);
		item.setOutTimeTicks(8_100_000L);
		item.setStreams(List.of(video, audio));

		PlayMark m0 = mark(0, 0L, 0xFFFF);
		PlayMark m1 = mark(0, 2_700_000L, 0xFFFF);
		PlayMark m2 = mark(0, 5_400_000L, 0x1011);

		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlaylistName("00001");
		pl.setPlayItems(List.of(item));
		pl.setSubPaths(List.of());
		pl.setPlayMarks(List.of(m0, m1, m2));
		return pl;
	}

	private MoviePlaylist buildMultiItemPlaylist() {
		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlaylistName("00002");
		pl.setPlayItems(List.of(simpleItem("00001", 0L, 5_400_000L), simpleItem("00002", 5_400_000L, 10_800_000L),
				simpleItem("00003", 0L, 3_600_000L)));
		pl.setSubPaths(List.of());
		pl.setPlayMarks(List.of());
		return pl;
	}

	private MoviePlaylist buildPlaylistWithSubtitle() {
		PlayItemStream video = new PlayItemStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setVideoFormat(0x06);
		video.setFrameRate(0x04);

		PlayItemStream pg = new PlayItemStream();
		pg.setPid(0x1200);
		pg.setCodingType(StreamCodingType.PRESENTATION_GRAPHICS);
		pg.setLanguage("fra");

		PlayItem item = new PlayItem();
		item.setClipName("00001");
		item.setConnectionCondition(1);
		item.setInTimeTicks(0L);
		item.setOutTimeTicks(8_100_000L);
		item.setStreams(List.of(video, pg));

		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlaylistName("00003");
		pl.setPlayItems(List.of(item));
		pl.setSubPaths(List.of());
		pl.setPlayMarks(List.of());
		return pl;
	}

	private MoviePlaylist buildPlaylistWithSubPath() {
		PlayItemStream video = new PlayItemStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setVideoFormat(0x06);
		video.setFrameRate(0x04);

		PlayItem item = new PlayItem();
		item.setClipName("00001");
		item.setConnectionCondition(1);
		item.setInTimeTicks(0L);
		item.setOutTimeTicks(8_100_000L);
		item.setStreams(List.of(video));

		SubPath.SubPlayItem spi = new SubPath.SubPlayItem();
		spi.setClipName("00001");
		spi.setInTimeTicks(0L);
		spi.setOutTimeTicks(900_000L);
		spi.setSyncPlayItemId(0);
		spi.setSyncStartPtsTicks(0L);

		SubPath sp = new SubPath();
		sp.setSubPathType(3);
		sp.setRepeatSubPath(false);
		sp.setSubPlayItems(List.of(spi));

		MoviePlaylist pl = new MoviePlaylist();
		pl.setPlaylistName("00004");
		pl.setPlayItems(List.of(item));
		pl.setSubPaths(List.of(sp));
		pl.setPlayMarks(List.of());
		return pl;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private PlayItem simpleItem(String clipName, long inTicks, long outTicks) {
		PlayItemStream video = new PlayItemStream();
		video.setPid(0x1011);
		video.setCodingType(StreamCodingType.H264_AVC);
		video.setVideoFormat(0x06);
		video.setFrameRate(0x04);

		PlayItem item = new PlayItem();
		item.setClipName(clipName);
		item.setConnectionCondition(1);
		item.setInTimeTicks(inTicks);
		item.setOutTimeTicks(outTicks);
		item.setStreams(List.of(video));
		return item;
	}

	private PlayMark mark(int playItemRef, long ticks, int esPid) {
		PlayMark m = new PlayMark();
		m.setMarkType(0x01);
		m.setPlayItemRef(playItemRef);
		m.setMarkTimeTicks(ticks);
		m.setEntryEsPid(esPid);
		m.setDurationTicks(0);
		return m;
	}

	private byte[] writeToBytes(MoviePlaylist pl) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(pl, out);
		return out.toByteArray();
	}

}
