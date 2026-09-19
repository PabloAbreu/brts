package org.brts.middle.orchestration;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-middle-level/src/main/java/org/brts/middle/orchestration/MiddleLevelOrchestrator.java' is part of BRTS.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.menu.TextStyle;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.paths.BrPath.BrRoot;
import org.brts.lowlevel.bdmv.NavigationCommandMnemonic;
import org.brts.lowlevel.bdmv.NavigationCommandUtils;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.IndexBdmv.TitleEntry;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.model.bdmv.MovieObjects.MovieObject;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.brts.lowlevel.mkv.MkvToPlaylistDescriptor;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.brts.lowlevel.titlemenu.descriptor.BackgroundSource;
import org.brts.lowlevel.titlemenu.descriptor.LayoutConfig;
import org.brts.lowlevel.titlemenu.descriptor.TitleMenuDescriptor;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.PopupMenuMode;
import org.brts.middle.descriptor.TitleDescriptor;
import org.brts.middle.descriptor.TitleMenuConfig;
import org.brts.middle.orchestration.launch.DeferredLaunchGenerator;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Middle-level orchestrator: processes a {@link DiscDescriptor}, builds low-level descriptors for each title, and
 * writes them to an output directory along with an orchestration script.
 * <p>
 * Output structure:
 *
 * <pre>
 * &lt;outputDir&gt;/
 *   descriptors/
 *     00001.clip-descriptor.json
 *     00001.playlist-descriptor.json
 *     ...
 *   orchestrate.sh   ← shell script invoking brt-cli low-level commands in order
 * </pre>
 *
 * The stream selection feature (with a settings menu for audio/subs) works consistently only when input titles have the
 * exact same stream structure (like episodes of a TV show). Note that if you use this class to repackage existing media
 * files from a source Blu-ray disc, keep in mind the possibility that some particular episodes might have additional
 * streams (like commentary tracks).
 *
 */
@Slf4j
@RequiredArgsConstructor
public class MiddleLevelOrchestrator {
	private static final String MOVIE_OBJECT_JSON = "MovieObject.json";

	private static final String INDEX_JSON = "index.json";

	private final SimpleTitleBuilder titleBuilder;

	private final DeferredLaunchGenerator launchGenerator;

	private final ObjectMapper mapper = JsonMapperFactory.get();

	/**
	 * Processes the disc descriptor and writes low-level descriptors + orchestration script.
	 *
	 * @param disc      the middle-level disc description
	 * @param outputDir directory where descriptors and the script will be written
	 */
	public void orchestrate(DiscDescriptor disc, Path outputDir) throws IOException {
		if (outputDir == null) {
			// when no explicit output dir is given, we create a temporary directory
			// because we still need that, even if the user is not interested.
			outputDir = Files.createTempDirectory("brts-orchestrate-");
		}
		Path descriptorsDir = outputDir.resolve("descriptors");
		Files.createDirectories(descriptorsDir);
		log.debug("Orchestrating disc '{}' to output directory {}", disc.getDiscName(), outputDir);

		Path mediaFolder = Paths.get(disc.getOutputFolder()).toAbsolutePath().normalize();
		Path discPath = mediaFolder.resolve(disc.getDiscName());
		BrRoot brRoot = BrRoot.root(discPath, true);
		Path bdmv = brRoot.bdmv().getPath();

		IndexBdmv index = new IndexBdmv();
		index.setContentProviderName(BrtsFileConfig.getInstance().getProperty("index.bdmv.contentProviderName"));
		index.setDiscApplicationType(1);
		index.setVersion("0200");
		List<TitleEntry> titles = new ArrayList<>();
		int movieObjectIndex = 0;
		List<MovieObject> movieObjects = new ArrayList<>();
		int nbTitles = disc.getTitles().size();
		Set<Integer> titleIds = new HashSet<>();

		boolean generateTitleMenu = nbTitles > 1 && disc.getTitleMenuConfig() != null;
		TitleMenuConfig menuConfig = generateTitleMenu ? disc.getTitleMenuConfig() : null;
		boolean generateStreamSelectionProgram = menuConfig != null
				&& ((menuConfig.getAudioItems() != null && !menuConfig.getAudioItems().isEmpty())
						|| (menuConfig.getSubtitleItems() != null && !menuConfig.getSubtitleItems().isEmpty()));

		// Resolve disc-wide popup style: popupStyle merged over global style (null-safe)
		TextStyle resolvedPopupStyle = resolvePopupStyle(disc);

		for (TitleDescriptor title : disc.getTitles()) {
			// first check that the title ID is valid (1-based, sequential)
			if (title.getTitleId() < 1 || title.getTitleId() > nbTitles) {
				throw new IllegalArgumentException("Invalid title ID " + title.getTitleId() + " for title '"
						+ title.getSourceMkv() + "': must be between 1 and " + nbTitles);
			}
			if (!titleIds.add(title.getTitleId())) {
				throw new IllegalArgumentException(
						"Duplicate title ID " + title.getTitleId() + " for title '" + title.getSourceMkv() + "'");
			}
			SimpleTitleBuilder.TitleBuildResult result = titleBuilder.build(title);

			String clipName = result.clipDescriptor().getClipName();

			// Resolve popup menu toggle
			String popupClipName = resolvePopupMenuClipName(title, result.mediaInfo(), clipName);

			launchGenerator.comment("Title " + title.getTitleId() + " — " + title.getSourceMkv());
			MkvToPlaylistDescriptor mkvDescriptor = new MkvToPlaylistDescriptor();
			mkvDescriptor.setInput(title.getSourceMkv());
			mkvDescriptor.setClipName(clipName);
			if (disc.getPgsConfig() != null) {
				mkvDescriptor.setPgsConfig(disc.getPgsConfig());
			}
			if (popupClipName != null) {
				PopupMenuConfig popupMenuConfig = new PopupMenuConfig();
				if (disc.getPopupMenu() != null) {
					popupMenuConfig.setLayout(disc.getPopupMenu().getLayout());
					popupMenuConfig.setScreenWidth(disc.getPopupMenu().getScreenWidth());
					popupMenuConfig.setScreenHeight(disc.getPopupMenu().getScreenHeight());
					popupMenuConfig.setBackgrounds(disc.getPopupMenu().getBackgrounds());
				}
				popupMenuConfig.setOutputClipName(popupClipName);
				if (resolvedPopupStyle != null) {
					popupMenuConfig.setStyle(resolvedPopupStyle);
				}
				mkvDescriptor.setPopupMenu(popupMenuConfig);
			}
			File mkvDescriptorFile = descriptorsDir.resolve(clipName + "-mkv-descriptor.json").toFile();
			mapper.writeValue(mkvDescriptorFile, mkvDescriptor);
			launchGenerator.mkvToPlaylist(mkvDescriptorFile.toPath(), bdmv);
			TitleEntry entry = new TitleEntry();
			entry.setObjectType(1);// HDMV
			entry.setHdmvObjectId(movieObjectIndex++);
			titles.add(entry);
			MovieObject movieObject = new MovieObject();
			movieObject.setResumeIntentionFlag(true);
			List<NavigationCommand> navigationCommands = new ArrayList<>();
			if (generateStreamSelectionProgram) {
				navigationCommands.addAll(NavigationCommandUtils.buildAudioSubtitleSelectionProgram(
						countAudioTracks(result.mediaInfo(), title), countSubtitleTracks(result.mediaInfo(), title)));
			}
			navigationCommands.add(
					NavigationCommand.compile(NavigationCommandMnemonic.PLAY_PL, title.getTitleId(), true, 0, true));
			navigationCommands.add(NavigationCommand.compile(NavigationCommandMnemonic.BREAK, 0, false, 0, false));
			movieObject.setNavigationCommands(navigationCommands);
			movieObjects.add(movieObject);
		}

		MovieObject topMenuMovieObject = new MovieObject();
		topMenuMovieObject.setResumeIntentionFlag(true);

		MovieObject firstPlayMovieObject = new MovieObject();
		firstPlayMovieObject.setResumeIntentionFlag(true);

		if (generateTitleMenu) {
			TitleMenuDescriptor menuDescriptor = buildTitleMenuDescriptor(disc, menuConfig);

			// Write descriptor to file so the CLI can reference it
			File menuDescriptorFile = descriptorsDir.resolve("title-menu-descriptor.json").toFile();
			mapper.writeValue(menuDescriptorFile, menuDescriptor);

			Path baseDir = resolveBaseDir(menuConfig, bdmv);

			// Menu playlist number (parsed from 5-digit output name)
			int menuPlaylistNumber = Integer.parseInt(menuConfig.getOutputPlaylistName());

			// Top Menu and First Play both play the menu playlist
			topMenuMovieObject.setNavigationCommands(NavigationCommandUtils.playPlaylist(menuPlaylistNumber));
			firstPlayMovieObject.setNavigationCommands(NavigationCommandUtils.playPlaylist(menuPlaylistNumber));

			launchGenerator.comment("Title menu");
			launchGenerator.createTitleMenu(menuDescriptorFile.toPath(), discPath, baseDir);
		} else {
			// No menu: jump directly to title 1
			topMenuMovieObject.setNavigationCommands(NavigationCommandUtils.jumpTitle(1));
			firstPlayMovieObject.setNavigationCommands(NavigationCommandUtils.jumpTitle(1));
		}

		movieObjects.add(topMenuMovieObject);
		movieObjects.add(firstPlayMovieObject);

		TitleEntry topMenuTitle = new TitleEntry();
		topMenuTitle.setObjectType(1);// HDMV
		topMenuTitle.setHdmvObjectId(nbTitles);
		topMenuTitle.setPlaybackType(1);
		index.setTopMenuTitle(topMenuTitle);
		TitleEntry firstPlayTitle = new TitleEntry();
		firstPlayTitle.setObjectType(1);// HDMV
		firstPlayTitle.setHdmvObjectId(nbTitles + 1);
		firstPlayTitle.setPlaybackType(1);
		index.setFirstPlayTitle(firstPlayTitle);

		index.setTitles(titles);
		File indexFile = descriptorsDir.resolve(INDEX_JSON).toFile();
		mapper.writeValue(indexFile, index);
		MovieObjects movies = new MovieObjects();
		movies.setMovieObjects(movieObjects);
		File moviesFile = descriptorsDir.resolve(MOVIE_OBJECT_JSON).toFile();
		mapper.writeValue(moviesFile, movies);

		launchGenerator.comment("Generate BDMV index and MovieObject");
		launchGenerator.indexWrite(indexFile.toPath(), bdmv);
		launchGenerator.mobjWrite(moviesFile.toPath(), bdmv);
		launchGenerator.message("Done. Check " + discPath + " for output.");

		Path scriptPath = launchGenerator.write(outputDir);
		log.info("Wrote orchestration script to {}", scriptPath);
	}

	// ── Title Menu Descriptor Builder ───────────────────────────────────────

	private TitleMenuDescriptor buildTitleMenuDescriptor(DiscDescriptor disc, TitleMenuConfig menuConfig) {
		TitleMenuDescriptor descriptor = new TitleMenuDescriptor();

		descriptor.setBackgroundMedia(menuConfig.getBackgroundSource());

		LayoutConfig layout = new LayoutConfig();
		layout.setType(menuConfig.getLayoutType());
		layout.setBoundingBox(menuConfig.getBoundingBox());

		// Cascade title menu style: titleMenuConfig.style merged over disc.style
		TextStyle resolvedTitleMenuStyle = resolveTitleMenuStyle(disc, menuConfig);
		if (resolvedTitleMenuStyle != null) {
			layout.setTitleStyle(resolvedTitleMenuStyle);
		}

		descriptor.setLayout(layout);

		List<org.brts.lowlevel.titlemenu.descriptor.TitleEntry> menuTitles = new ArrayList<>();
		for (TitleDescriptor title : disc.getTitles()) {
			org.brts.lowlevel.titlemenu.descriptor.TitleEntry entry = new org.brts.lowlevel.titlemenu.descriptor.TitleEntry();
			entry.setTitleNumber(title.getTitleId());
			entry.setDisplayName(resolveDisplayName(title));
			entry.setSourceMediaPath(title.getSourceMkv());
			menuTitles.add(entry);
		}
		descriptor.setTitles(menuTitles);

		descriptor.setOutputBackgroundName(menuConfig.getOutputBackgroundName());
		descriptor.setOutputMenuName(menuConfig.getOutputMenuName());
		descriptor.setOutputPlaylistName(menuConfig.getOutputPlaylistName());
		descriptor.setBackgroundLoopCount(menuConfig.getBackgroundLoopCount());

		descriptor.setAudioItems(menuConfig.getAudioItems().stream().map(item -> {
			org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem menuItem = new org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem();
			menuItem.setDescription(item.getDescription());
			menuItem.setStreamNumber(item.getStreamNumber());
			menuItem.setStyle(item.getStyle());
			return menuItem;
		}).toList());
		descriptor.setSubtitleItems(menuConfig.getSubtitleItems().stream().map(item -> {
			org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem menuItem = new org.brts.lowlevel.titlemenu.descriptor.StreamMenuItem();
			menuItem.setDescription(item.getDescription());
			menuItem.setStreamNumber(item.getStreamNumber());
			menuItem.setStyle(item.getStyle());
			return menuItem;
		}).toList());

		return descriptor;
	}

	private String resolveDisplayName(TitleDescriptor title) {
		if (title.getDisplayName() != null && !title.getDisplayName().isBlank()) {
			return title.getDisplayName();
		}
		// Derive from MKV filename: strip path and extension
		String path = title.getSourceMkv();
		int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
		String filename = lastSep >= 0 ? path.substring(lastSep + 1) : path;
		int dotIdx = filename.lastIndexOf('.');
		return dotIdx > 0 ? filename.substring(0, dotIdx) : filename;
	}

	private Path resolveBaseDir(TitleMenuConfig menuConfig, Path fallback) {
		if (menuConfig.getBaseDir() != null) {
			return Paths.get(menuConfig.getBaseDir()).toAbsolutePath();
		}
		BackgroundSource bg = menuConfig.getBackgroundSource();
		if (bg != null) {
			if (bg.isVideo()) {
				Path parent = Paths.get(bg.getVideoPath()).toAbsolutePath().getParent();
				return parent != null ? parent : fallback;
			}
			if (bg.isImage()) {
				Path parent = Paths.get(bg.getImagePath()).toAbsolutePath().getParent();
				return parent != null ? parent : fallback;
			}
		}
		return fallback;
	}

	// ── Style Resolution ────────────────────────────────────────────────────

	/**
	 * Resolves the effective popup menu style by merging disc-wide popupMenu style, popupStyle, and global disc style.
	 * Returns {@code null} if none is set (letting the low-level apply brts.conf defaults).
	 */
	private TextStyle resolvePopupStyle(DiscDescriptor disc) {
		TextStyle effective = disc.getStyle();
		if (disc.getPopupStyle() != null) {
			effective = effective != null ? disc.getPopupStyle().mergeOver(effective) : disc.getPopupStyle();
		}
		if (disc.getPopupMenu() != null && disc.getPopupMenu().getStyle() != null) {
			effective = effective != null ? disc.getPopupMenu().getStyle().mergeOver(effective)
					: disc.getPopupMenu().getStyle();
		}
		return effective; // may be null
	}

	/**
	 * Resolves the effective title menu style by merging titleMenuConfig.style over the global disc style. Returns
	 * {@code null} if neither is set (letting the low-level apply brts.conf defaults).
	 */
	private TextStyle resolveTitleMenuStyle(DiscDescriptor disc, TitleMenuConfig menuConfig) {
		TextStyle global = disc.getStyle();
		TextStyle menuStyle = menuConfig.getStyle();
		if (menuStyle != null && global != null) {
			return menuStyle.mergeOver(global);
		}
		if (menuStyle != null) {
			return menuStyle;
		}
		return global; // may be null
	}

	// ── Popup Menu Toggle Resolution ────────────────────────────────────────

	/**
	 * Resolves whether a popup menu should be generated for this title and returns the popup clip name, or null if no
	 * popup is needed.
	 */
	private String resolvePopupMenuClipName(TitleDescriptor title, SourceMediaInfo mediaInfo, String titleClipName) {
		PopupMenuMode mode = title.getPopupMenu() != null ? title.getPopupMenu() : PopupMenuMode.AUTO;

		boolean generate;
		switch (mode) {
		case TRUE:
			generate = true;
			break;
		case FALSE:
			generate = false;
			break;
		case AUTO:
		default:
			generate = shouldAutoGeneratePopup(mediaInfo, title);
			break;
		}

		if (!generate) {
			return null;
		}

		// Allocate popup clip name: title clip number + 500 offset (e.g. "00001" → "00501")
		int titleNum = Integer.parseInt(titleClipName);
		return String.format("%05d", titleNum + 500);
	}

	private boolean shouldAutoGeneratePopup(SourceMediaInfo mediaInfo, TitleDescriptor title) {
		return countAudioTracks(mediaInfo, title) > 1 || countSubtitleTracks(mediaInfo, title) > 1;
	}

	/**
	 * Counts audio tracks that survive this title's language filter (same order used by {@link SimpleTitleBuilder}).
	 */
	private int countAudioTracks(SourceMediaInfo mediaInfo, TitleDescriptor title) {
		if (mediaInfo == null || mediaInfo.getTracks() == null) {
			return 0;
		}
		return (int) mediaInfo.getTracks().stream().filter(t -> t.getCodingType() != null)
				.filter(t -> t.getCodingType().isAudio()).filter(t -> title.getAudioLanguages() == null
						|| title.getAudioLanguages().isEmpty() || title.getAudioLanguages().contains(t.getLanguage()))
				.count();
	}

	/** Counts PG/subtitle tracks that survive this title's language filter. */
	private int countSubtitleTracks(SourceMediaInfo mediaInfo, TitleDescriptor title) {
		if (mediaInfo == null || mediaInfo.getTracks() == null) {
			return 0;
		}
		return (int) mediaInfo.getTracks().stream().filter(t -> t.getCodingType() != null).filter(
				t -> t.getCodingType().isSubtitle() || t.getCodingType() == StreamCodingType.PRESENTATION_GRAPHICS)
				.filter(t -> title.getSubtitleLanguages() == null || title.getSubtitleLanguages().isEmpty()
						|| title.getSubtitleLanguages().contains(t.getLanguage()))
				.count();
	}

}
