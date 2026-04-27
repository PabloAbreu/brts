package org.brts.middle.orchestration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.bdmv.NavigationCommandMnemonic;
import org.brts.lowlevel.bdmv.ParsedNavigationCommand;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.IndexBdmv.TitleEntry;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.model.bdmv.MovieObjects.MovieObject;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.brts.middle.api.SimpleTitleBuilder;
import org.brts.middle.descriptor.DiscDescriptor;
import org.brts.middle.descriptor.TitleDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Middle-level orchestrator: processes a {@link DiscDescriptor}, builds low-level
 * descriptors for each title, and writes them to an output directory along with an
 * orchestration script.
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
 */
public class MiddleLevelOrchestrator {

	private static final String MOVIE_OBJECT_JSON = "MovieObject.json";

	private static final String INDEX_JSON = "index.json";

	private static final Logger log = LoggerFactory.getLogger(MiddleLevelOrchestrator.class);

	private final SimpleTitleBuilder titleBuilder;

	private final ObjectMapper mapper = JsonMapperFactory.get();

	public MiddleLevelOrchestrator(SimpleTitleBuilder titleBuilder) {
		this.titleBuilder = titleBuilder;
	}

	/**
	 * Processes the disc descriptor and writes low-level descriptors + orchestration
	 * script.
	 * @param disc the middle-level disc description
	 * @param outputDir directory where descriptors and the script will be written
	 */
	public void orchestrate(DiscDescriptor disc, Path outputDir) throws IOException {
		Path descriptorsDir = outputDir.resolve("descriptors");
		Path mediaFolder = Paths.get(disc.getOutputFolder()).toAbsolutePath().normalize();
		Path discPath = mediaFolder.resolve(disc.getDiscName());// FIXME sanitize
																// discName, maybe
																// annotations on
																// descriptors

		Path bdmv = discPath.resolve("BDMV");
		Files.createDirectories(descriptorsDir);

		List<String> scriptLines = new ArrayList<>();
		scriptLines.add("#!/usr/bin/env bash");
		scriptLines.add("# Auto-generated middle-level orchestration script");
		scriptLines.add("# Run each step in order to produce the low-level Blu-ray files");
		scriptLines.add(
				"# 'java' must be in your PATH, and the BRTS CLI JAR must be at $HOME/.m2/repository/org/brts/brts-cli/1.0.0-SNAPSHOT/brts-cli-1.0.0-SNAPSHOT.jar");
		scriptLines.add("set -euo pipefail");
		scriptLines.add("");
		scriptLines.add(
				"BRTS_CLI=\"java -jar $HOME/.m2/repository/org/brts/brts-cli/1.0.0-SNAPSHOT/brts-cli-1.0.0-SNAPSHOT.jar\"");
		scriptLines.add("");

		IndexBdmv index = new IndexBdmv();
		index.setContentProviderName("BRTS authoring");
		index.setDiscApplicationType(1);
		index.setVersion("0200");
		List<TitleEntry> titles = new ArrayList<>();
		int movieObjectIndex = 0;
		List<MovieObject> movieObjects = new ArrayList<>();
		int nbTitles = disc.getTitles().size();
		int lastTitleId = -1;
		for (TitleDescriptor title : disc.getTitles()) {
			SimpleTitleBuilder.TitleBuildResult result = titleBuilder.build(title);

			String clipName = result.clipDescriptor().getClipName();
			// Emit script lines
			scriptLines.add("# Title " + title.getTitleId() + " — " + title.getSourceMkv());
			scriptLines.add("$BRTS_CLI low mkv-to-playlist --clip-name " + clipName + " --input " + title.getSourceMkv()
					+ " --output " + bdmv);
			scriptLines.add("");
			TitleEntry entry = new TitleEntry();
			entry.setObjectType(1);// HDMV
			entry.setHdmvObjectId(movieObjectIndex++);
			titles.add(entry);
			MovieObject movieObject = new MovieObject();
			movieObject.setNavigationCommands(List.of(NavigationCommand.fromParsed(ParsedNavigationCommand
				.compile(NavigationCommandMnemonic.PLAY_PL, title.getTitleId(), true, 0, false))));
			movieObjects.add(movieObject);
			lastTitleId = title.getTitleId();
		}

		MovieObject movieObject = new MovieObject();
		movieObject.setNavigationCommands(List.of(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.PLAY_PL, lastTitleId, true, 0, false))));
		movieObjects.add(movieObject);

		movieObject = new MovieObject();
		movieObject.setNavigationCommands(List.of(NavigationCommand.fromParsed(
				ParsedNavigationCommand.compile(NavigationCommandMnemonic.PLAY_PL, lastTitleId, true, 0, false))));
		movieObjects.add(movieObject);

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

		// Emit index/movie-object generation step (placeholders)
		scriptLines.add("# Generate BDMV index and MovieObject");
		scriptLines.add("$BRTS_CLI low index-write --input " + indexFile.getAbsolutePath() + " --output " + bdmv);
		scriptLines.add("$BRTS_CLI low mobj-write --input " + moviesFile.getAbsolutePath() + " --output " + bdmv);
		scriptLines.add("");
		scriptLines.add("echo \"Done. Check " + discPath + " for output.\"");

		// Write orchestration script
		Path scriptPath = outputDir.resolve("orchestrate.sh");
		Files.writeString(scriptPath, String.join("\n", scriptLines) + "\n");
		scriptPath.toFile().setExecutable(true);
		log.info("Wrote orchestration script to {}", scriptPath);
	}

}
