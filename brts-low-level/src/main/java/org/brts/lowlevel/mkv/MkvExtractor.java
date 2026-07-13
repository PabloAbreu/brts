package org.brts.lowlevel.mkv;

import org.brts.common.mkv.EsDemuxer;
import org.brts.common.mkv.MkvDemuxerFactory;
import org.brts.common.mkv.MkvSourceMediaParser;
import org.brts.common.mkv.SourceMediaInfo;
import org.brts.common.model.StreamCodingType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extracts elementary streams from an MKV file with optional track selectors.
 */
public class MkvExtractor {

	@lombok.Data
	public static class Config {

		private final Path inputFile;

		private final Path outputDir;

		private Set<Integer> trackNumbers;

		private boolean videoOnly;

		private boolean audioOnly;

		private boolean subtitlesOnly;

		public Config(Path inputFile, Path outputDir) {
			this.inputFile = inputFile;
			this.outputDir = outputDir;
		}

	}

	public record Result(SourceMediaInfo mediaInfo, Set<Integer> selectedTrackNumbers,
			Map<Integer, Path> extractedFiles) {
	}

	private final MkvSourceMediaParser parser;

	private final EsDemuxer demuxer;

	public MkvExtractor() {
		this(new MkvSourceMediaParser(), MkvDemuxerFactory.get());
	}

	MkvExtractor(MkvSourceMediaParser parser, EsDemuxer demuxer) {
		this.parser = Objects.requireNonNull(parser, "parser is required");
		this.demuxer = Objects.requireNonNull(demuxer, "demuxer is required");
	}

	public Result extract(Config config) throws IOException {
		validateConfig(config);

		Path inputFile = config.getInputFile();
		Path outputDir = config.getOutputDir();

		Files.createDirectories(outputDir);

		SourceMediaInfo mediaInfo = parser.parse(inputFile);
		Set<Integer> selectedTrackNumbers = resolveTrackSelection(mediaInfo, config);

		Map<Integer, Path> extracted = demuxer.demux(inputFile, outputDir, selectedTrackNumbers);
		Map<Integer, Path> orderedExtracted = extracted.entrySet().stream().sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

		return new Result(mediaInfo, Collections.unmodifiableSet(selectedTrackNumbers),
				Collections.unmodifiableMap(orderedExtracted));
	}

	private void validateConfig(Config config) {
		if (config == null) {
			throw new IllegalArgumentException("config is required");
		}

		if (config.getInputFile() == null) {
			throw new IllegalArgumentException("--input is required");
		}
		if (!Files.isRegularFile(config.getInputFile())) {
			throw new IllegalArgumentException("Input MKV file does not exist: " + config.getInputFile());
		}
		if (!Files.isReadable(config.getInputFile())) {
			throw new IllegalArgumentException("Input MKV file is not readable: " + config.getInputFile());
		}

		if (config.getOutputDir() == null) {
			throw new IllegalArgumentException("--output is required");
		}
		if (Files.exists(config.getOutputDir()) && !Files.isDirectory(config.getOutputDir())) {
			throw new IllegalArgumentException("Output path is not a directory: " + config.getOutputDir());
		}
	}

	Set<Integer> resolveTrackSelection(SourceMediaInfo mediaInfo, Config config) {
		if (mediaInfo == null || mediaInfo.getTracks() == null || mediaInfo.getTracks().isEmpty()) {
			throw new IllegalArgumentException("No tracks found in source MKV");
		}

		Set<Integer> available = mediaInfo.getTracks().stream().map(SourceMediaInfo.SourceTrack::getTrackNumber)
				.collect(Collectors.toCollection(LinkedHashSet::new));

		Set<Integer> selected = config.getTrackNumbers() == null ? new LinkedHashSet<>(available)
				: new LinkedHashSet<>(config.getTrackNumbers());

		Set<Integer> unknown = selected.stream().filter(n -> !available.contains(n))
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (!unknown.isEmpty()) {
			throw new IllegalArgumentException("Selected track number(s) not found in source MKV: " + unknown);
		}

		boolean hasTypeFilter = config.isVideoOnly() || config.isAudioOnly() || config.isSubtitlesOnly();
		if (hasTypeFilter) {
			Set<Integer> baseSelected = selected;
			selected = mediaInfo.getTracks().stream().filter(t -> baseSelected.contains(t.getTrackNumber()))
					.filter(t -> matchesTypeFilter(t.getCodingType(), config))
					.map(SourceMediaInfo.SourceTrack::getTrackNumber)
					.collect(Collectors.toCollection(LinkedHashSet::new));
		}

		if (selected.isEmpty()) {
			throw new IllegalArgumentException("No tracks selected for extraction after applying filters");
		}

		return selected;
	}

	private boolean matchesTypeFilter(StreamCodingType codingType, Config config) {
		if (codingType == null) {
			return false;
		}
		if (config.isVideoOnly() && codingType.isVideo()) {
			return true;
		}
		if (config.isAudioOnly() && codingType.isAudio()) {
			return true;
		}
		return config.isSubtitlesOnly() && codingType.isSubtitle();
	}

}