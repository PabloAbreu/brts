package org.brts.common.m2ts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.m2ts.model.M2tsChapter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.utils.BrtsFileConfig;
import org.brts.common.utils.FileUtils;
import org.brts.common.utils.ProcessUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TsMuxerM2tsClipWriter implements M2tsClipWriter {

	private static final String TSMUXER_BINARY = "tsmuxer.binary";

	private static String ptsToTimeString(long ptsTicks) {
		long milliseconds = ptsTicks / 90;
		long totalSeconds = milliseconds / 1000;
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		long remainingMillis = milliseconds % 1000;
		return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, remainingMillis);
	}

	@Override
	public void write(M2tsDescriptor descriptor, Path m2tsPath, Path clipPath) throws IOException {
		// create process to run tsMuxeR CLI
		// same params as in tsMuxerGUI
		final StringBuilder metaFilecontents = new StringBuilder(
				"MUXOPT --no-pcr-on-video-pid --new-audio-pes --blu-ray --vbr --vbv-len=500");
		List<M2tsChapter> chapters = descriptor.getChapters();
		if (chapters != null && !chapters.isEmpty()) {
			metaFilecontents.append(" --custom-chapters=");
			metaFilecontents.append(String.join(";",
					chapters.stream().map(chapter -> ptsToTimeString(chapter.getPtsTicks())).toList()));
		}
		// TODO manage timeshift
		descriptor.getStreams().forEach(stream -> {
			metaFilecontents
					.append(String.format("\n%s, \"%s\", lang=%s", translateCodecToTsMuxer(stream.getStreamTypeByte()),
							stream.getFile(), stream.getLanguage() != null ? stream.getLanguage() : "und"));
		});

		Path parent = m2tsPath.getParent();
		parent.toFile().mkdirs();
		Path workDir = Files.createTempDirectory(parent, ".mux_temp");
		Path muxOpts = Files.createTempFile(workDir, "meta_file", "_for_ts_muxer");
		Files.writeString(muxOpts, metaFilecontents);
		log.debug("Created tsMuxeR meta file at {}:\n{}", muxOpts, metaFilecontents);
		log.info("Running tsMuxeR '{}' to write M2TS clip to {} …", resolveTsMuxeRBinary(), workDir);
		ProcessBuilder builder = new ProcessBuilder(resolveTsMuxeRBinary(), muxOpts.toString(), workDir.toString());
		Process process = builder.start();
		log.debug("Started tsMuxeR process with PID {}", process.pid());
		ProcessUtils.StringStreamGobbler outputGobbler = new ProcessUtils.StringStreamGobbler(process.getInputStream());
		ProcessUtils.StringStreamGobbler errorGobbler = new ProcessUtils.StringStreamGobbler(process.getErrorStream());
		try {
			int result = process.waitFor();
			log.debug("tsMuxeR ended with exit code {}", result);
			String errors = errorGobbler.getOutput();
			if (!errors.isBlank()) {
				log.error("tsMuxeR error output:\n{}", errors);
			}
			String output = outputGobbler.getOutput();
			if (!output.isBlank())
				log.info("tsMuxeR output:\n{}", output);
			Path generatedM2ts = workDir.resolve("BDMV/STREAM/00000.m2ts");
			Path generatedClpi = workDir.resolve("BDMV/CLIPINF/00000.clpi");
			Files.move(generatedM2ts, m2tsPath);
			clipPath.getParent().toFile().mkdirs();
			Files.move(generatedClpi, clipPath);
		} catch (InterruptedException e) {
			log.error("tsMuxeR process was interrupted", e);
		} finally {
			// FileUtils.deleteDir(workDir);
		}
	}

	private static String translateCodecToTsMuxer(int streamTypeByte) {
		return switch (streamTypeByte) {
		case 0x1B -> "V_MPEG4/ISO/AVC"; // H.264
		case 0x24 -> "V_MPEG4/ISO/HEVC"; // H.265
		case 0x06 -> "A_AC3"; // AC-3
		case 0x81 -> "A_AC3"; // E-AC-3
		case 0x84 -> "A_AC3"; // E-AC-3
		case 0x90 -> "S_HDMV/PGS"; // subs
		default ->
			throw new IllegalArgumentException(String.format("Unsupported stream type byte: 0x%02X", streamTypeByte));
		};
	}

	static String resolveTsMuxeRBinary() {
		String error = "";
		String tsmuxer = BrtsFileConfig.getInstance().getProperty(TSMUXER_BINARY);
		if (tsmuxer == null) {
			error += "Property 'tsmuxer.binary' is not set. Please set it to the path of the tsMuxeR CLI binary.";
			tsmuxer = ProcessUtils.findFullPath("tsMuxeR");
		}
		if (tsmuxer == null) {
			error += " tsMuxeR binary not found in system PATH either.";
			throw new IllegalStateException(error);
		}
		File tsmuxerFile = new File(tsmuxer);
		if (!tsmuxerFile.exists() || !tsmuxerFile.isFile() || !tsmuxerFile.canExecute()) {
			throw new IllegalStateException(
					"Invalid tsMuxeR binary path: " + tsmuxer + ". Please ensure the file exists and is executable.");
		}
		return tsmuxer;
	}

	static boolean isTsMuxeRAvailable() {
		try {
			resolveTsMuxeRBinary();
			return true;
		} catch (IllegalStateException e) {
			log.warn("tsMuxeR binary not available: {}", e.getMessage());
			return false;
		}
	}

}
