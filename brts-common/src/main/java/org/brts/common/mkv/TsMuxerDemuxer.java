package org.brts.common.mkv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.brts.common.model.StreamCodingType;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.FileUtils;
import org.brts.common.utils.ProcessUtils;
import org.brts.common.utils.TsMuxerUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * MKV demuxer implementation backed by the external tsMuxeR CLI. This class does not work as is.
 *
 * There is an unsolved issue with tsMuxeR where it will fail to demux text subtitles because it cannot find the font
 * specified in the demux meta file. This is a known bug: it seems it misinterprets the font name/family. It works only
 * when font family == font name == font file name.
 *
 * https://github.com/justdan96/tsMuxer/issues/459
 *
 * https://github.com/justdan96/tsMuxer/issues/170
 *
 * Another (related) issue is that tsMuxer does not actually demux text subtitles: it just converts them to PGS
 * subtitles, which is not exactly what we want here.
 *
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
		try {
			String tsmuxerBinary = TsMuxerUtils.resolveTsMuxeRBinary();
			long timeoutMs = resolveTimeoutMs();
			SubtitleMeta subtitleMeta = resolveSubtitleMeta(tracksByNumber.values());

			Path rawOutputDir = Files.createDirectories(workDir.resolve("raw_demux"));
			Path metaFile = workDir.resolve("demux.meta");
			Files.writeString(metaFile, buildDemuxMeta(sourcePath, tracksByNumber.values(), subtitleMeta));

			runTsMuxerDemux(tsmuxerBinary, metaFile, rawOutputDir, timeoutMs);
			Map<Integer, Path> mappedRaw = mapRawOutputsToTracks(rawOutputDir, metaFile, tracksByNumber);

			Map<Integer, Path> demuxed = new LinkedHashMap<>();
			for (Map.Entry<Integer, SourceMediaInfo.SourceTrack> entry : tracksByNumber.entrySet()) {
				Integer trackNo = entry.getKey();
				SourceMediaInfo.SourceTrack track = entry.getValue();
				Path rawOutput = mappedRaw.get(trackNo);
				if (rawOutput == null) {
					throw new IOException("tsMuxeR demux produced no output for requested track " + trackNo);
				}
				Path normalized = outputDir.resolve("track_" + trackNo + "." + extensionForTrack(track));
				Files.move(rawOutput, normalized, StandardCopyOption.REPLACE_EXISTING);
				demuxed.put(trackNo, normalized);
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
			FileUtils.deleteDir(workDir);
		}
	}

	private static void runTsMuxerDemux(String tsmuxerBinary, Path metaFile, Path outputDir, long timeoutMs)
			throws IOException {
		Process process = new ProcessBuilder(tsmuxerBinary, metaFile.toString(), outputDir.toString()).start();
		ProcessUtils.StringStreamGobbler outputGobbler = new ProcessUtils.StringStreamGobbler(process.getInputStream());
		ProcessUtils.StringStreamGobbler errorGobbler = new ProcessUtils.StringStreamGobbler(process.getErrorStream());
		outputGobbler.start();
		errorGobbler.start();

		try {
			boolean finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
			if (!finished) {
				process.destroyForcibly();
				throw new IOException("tsMuxeR demux timeout");
			}
			int exit = process.exitValue();
			outputGobbler.join(2_000L);
			errorGobbler.join(2_000L);

			if (exit != 0) {
				throw new IOException("tsMuxeR demux failed (exit=" + exit + ").\nstdout: " + outputGobbler.getOutput()
						+ "\nstderr: " + errorGobbler.getOutput());
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IOException("tsMuxeR demux interrupted", e);
		}
	}

	private static List<Path> listDemuxedOutputs(Path outputDir, Path metaFile) throws IOException {
		try (Stream<Path> stream = Files.walk(outputDir)) {
			List<Path> candidates = stream.filter(Files::isRegularFile).filter(p -> !p.equals(metaFile))
					.filter(p -> !p.getFileName().toString().endsWith(".txt"))
					.filter(p -> !p.getFileName().toString().endsWith(".meta")).collect(Collectors.toList());
			candidates.sort(Comparator.comparing(Path::toString));
			return candidates;
		}
	}

	private static Map<Integer, Path> mapRawOutputsToTracks(Path rawOutputDir, Path metaFile,
			Map<Integer, SourceMediaInfo.SourceTrack> tracksByNumber) throws IOException {
		List<Path> available = new ArrayList<>(listDemuxedOutputs(rawOutputDir, metaFile));
		if (available.isEmpty()) {
			throw new IOException(
					"tsMuxeR demux produced no output files for requested tracks " + tracksByNumber.keySet());
		}

		Map<Integer, Path> mapped = new LinkedHashMap<>();
		Set<Path> used = new HashSet<>();

		for (Map.Entry<Integer, SourceMediaInfo.SourceTrack> entry : tracksByNumber.entrySet()) {
			int trackNo = entry.getKey();
			Path match = available.stream().filter(p -> !used.contains(p))
					.filter(p -> fileNameContainsTrackNumberToken(p.getFileName().toString(), trackNo)).findFirst()
					.orElse(null);
			if (match != null) {
				mapped.put(trackNo, match);
				used.add(match);
			}
		}

		Map<String, List<Integer>> tracksByExt = new LinkedHashMap<>();
		for (Map.Entry<Integer, SourceMediaInfo.SourceTrack> entry : tracksByNumber.entrySet()) {
			if (mapped.containsKey(entry.getKey())) {
				continue;
			}
			String ext = extensionForTrack(entry.getValue()).toLowerCase(Locale.ROOT);
			tracksByExt.computeIfAbsent(ext, k -> new ArrayList<>()).add(entry.getKey());
		}

		for (Map.Entry<String, List<Integer>> entry : tracksByExt.entrySet()) {
			String ext = entry.getKey();
			List<Integer> trackNos = entry.getValue();
			List<Path> candidates = available.stream().filter(p -> !used.contains(p))
					.filter(p -> extensionOf(p).equalsIgnoreCase(ext)).sorted(Comparator.comparing(Path::toString))
					.collect(Collectors.toList());

			if (candidates.size() != trackNos.size()) {
				throw new IOException("Cannot map tsMuxeR outputs for extension '" + ext + "': expected "
						+ trackNos.size() + " file(s) for tracks " + trackNos + " but found " + candidates.size()
						+ " candidate(s): " + candidates.stream().map(Path::getFileName).collect(Collectors.toList()));
			}

			for (int i = 0; i < trackNos.size(); i++) {
				Path candidate = candidates.get(i);
				mapped.put(trackNos.get(i), candidate);
				used.add(candidate);
			}
		}

		if (mapped.size() != tracksByNumber.size()) {
			Set<Integer> missing = new HashSet<>(tracksByNumber.keySet());
			missing.removeAll(mapped.keySet());
			throw new IOException("Cannot map demux output(s) for track(s): " + missing + ". Available files: "
					+ available.stream().map(Path::getFileName).collect(Collectors.toList()));
		}

		return mapped;
	}

	private static String buildDemuxMeta(Path sourcePath, java.util.Collection<SourceMediaInfo.SourceTrack> tracks,
			SubtitleMeta subtitleMeta) {
		StringBuilder meta = new StringBuilder(
				"MUXOPT --no-pcr-on-video-pid --new-audio-pes --demux --vbr --vbv-len=500");
		for (SourceMediaInfo.SourceTrack track : tracks) {
			meta.append('\n');
			meta.append(buildMetaTrackLine(sourcePath, track, subtitleMeta));
		}
		String metaContent = meta.toString();
		log.debug("tsMuxeR demux meta file content:\n{}", metaContent);
		return metaContent;
	}

	private static String buildMetaTrackLine(Path sourcePath, SourceMediaInfo.SourceTrack track,
			SubtitleMeta subtitleMeta) {
		StringBuilder line = new StringBuilder();
		line.append(TsMuxerUtils.tsmuxerCodecFor(track));
		line.append(", \"").append(sourcePath.toAbsolutePath()).append("\"");

		if (track.getCodingType() == StreamCodingType.TEXT_SUBTITLE) {
			if (subtitleMeta == null) {
				throw new IllegalStateException("subtitle metadata is required for text subtitle demux");
			}
			line.append(", font-name=\"").append(subtitleMeta.fontName).append("\"");
			line.append(", font-size=").append(subtitleMeta.fontSize);
			line.append(", font-color=").append(subtitleMeta.fontColorHex);
			line.append(", bottom-offset=").append(subtitleMeta.bottomOffset);
			line.append(", font-border=").append(subtitleMeta.fontBorder);
			line.append(", text-align=").append(subtitleMeta.textAlign);
			line.append(", video-width=").append(subtitleMeta.videoWidth);
			line.append(", video-height=").append(subtitleMeta.videoHeight);
			line.append(", fps=").append(formatFps(subtitleMeta.fps));
		}

		line.append(", track=").append(track.getTrackNumber());
		line.append(", lang=")
				.append(track.getLanguage() == null || track.getLanguage().isBlank() ? "und" : track.getLanguage());
		return line.toString();
	}

	private static SubtitleMeta resolveSubtitleMeta(Collection<SourceMediaInfo.SourceTrack> tracks) throws IOException {
		boolean hasTextSubtitle = tracks.stream().anyMatch(t -> t.getCodingType() == StreamCodingType.TEXT_SUBTITLE);
		if (!hasTextSubtitle) {
			return null;
		}

		SourceMediaInfo.SourceTrack videoTrack = tracks.stream()
				.filter(t -> t.getCodingType() != null && t.getCodingType().isVideo()).findFirst().orElse(null);
		if (videoTrack == null || videoTrack.getWidthPixels() == null || videoTrack.getHeightPixels() == null
				|| videoTrack.getFrameRateFps() == null || videoTrack.getFrameRateFps() <= 0) {
			throw new IOException(
					"Cannot build mandatory tsMuxeR text subtitle metadata: missing video width/height/fps from first selected video track");
		}

		BrtsFileConfig config = BrtsFileConfig.getInstance();
		String fontName = config.propertyOrDefault("pgs.render.fontName", "SansSerif");
		int fontSize = config.parseIntProperty("pgs.render.fontSize", 48);
		String fontColorHex = config.parseColorProperty("pgs.render.fontColor", "0xffffffff");
		int fontBorder = Math.max(1, Math.round(config.parseFloatProperty("pgs.render.outlineWidth", 3.0f)));
		double verticalRatio = config.parseDoubleProperty("pgs.render.verticalPositionRatio", 0.90d);
		int bottomOffset = deriveBottomOffset(videoTrack.getHeightPixels(), verticalRatio);

		return new SubtitleMeta(videoTrack.getWidthPixels(), videoTrack.getHeightPixels(), videoTrack.getFrameRateFps(),
				fontName, fontSize, fontColorHex, fontBorder, bottomOffset, "center");
	}

	private static int deriveBottomOffset(int videoHeight, double verticalRatio) {
		double clamped = Math.max(0.0d, Math.min(1.0d, verticalRatio));
		int offset = (int) Math.round((1.0d - clamped) * videoHeight);
		return Math.max(0, Math.min(videoHeight - 1, offset));
	}

	private static String formatFps(double fps) {
		if (Math.abs(fps - Math.rint(fps)) < 0.0001d) {
			return Integer.toString((int) Math.rint(fps));
		}
		String formatted = String.format(Locale.ROOT, "%.3f", fps);
		return formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	private static boolean fileNameContainsTrackNumberToken(String fileName, int trackNo) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		return lower.matches(".*(^|[^0-9])track[_-]?" + trackNo + "([^0-9]|$).*");
	}

	private static String extensionOf(Path path) {
		String name = path.getFileName().toString();
		int idx = name.lastIndexOf('.');
		return idx >= 0 ? name.substring(idx + 1) : "";
	}

	private record SubtitleMeta(int videoWidth, int videoHeight, double fps, String fontName, int fontSize,
			String fontColorHex, int fontBorder, int bottomOffset, String textAlign) {
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
}
