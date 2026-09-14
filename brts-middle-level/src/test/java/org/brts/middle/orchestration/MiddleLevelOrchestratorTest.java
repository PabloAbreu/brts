package org.brts.middle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.menu.TextStyle;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.descriptor.ClipDescriptor;
import org.brts.lowlevel.descriptor.PlaylistDescriptor;
import org.brts.lowlevel.mkv.MkvToPlaylistDescriptor;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.PopupMenuMode;
import org.brts.middle.descriptor.TitleDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

class MiddleLevelOrchestratorTest {

	@TempDir
	Path tempDir;

	private SimpleTitleBuilder titleBuilder;
	private MiddleLevelOrchestrator orchestrator;
	private ObjectMapper mapper;

	@BeforeEach
	void setUp() {
		titleBuilder = mock(SimpleTitleBuilder.class);
		orchestrator = new MiddleLevelOrchestrator(titleBuilder);
		mapper = JsonMapperFactory.get();
	}

	private SimpleTitleBuilder.TitleBuildResult createMockTitleResult(String clipName, int audioTracksCount,
			int subtitleTracksCount) {
		ClipDescriptor clipDescriptor = new ClipDescriptor();
		clipDescriptor.setClipName(clipName);
		PlaylistDescriptor playlistDescriptor = new PlaylistDescriptor();
		playlistDescriptor.setPlaylistName(clipName);

		SourceMediaInfo mediaInfo = new SourceMediaInfo();
		var trackList = new java.util.ArrayList<SourceMediaInfo.SourceTrack>();
		for (int i = 0; i < audioTracksCount; i++) {
			SourceMediaInfo.SourceTrack track = new SourceMediaInfo.SourceTrack();
			track.setTrackNumber(i + 1);
			track.setLanguage("eng");
			track.setCodingType(StreamCodingType.DOLBY_AC3);
			trackList.add(track);
		}
		for (int i = 0; i < subtitleTracksCount; i++) {
			SourceMediaInfo.SourceTrack track = new SourceMediaInfo.SourceTrack();
			track.setTrackNumber(audioTracksCount + i + 1);
			track.setLanguage("eng");
			track.setCodingType(StreamCodingType.PRESENTATION_GRAPHICS);
			trackList.add(track);
		}
		mediaInfo.setTracks(trackList);

		return new SimpleTitleBuilder.TitleBuildResult(clipDescriptor, playlistDescriptor, mediaInfo);
	}

	@Test
	void orchestrate_propagatesPgsConfigToMkvDescriptors() throws IOException {
		when(titleBuilder.build(any(TitleDescriptor.class))).thenReturn(createMockTitleResult("00001", 1, 1));

		DiscDescriptor disc = new DiscDescriptor();
		disc.setDiscName("TestDisc");
		disc.setOutputFolder(tempDir.toString());

		PgsRenderConfig pgsConfig = new PgsRenderConfig();
		pgsConfig.setFontName("Ubuntu");
		pgsConfig.setFontSize(54);
		pgsConfig.setFontColor(0xFFFFCC00);
		disc.setPgsConfig(pgsConfig);

		TitleDescriptor title = new TitleDescriptor();
		title.setTitleId(1);
		title.setSourceMkv("/path/to/video.mkv");
		disc.setTitles(List.of(title));

		orchestrator.orchestrate(disc, tempDir);

		Path mkvDescFile = tempDir.resolve("descriptors").resolve("00001-mkv-descriptor.json");
		assertThat(Files.exists(mkvDescFile)).isTrue();

		MkvToPlaylistDescriptor descriptor = mapper.readValue(mkvDescFile.toFile(), MkvToPlaylistDescriptor.class);
		assertThat(descriptor.getPgsConfig()).isNotNull();
		assertThat(descriptor.getPgsConfig().getFontName()).isEqualTo("Ubuntu");
		assertThat(descriptor.getPgsConfig().getFontSize()).isEqualTo(54);
		assertThat(descriptor.getPgsConfig().getFontColor()).isEqualTo(0xFFFFCC00);
	}

	@Test
	void orchestrate_propagatesPopupMenuConfigToMkvDescriptors() throws IOException {
		when(titleBuilder.build(any(TitleDescriptor.class))).thenReturn(createMockTitleResult("00001", 2, 2));

		DiscDescriptor disc = new DiscDescriptor();
		disc.setDiscName("TestDisc");
		disc.setOutputFolder(tempDir.toString());

		PopupMenuConfig popupConfig = new PopupMenuConfig();
		popupConfig.setLayout(PopupMenuConfig.Layout.HORIZONTAL_BOTTOM);
		popupConfig.setScreenWidth(1920);
		popupConfig.setScreenHeight(1080);
		TextStyle menuTextStyle = new TextStyle();
		menuTextStyle.setFontName("DejaVu Sans");
		menuTextStyle.setFontSize(28);
		popupConfig.setStyle(menuTextStyle);
		disc.setPopupMenu(popupConfig);

		TitleDescriptor title = new TitleDescriptor();
		title.setTitleId(1);
		title.setSourceMkv("/path/to/video.mkv");
		disc.setTitles(List.of(title));

		orchestrator.orchestrate(disc, tempDir);

		Path mkvDescFile = tempDir.resolve("descriptors").resolve("00001-mkv-descriptor.json");
		assertThat(Files.exists(mkvDescFile)).isTrue();

		MkvToPlaylistDescriptor descriptor = mapper.readValue(mkvDescFile.toFile(), MkvToPlaylistDescriptor.class);
		assertThat(descriptor.getPopupMenu()).isNotNull();
		assertThat(descriptor.getPopupMenu().getOutputClipName()).isEqualTo("00501");
		assertThat(descriptor.getPopupMenu().getLayout()).isEqualTo(PopupMenuConfig.Layout.HORIZONTAL_BOTTOM);
		assertThat(descriptor.getPopupMenu().getScreenWidth()).isEqualTo(1920);
		assertThat(descriptor.getPopupMenu().getScreenHeight()).isEqualTo(1080);
		assertThat(descriptor.getPopupMenu().getStyle()).isNotNull();
		assertThat(descriptor.getPopupMenu().getStyle().getFontName()).isEqualTo("DejaVu Sans");
		assertThat(descriptor.getPopupMenu().getStyle().getFontSize()).isEqualTo(28);
	}

	@Test
	void orchestrate_stylePrecedence_popupMenuOverridesPopupStyleAndGlobalStyle() throws IOException {
		when(titleBuilder.build(any(TitleDescriptor.class))).thenReturn(createMockTitleResult("00001", 2, 1));

		DiscDescriptor disc = new DiscDescriptor();
		disc.setDiscName("TestDisc");
		disc.setOutputFolder(tempDir.toString());

		TextStyle globalStyle = new TextStyle();
		globalStyle.setFontName("GlobalFont");
		globalStyle.setFontSize(20);
		globalStyle.setNormalColor("#111111");
		disc.setStyle(globalStyle);

		TextStyle popupStyle = new TextStyle();
		popupStyle.setFontSize(30);
		popupStyle.setNormalColor("#222222");
		disc.setPopupStyle(popupStyle);

		PopupMenuConfig popupConfig = new PopupMenuConfig();
		TextStyle popupMenuStyle = new TextStyle();
		popupMenuStyle.setNormalColor("#333333");
		popupConfig.setStyle(popupMenuStyle);
		disc.setPopupMenu(popupConfig);

		TitleDescriptor title = new TitleDescriptor();
		title.setTitleId(1);
		title.setSourceMkv("/path/to/video.mkv");
		disc.setTitles(List.of(title));

		orchestrator.orchestrate(disc, tempDir);

		Path mkvDescFile = tempDir.resolve("descriptors").resolve("00001-mkv-descriptor.json");
		MkvToPlaylistDescriptor descriptor = mapper.readValue(mkvDescFile.toFile(), MkvToPlaylistDescriptor.class);

		assertThat(descriptor.getPopupMenu()).isNotNull();
		TextStyle resolvedStyle = descriptor.getPopupMenu().getStyle();
		assertThat(resolvedStyle).isNotNull();
		// fontName inherited from globalStyle, fontSize from popupStyle, normalColor from popupMenu.style
		assertThat(resolvedStyle.getFontName()).isEqualTo("GlobalFont");
		assertThat(resolvedStyle.getFontSize()).isEqualTo(30);
		assertThat(resolvedStyle.getNormalColor()).isEqualTo("#333333");
	}

	@Test
	void orchestrate_whenPopupMenuForcedFalse_noPopupMenuInMkvDescriptor() throws IOException {
		when(titleBuilder.build(any(TitleDescriptor.class))).thenReturn(createMockTitleResult("00001", 2, 2));

		DiscDescriptor disc = new DiscDescriptor();
		disc.setDiscName("TestDisc");
		disc.setOutputFolder(tempDir.toString());

		PopupMenuConfig popupConfig = new PopupMenuConfig();
		disc.setPopupMenu(popupConfig);

		TitleDescriptor title = new TitleDescriptor();
		title.setTitleId(1);
		title.setSourceMkv("/path/to/video.mkv");
		title.setPopupMenu(PopupMenuMode.FALSE);
		disc.setTitles(List.of(title));

		orchestrator.orchestrate(disc, tempDir);

		Path mkvDescFile = tempDir.resolve("descriptors").resolve("00001-mkv-descriptor.json");
		MkvToPlaylistDescriptor descriptor = mapper.readValue(mkvDescFile.toFile(), MkvToPlaylistDescriptor.class);
		assertThat(descriptor.getPopupMenu()).isNull();
	}
}
