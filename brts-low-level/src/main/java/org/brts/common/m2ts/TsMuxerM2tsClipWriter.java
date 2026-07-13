package org.brts.common.m2ts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.brts.common.m2ts.model.M2tsChapter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.utils.ProcessUtils;
import org.brts.common.utils.TsMuxerUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TsMuxerM2tsClipWriter implements M2tsClipWriter {

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
			metaFilecontents.append(String.format("\n%s, \"%s\", lang=%s",
					TsMuxerUtils.translateCodecToTsMuxer(stream.getStreamTypeByte()), stream.getFile(),
					stream.getLanguage() != null ? stream.getLanguage() : "und"));
		});

		Path parent = m2tsPath.getParent();
		parent.toFile().mkdirs();
		Path workDir = Files.createTempDirectory(parent, ".mux_temp");
		Path muxOpts = Files.createTempFile(workDir, "meta_file", "_for_ts_muxer");
		Files.writeString(muxOpts, metaFilecontents);
		log.debug("Created tsMuxeR meta file at {}:\n{}", muxOpts, metaFilecontents);
		log.info("Running tsMuxeR '{}' to write M2TS clip to {} …", TsMuxerUtils.resolveTsMuxeRBinary(), workDir);
		ProcessBuilder builder = new ProcessBuilder(TsMuxerUtils.resolveTsMuxeRBinary(), muxOpts.toString(),
				workDir.toString());
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
}
