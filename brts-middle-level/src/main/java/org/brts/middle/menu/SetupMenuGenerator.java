package org.brts.middle.menu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsClipWriter;
import org.brts.common.m2ts.M2tsClipWriterFactory;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.writer.MoviePlaylistWriter;
import org.brts.middle.menu.descriptor.SetupMenuDescriptor;
import org.brts.middle.menu.media.MediaSource;
import org.brts.middle.menu.media.MediaSourceFactory;
import org.brts.middle.pid.PidAllocator;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Top-level orchestrator for setup menu M2TS generation.
 * <p>
 * Coordinates the full pipeline:
 * <ol>
 * <li>Read the JSON descriptor</li>
 * <li>Extract video + audio ES from the background media</li>
 * <li>Build the IGS display set (buttons, navigation, RLE images)</li>
 * <li>Encode the IGS to a raw elementary stream file</li>
 * <li>Mux video + audio + IGS into an M2TS file</li>
 * </ol>
 */
@Slf4j
public class SetupMenuGenerator {

	private final ObjectMapper mapper = JsonMapperFactory.get();

	/**
	 * Generates a setup menu M2TS from an in-memory descriptor.
	 * @param descriptor the setup menu descriptor
	 * @param outputDir directory where output files will be written
	 * @throws IOException on I/O or generation error
	 */
	public void generate(SetupMenuDescriptor descriptor, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		Path bdmv = outputDir;
		if (!bdmv.endsWith("BDMV"))
			bdmv = outputDir.resolve("BDMV");
		Path workDir = outputDir.resolve("work");
		Files.createDirectories(workDir);

		// ── 1. Extract background media ─────────────────────────────────────

		Path bgDir = outputDir.resolve("bg");
		Files.createDirectories(bgDir);
		MediaSource mediaSource = MediaSourceFactory.create(descriptor.getBackgroundMedia());
		MediaSource.ExtractionResult extraction = mediaSource.extract(bgDir);
		log.info("Background media extracted: video={}, audio={}", extraction.videoEsFile(), extraction.audioEsFile());
		MediaSource.ExtractionResult introExtraction = null;
		if (descriptor.getBackgroundIntroMedia() != null) {
			Path introDir = outputDir.resolve("intro");
			Files.createDirectories(introDir);
			MediaSource introSource = MediaSourceFactory.create(descriptor.getBackgroundIntroMedia());
			introExtraction = introSource.extract(introDir);
			log.info("Background intro media extracted: video={}, audio={}", introExtraction.videoEsFile(),
					introExtraction.audioEsFile());
		}

		// ── 2. Build IGS ────────────────────────────────────────────────────

		SetupMenuIgsBuilder igsBuilder = new SetupMenuIgsBuilder();
		IgsDisplaySet displaySet = igsBuilder.build(descriptor);

		// ── 3. Encode IGS to raw ES file ────────────────────────────────────

		IgsMuxer igsMuxer = new IgsMuxer();
		byte[] igsEs = igsMuxer.encodeDisplaySet(displaySet);
		Path igsEsFile = workDir.resolve("menu.igs");
		Files.write(igsEsFile, igsEs);
		log.info("IGS ES written: {} bytes → {}", igsEs.length, igsEsFile);

		// ── 4. Build M2TS descriptors and mux ───────────────────────────────────────

		PidAllocator pids = new PidAllocator();
		muxIntoM2ts(extraction, pids, descriptor.getOutputName(), bdmv);
		muxIntoM2ts(introExtraction, pids, descriptor.getOutputIntroName(), bdmv);

		muxMenu(igsEsFile, pids, descriptor.getOutputMenuName(), bdmv);

		// ── 5. Playlist with intro, loop on background, menu ────────────────────────

		generatePlaylist(descriptor.getOutputIntroName(), descriptor.getOutputName(), descriptor.getOutputMenuName(),
				descriptor.getOutputPlaylistName(), bdmv);
	}

	/**
	 * Generates the mpls playlist file that references the intro, menu, and background
	 * M2TS files.
	 * @param outputIntroName
	 * @param outputName
	 * @param outputMenuName
	 * @param bdmv
	 * @throws IOException
	 */
	private void generatePlaylist(String outputIntroName, String outputName, String outputMenuName,
			String outputPlaylistName, Path bdmv) throws IOException {
		Path playlistDir = bdmv.resolve("PLAYLIST");
		Files.createDirectories(playlistDir);
		Path playlistPath = playlistDir.resolve(outputPlaylistName + ".mpls");
		MoviePlaylistWriter playlistWriter = new MoviePlaylistWriter();
		MoviePlaylist playlist = new MoviePlaylist();
		playlist.setMenu(true);
		playlist.setPlaylistName(outputPlaylistName);// in fact unused
		// TODO videos, menu
		playlistWriter.write(playlist, playlistPath);
	}

	private void muxMenu(Path igsStreamPath, PidAllocator pids, String outputName, Path bdmv) throws IOException {

		M2tsDescriptor m2tsDesc = new M2tsDescriptor();
		List<M2tsDescriptor.StreamEntry> streams = new ArrayList<>();
		// IGS stream
		M2tsDescriptor.StreamEntry igsStream = new M2tsDescriptor.StreamEntry();
		igsStream.setFile(igsStreamPath.toAbsolutePath().toString());
		igsStream.setPid(pids.allocate(StreamCodingType.INTERACTIVE_GRAPHICS));
		igsStream.setStreamTypeByte(StreamCodingType.INTERACTIVE_GRAPHICS.getCodingTypeByte());
		streams.add(igsStream);

		m2tsDesc.setStreams(streams);
		mux(m2tsDesc, outputName, bdmv);

	}

	private void mux(M2tsDescriptor m2tsDesc, String outputName, Path bdmv) throws IOException {

		Path stream = bdmv.resolve("STREAM");
		Files.createDirectories(stream);
		Path clipDir = bdmv.resolve("CLIPINF");
		Files.createDirectories(clipDir);
		Path m2tsOutput = stream.resolve(outputName + ".m2ts");
		Path clpiOutput = clipDir.resolve(outputName + ".clpi");

		M2tsClipWriter writer = M2tsClipWriterFactory.createWriter();
		writer.write(m2tsDesc, m2tsOutput, clpiOutput);
		Path descOutPath = bdmv.resolve(outputName + ".m2ts-descriptor.json");
		mapper.writerWithDefaultPrettyPrinter().writeValue(descOutPath.toFile(), m2tsDesc);
	}

	private void muxIntoM2ts(MediaSource.ExtractionResult extraction, PidAllocator pids, String outputName, Path bdmv)
			throws IOException {

		M2tsDescriptor m2tsDesc = new M2tsDescriptor();
		m2tsDesc.setOutputName(outputName);

		List<M2tsDescriptor.StreamEntry> streams = new ArrayList<>();

		// Video stream
		M2tsDescriptor.StreamEntry videoStream = new M2tsDescriptor.StreamEntry();
		videoStream.setFile(extraction.videoEsFile().toAbsolutePath().toString());
		videoStream.setPid(pids.allocate(StreamCodingType.fromByte(extraction.videoStreamTypeByte())));
		videoStream.setStreamTypeByte(extraction.videoStreamTypeByte());
		videoStream.setFrameRateFps(extraction.frameRateFps());
		streams.add(videoStream);

		// Audio stream (if present)
		if (extraction.audioEsFile() != null && extraction.audioStreamTypeByte() != 0) {
			M2tsDescriptor.StreamEntry audioStream = new M2tsDescriptor.StreamEntry();
			audioStream.setFile(extraction.audioEsFile().toAbsolutePath().toString());
			audioStream.setPid(pids.allocate(StreamCodingType.fromByte(extraction.audioStreamTypeByte())));
			audioStream.setStreamTypeByte(extraction.audioStreamTypeByte());
			audioStream.setLanguage(extraction.audioLanguage());
			if (extraction.audioSampleRateHz() != null)
				audioStream.setSampleRateHz(extraction.audioSampleRateHz());
			if (extraction.audioBitrateKbps() != null)
				audioStream.setBitrateKbps(extraction.audioBitrateKbps());
			if (extraction.audioChannels() != null)
				audioStream.setChannels(extraction.audioChannels());
			streams.add(audioStream);
		}
		mux(m2tsDesc, outputName, bdmv);
	}

}
