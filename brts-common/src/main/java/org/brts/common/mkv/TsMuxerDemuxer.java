package org.brts.common.mkv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.ProcessUtils;
import org.brts.common.utils.TsMuxerUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * MKV demuxer implementation backed by the external tsMuxeR CLI.
 */
@Slf4j
public class TsMuxerDemuxer implements EsDemuxer {

	private static final String TSMUXER_TIMEOUT_MS = "brts.mkv.tsmuxer.timeout.ms";

	private static final long DEFAULT_TIMEOUT_MS = 120_000L;

	@Override
	public Map<Integer, Path> demux(Path sourcePath, Path outputDir) throws IOException {
		return demux(sourcePath, outputDir, null);
	}

	@Override
	public Map<Integer, Path> demux(Path sourcePath, Path outputDir, Set<Integer> trackFilter) throws IOException {
		Files.createDirectories(outputDir);

		SourceMediaInfo info = new MkvSourceMediaParser().parse(sourcePath);
		Map<Integer, SourceMediaInfo.SourceTrack> tracksByNumber = info.getTracks().stream()
				.filter(track -> trackFilter == null || trackFilter.contains(track.getTrackNumber())).collect(Collectors
						.toMap(SourceMediaInfo.SourceTrack::getTrackNumber, t -> t, (a, b) -> a, LinkedHashMap::new));

		if (tracksByNumber.isEmpty()) {
			return Map.of();
		}

		Path workDir = Files.createTempDirectory(outputDir, ".tsmuxer_demux_");
		Map<Integer, Path> demuxed = new LinkedHashMap<>();
		try {
			String tsmuxerBinary = TsMuxerUtils.resolveTsMuxeRBinary();
			long timeoutMs = resolveTimeoutMs();

			for (SourceMediaInfo.SourceTrack track : tracksByNumber.values()) {
				int trackNo = track.getTrackNumber();
				Path perTrackDir = Files.createDirectories(workDir.resolve("track_" + trackNo));
				Path rawOutput = demuxWithTsMuxer(tsmuxerBinary, sourcePath, track, perTrackDir, timeoutMs);

				String ext = extensionForTrack(track);
				Path normalized = outputDir.resolve("track_" + trackNo + "." + ext);
				Files.move(rawOutput, normalized);
				demuxed.put(trackNo, normalized);
			}

			// Keep parity for text subtitle tracks if the tsMuxeR output extension differs.
			Set<Integer> textTracks = tracksByNumber.values().stream()
					.filter(t -> t.getCodingType() == StreamCodingType.TEXT_SUBTITLE)
					.map(SourceMediaInfo.SourceTrack::getTrackNumber).collect(Collectors.toSet());
			if (!textTracks.isEmpty()) {
				Map<Integer, Path> textFromFallback = new MkvDemuxer().demux(sourcePath,
						workDir.resolve("text_fallback"), textTracks);
				for (Map.Entry<Integer, Path> entry : textFromFallback.entrySet()) {
					if (!demuxed.containsKey(entry.getKey())) {
						SourceMediaInfo.SourceTrack track = tracksByNumber.get(entry.getKey());
						Path normalized = outputDir.resolve("track_" + entry.getKey() + "." + extensionForTrack(track));
						Files.move(entry.getValue(), normalized);
						demuxed.put(entry.getKey(), normalized);
					}
				}
			}

			// Preserve input order by track number map insertion order.
			Map<Integer, Path> ordered = new LinkedHashMap<>();
			for (Integer trackNo : tracksByNumber.keySet()) {
				Path path = demuxed.get(trackNo);
				if (path != null) {
					ordered.put(trackNo, path);
				}
			}
			return ordered;
		} finally {
			deleteRecursively(workDir);
		}
	}

	private static Path demuxWithTsMuxer(String tsmuxerBinary, Path sourcePath, SourceMediaInfo.SourceTrack track,
			Path perTrackDir, long timeoutMs) throws IOException {
		Path metaFile = perTrackDir.resolve("demux.meta");
		String meta = "MUXOPT --demux\n" + TsMuxerUtils.tsmuxerCodecFor(track) + ", \"" + sourcePath.toAbsolutePath()
				+ "\", track=" + track.getTrackNumber();
		Files.writeString(metaFile, meta);

		Process process = new ProcessBuilder(tsmuxerBinary, metaFile.toString(), perTrackDir.toString()).start();
		ProcessUtils.StringStreamGobbler outputGobbler = new ProcessUtils.StringStreamGobbler(process.getInputStream());
		ProcessUtils.StringStreamGobbler errorGobbler = new ProcessUtils.StringStreamGobbler(process.getErrorStream());
		outputGobbler.start();
		errorGobbler.start();

		try {
			boolean finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new IOException("tsMuxeR demux timeout for track " + track.getTrackNumber());
			}
			int exit = process.exitValue();
			outputGobbler.join(2_000L);
			errorGobbler.join(2_000L);

			if (exit != 0) {
				throw new IOException("tsMuxeR demux failed for track " + track.getTrackNumber() + " (exit=" + exit
						+ "): " + errorGobbler.getOutput());
			}

			Path output = resolveDemuxedOutput(perTrackDir, metaFile);
			if (output == null) {
				throw new IOException("tsMuxeR demux produced no output for track " + track.getTrackNumber() + ".\n"
						+ "stdout: " + outputGobbler.getOutput() + "\nstderr: " + errorGobbler.getOutput());
			}
			return output;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("tsMuxeR demux interrupted for track " + track.getTrackNumber(), e);
		}
	}

	private static Path resolveDemuxedOutput(Path perTrackDir, Path metaFile) throws IOException {
		try (Stream<Path> stream = Files.walk(perTrackDir)) {
			List<Path> candidates = stream.filter(Files::isRegularFile).filter(p -> !p.equals(metaFile))
					.filter(p -> !p.getFileName().toString().endsWith(".txt"))
					.filter(p -> !p.getFileName().toString().endsWith(".meta")).collect(Collectors.toList());
			if (candidates.isEmpty()) {
				return null;
			}
			candidates.sort(Comparator.comparingLong(TsMuxerDemuxer::fileSize).reversed());
			return candidates.get(0);
		}
	}

	private static long fileSize(Path path) {
		try {
			return Files.size(path);
		} catch (IOException e) {
			return -1L;
		}
	}

	private static String extensionForTrack(SourceMediaInfo.SourceTrack track) {
		if (track == null || track.getCodingType() == null) {
			return "bin";
		}
		if (track.getCodingType() == StreamCodingType.TEXT_SUBTITLE && track.getSubtitleFormat() != null) {
			String format = track.getSubtitleFormat().toLowerCase();
			if ("ass".equals(format) || "ssa".equals(format) || "srt".equals(format)) {
				return format;
			}
		}
		Map<StreamCodingType, String> byType = new HashMap<>();
		byType.put(StreamCodingType.H264_AVC, "h264");
		byType.put(StreamCodingType.H265_HEVC, "h265");
		byType.put(StreamCodingType.MPEG2_VIDEO, "m2v");
		byType.put(StreamCodingType.VC1, "vc1");
		byType.put(StreamCodingType.DOLBY_AC3, "ac3");
		byType.put(StreamCodingType.DOLBY_AC3_PLUS, "eac3");
		byType.put(StreamCodingType.DOLBY_TRUEHD, "thd");
		byType.put(StreamCodingType.DTS, "dts");
		byType.put(StreamCodingType.DTS_HD, "dtshd");
		byType.put(StreamCodingType.DTS_HD_MASTER_AUDIO, "dtsma");
		byType.put(StreamCodingType.LPCM, "lpcm");
		byType.put(StreamCodingType.PRESENTATION_GRAPHICS, "pgs");
		byType.put(StreamCodingType.TEXT_SUBTITLE, "srt");
		return byType.getOrDefault(track.getCodingType(), "bin");
	}

	private static long resolveTimeoutMs() {
		String value = BrtsFileConfig.getInstance().getProperty(TSMUXER_TIMEOUT_MS);
		if (value == null || value.isBlank()) {
			return DEFAULT_TIMEOUT_MS;
		}
		try {
			long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : DEFAULT_TIMEOUT_MS;
		} catch (NumberFormatException e) {
			log.warn("Invalid timeout '{}' for {}, using default {} ms", value, TSMUXER_TIMEOUT_MS, DEFAULT_TIMEOUT_MS);
			return DEFAULT_TIMEOUT_MS;
		}
	}

	private static void deleteRecursively(Path root) {
		if (root == null || !Files.exists(root)) {
			return;
		}
		try (Stream<Path> stream = Files.walk(root)) {
			stream.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException e) {
					log.debug("Cannot delete temporary file {}: {}", path, e.getMessage());
				}
			});
		} catch (IOException e) {
			log.debug("Cannot delete temporary directory {}: {}", root, e.getMessage());
		}
	}
}
