package org.brts.middle.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.brts.common.json.JsonMapperFactory;
import org.brts.common.m2ts.M2tsWriter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.middle.menu.descriptor.SetupMenuDescriptor;
import org.brts.middle.menu.media.MediaSource;
import org.brts.middle.menu.media.MediaSourceFactory;
import org.brts.middle.pid.PidAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
public class SetupMenuGenerator {

	private static final Logger log = LoggerFactory.getLogger(SetupMenuGenerator.class);

	private final ObjectMapper mapper = JsonMapperFactory.get();

	/**
	 * Generates a setup menu M2TS from a JSON descriptor file.
	 * @param descriptorFile path to the setup menu JSON descriptor
	 * @param outputDir directory where the output M2TS (and intermediate files) will be
	 * written
	 * @throws IOException on I/O or generation error
	 */
	public void generate(Path descriptorFile, Path outputDir) throws IOException {
		log.info("Generating setup menu from descriptor: {}", descriptorFile);

		SetupMenuDescriptor descriptor = mapper.readValue(descriptorFile.toFile(), SetupMenuDescriptor.class);

		generate(descriptor, outputDir);
	}

	/**
	 * Generates a setup menu M2TS from an in-memory descriptor.
	 * @param descriptor the setup menu descriptor
	 * @param outputDir directory where output files will be written
	 * @throws IOException on I/O or generation error
	 */
	public void generate(SetupMenuDescriptor descriptor, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		Path workDir = outputDir.resolve("work");
		Files.createDirectories(workDir);

		// ── 1. Extract background media ─────────────────────────────────────

		MediaSource mediaSource = MediaSourceFactory.create(descriptor.getBackgroundMedia());
		MediaSource.ExtractionResult extraction = mediaSource.extract(workDir);
		log.info("Background media extracted: video={}, audio={}", extraction.videoEsFile(), extraction.audioEsFile());

		// ── 2. Build IGS ────────────────────────────────────────────────────

		SetupMenuIgsBuilder igsBuilder = new SetupMenuIgsBuilder();
		IgsDisplaySet displaySet = igsBuilder.build(descriptor);

		// ── 3. Encode IGS to raw ES file ────────────────────────────────────

		IgsMuxer igsMuxer = new IgsMuxer();
		byte[] igsEs = igsMuxer.encodeDisplaySet(displaySet);
		Path igsEsFile = workDir.resolve("menu.igs");
		Files.write(igsEsFile, igsEs);
		log.info("IGS ES written: {} bytes → {}", igsEs.length, igsEsFile);

		// ── 4. Build M2TS descriptor ────────────────────────────────────────

		PidAllocator pids = new PidAllocator();

		M2tsDescriptor m2tsDesc = new M2tsDescriptor();
		m2tsDesc.setOutputName(descriptor.getOutputName());

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

		// IGS stream
		M2tsDescriptor.StreamEntry igsStream = new M2tsDescriptor.StreamEntry();
		igsStream.setFile(igsEsFile.toAbsolutePath().toString());
		igsStream.setPid(pids.allocate(StreamCodingType.INTERACTIVE_GRAPHICS));
		igsStream.setStreamTypeByte(StreamCodingType.INTERACTIVE_GRAPHICS.getCodingTypeByte());
		streams.add(igsStream);

		m2tsDesc.setStreams(streams);

		// ── 5. Mux into M2TS ───────────────────────────────────────────────

		String outputName = descriptor.getOutputName();
		Path m2tsOutput = outputDir.resolve(outputName + ".m2ts");

		M2tsWriter writer = new M2tsWriter();
		writer.write(m2tsDesc, m2tsOutput);

		log.info("Setup menu M2TS generated: {}", m2tsOutput);

		// Optionally write the M2TS descriptor for reference
		Path descOutPath = outputDir.resolve(outputName + ".m2ts-descriptor.json");
		mapper.writerWithDefaultPrettyPrinter().writeValue(descOutPath.toFile(), m2tsDesc);
		log.info("M2TS descriptor written: {}", descOutPath);
	}

}
