package org.brts.lowlevel.popupmenu;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.m2ts.M2tsClipWriter;
import org.brts.common.m2ts.M2tsClipWriterImpl;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.utils.FileUtils;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.igs.model.IgsDisplaySet;

import lombok.extern.slf4j.Slf4j;

/**
 * Generates an out-of-mux IGS popup menu M2TS + CLPI for audio/subtitle track selection.
 * <p>
 * The generated IGS is intended to be referenced via SubPath type 3 in the title's MPLS playlist.
 */
@Slf4j
public class PopupMenuGenerator {

	/** Standard Blu-ray IGS stream type byte (0x91). */
	private static final int IGS_STREAM_TYPE = 0x91;

	/** Standard Blu-ray IGS PID. */
	private static final int IGS_PID = 0x1400;

	/**
	 * Result of popup menu generation.
	 *
	 * @param igsM2ts path to the generated IGS M2TS file
	 * @param igsClpi path to the generated IGS CLPI file
	 */
	public record Result(Path igsM2ts, Path igsClpi) {
	}

	/**
	 * Generates the popup menu IGS M2TS + CLPI.
	 *
	 * @param config    popup menu configuration
	 * @param outputDir root output directory (parent of STREAM/ and CLIPINF/)
	 * @return paths to the generated files
	 * @throws IOException on I/O or encoding error
	 */
	public Result generate(PopupMenuConfig config, Path outputDir) throws IOException {
		String clipName = config.getOutputClipName();

		Path streamDir = outputDir.resolve("STREAM");
		Path clipDir = outputDir.resolve("CLIPINF");
		Files.createDirectories(streamDir);
		Files.createDirectories(clipDir);

		// 1. Build IGS display set
		PopupMenuIgsBuilder igsBuilder = new PopupMenuIgsBuilder();
		IgsDisplaySet displaySet = igsBuilder.build(config);

		// 2. Encode to ES bytes
		IgsMuxer igsMuxer = new IgsMuxer();
		byte[] igsEs = igsMuxer.encodeDisplaySet(displaySet);

		// 3. Write ES to temp file and mux into M2TS + CLPI
		Path workDir = Files.createTempDirectory("brts-popup-menu-");
		try {
			Path igsEsFile = workDir.resolve("popup.igs");
			Files.write(igsEsFile, igsEs);
			log.info("Popup menu IGS ES: {} bytes", igsEs.length);

			M2tsDescriptor menuDesc = new M2tsDescriptor();
			List<M2tsDescriptor.StreamEntry> streams = new ArrayList<>();
			M2tsDescriptor.StreamEntry igsStream = new M2tsDescriptor.StreamEntry();
			igsStream.setFile(igsEsFile.toAbsolutePath().toString());
			igsStream.setPid(IGS_PID);
			igsStream.setStreamTypeByte(IGS_STREAM_TYPE);
			streams.add(igsStream);
			menuDesc.setStreams(streams);

			Path igsM2ts = streamDir.resolve(clipName + ".m2ts");
			Path igsClpi = clipDir.resolve(clipName + ".clpi");

			M2tsClipWriter writer = new M2tsClipWriterImpl();
			writer.write(menuDesc, igsM2ts, igsClpi);

			log.info("Popup menu generated: M2TS={}, CLPI={}", igsM2ts, igsClpi);
			return new Result(igsM2ts, igsClpi);
		} finally {
			FileUtils.deleteDir(workDir);
		}
	}

}
