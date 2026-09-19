package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.clpi.M2tsClpiRegenBuilder;
import org.brts.lowlevel.igs.IgsDemuxer;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.m2ts.M2tsDumpFormatter;
import org.brts.lowlevel.m2ts.M2tsDumpTableFormatter;
import org.brts.lowlevel.m2ts.M2tsDumper;
import org.brts.lowlevel.m2ts.M2tsIgsMuxer;
import org.brts.lowlevel.m2ts.M2tsWriter;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CLI for M2TS (Blu-ray MPEG-2 Transport Stream) operations.
 */
public class M2tsCli {

	// =========================================================================
	// m2ts-info
	// =========================================================================

	public static class InfoOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the .m2ts file to inspect")
		File input;

		@Option(name = "--output", usage = "Output JSON file path (default: stdout)")
		File output;

	}

	public static class Info extends FeatureRunner<InfoOptions> {

		@Override
		public String getCommandName() {
			return "m2ts-info";
		}

		@Override
		public String getDescription() {
			return "Inspect stream and timing metadata of an M2TS file";
		}

		@Override
		protected void execute(InfoOptions opts) throws Exception {
			M2tsInfo info = new M2tsParser().parse(opts.input.toPath());
			writeJson(opts.output, info);
			if (opts.output != null) {
				System.out.println("M2TS info → " + opts.output);
			}
		}

	}

	// =========================================================================
	// m2ts-extract
	// =========================================================================

	public static class ExtractOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the .m2ts source file")
		File input;

		@Option(name = "--output", required = true, usage = "Output directory for elementary stream files")
		File outputDir;

		@Option(name = "--pids", usage = "Comma-separated list of PIDs to extract (default: all)")
		String pids;

		@Option(name = "--igs-demux", usage = "Also demux IGS streams into sub-folders with bitmaps and metadata")
		boolean igsDemux;

	}

	public static class Extract extends FeatureRunner<ExtractOptions> {

		@Override
		public String getCommandName() {
			return "m2ts-extract";
		}

		@Override
		public String getDescription() {
			return "Demux elementary streams from an M2TS file";
		}

		@Override
		protected void execute(ExtractOptions opts) throws Exception {
			Path inputPath = opts.input.toPath();
			Path outputDir = opts.outputDir.toPath();

			M2tsParser parser = new M2tsParser();
			M2tsInfo info = parser.parse(inputPath);

			Set<Integer> pidFilter = null;
			if (opts.pids != null && !opts.pids.isBlank()) {
				pidFilter = Arrays.stream(opts.pids.split(",")).map(String::trim)
						.map(s -> s.startsWith("0x") || s.startsWith("0X") ? Integer.parseInt(s.substring(2), 16)
								: Integer.parseInt(s))
						.collect(Collectors.toCollection(HashSet::new));
			}

			new M2tsExtractor().extract(inputPath, info, outputDir, pidFilter);
			System.out.println("Extraction complete → " + outputDir);

			// Optionally demux IGS streams
			if (opts.igsDemux && info.getStreams() != null) {
				IgsDemuxer igsDemuxer = new IgsDemuxer();
				for (M2tsStreamInfo stream : info.getStreams()) {
					if (stream.getCodingType() == StreamCodingType.INTERACTIVE_GRAPHICS) {
						String igsFileName = String.format("pid_%04x.igs", stream.getPid());
						Path igsFile = outputDir.resolve(igsFileName);
						if (igsFile.toFile().exists()) {
							Path igsOutDir = outputDir.resolve(String.format("pid_%04x_igs", stream.getPid()));
							igsDemuxer.demux(igsFile, igsOutDir);
							System.out.println("IGS demuxed → " + igsOutDir);
						}
					}
				}
			}
		}

	}

	// =========================================================================
	// igs-demux
	// =========================================================================

	public static class IgsDemuxOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to a raw .igs elementary stream file")
		File input;

		@Option(name = "--output", required = true, usage = "Output directory for demuxed IGS resources")
		File outputDir;

	}

	public static class IgsDemux extends FeatureRunner<IgsDemuxOptions> {

		@Override
		public String getCommandName() {
			return "igs-demux";
		}

		@Override
		public String getDescription() {
			return "Demux a raw IGS elementary stream into resources";
		}

		@Override
		protected void execute(IgsDemuxOptions opts) throws Exception {
			new IgsDemuxer().demux(opts.input.toPath(), opts.outputDir.toPath());
			System.out.println("IGS demux complete → " + opts.outputDir);
		}

	}

	// =========================================================================
	// igs-mux
	// =========================================================================

	public static class IgsMuxOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to a demuxed IGS directory (with igs_manifest.json)")
		File inputDir;

		@Option(name = "--output", required = true, usage = "Output .igs file path")
		File output;

	}

	public static class IgsMux extends FeatureRunner<IgsMuxOptions> {

		@Override
		public String getCommandName() {
			return "igs-mux";
		}

		@Override
		public String getDescription() {
			return "Mux demuxed IGS resources back into an IGS stream";
		}

		@Override
		protected void execute(IgsMuxOptions opts) throws Exception {
			new IgsMuxer().mux(opts.inputDir.toPath(), opts.output.toPath());
			System.out.println("IGS mux complete → " + opts.output);
		}

	}

	// =========================================================================
	// m2ts-create
	// =========================================================================

	public static class CreateOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--descriptor", required = true, usage = "Path to the .m2ts-descriptor.json file describing the mux")
		File descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for the .m2ts file (e.g. BDMV/STREAM/)")
		File outputDir;

		@Option(name = "--clpi", usage = "Output directory for the matching .clpi file (e.g. BDMV/CLIPINF/); "
				+ "if omitted the CLPI is written next to the M2TS")
		File clpiDir;

	}

	public static class Create extends FeatureRunner<CreateOptions> {

		@Override
		public String getCommandName() {
			return "m2ts-create";
		}

		@Override
		public String getDescription() {
			return "Mux elementary streams into a new M2TS file with matching CLPI";
		}

		@Override
		protected void execute(CreateOptions opts) throws Exception {
			M2tsDescriptor descriptor = loadJson(opts.descriptor, M2tsDescriptor.class);

			String outputName = descriptor.getOutputName();
			if (outputName == null || outputName.isBlank()) {
				throw new IllegalArgumentException("'outputName' is required in the descriptor");
			}

			Path outputDir = opts.outputDir.toPath();
			Path m2tsPath = outputDir.resolve(outputName + ".m2ts");

			Path clpiDir = opts.clpiDir != null ? opts.clpiDir.toPath() : outputDir;
			Path clpiPath = clpiDir.resolve(outputName + ".clpi");

			// --- Write M2TS ---
			M2tsWriter writer = new M2tsWriter();
			writer.write(descriptor, m2tsPath);
			System.out.println("M2TS written → " + m2tsPath);

			// --- Build and write matching CLPI ---
			int applicationType = ClipInfo.APPLICATION_TYPE_MOVIE;
			// if only one stream and it's a IGS, set application type to interactive graphics
			if (descriptor.getStreams().size() == 1 && descriptor.getStreams().get(0)
					.getStreamTypeByte() == StreamCodingType.INTERACTIVE_GRAPHICS.getCodingTypeByte()) {
				applicationType = ClipInfo.APPLICATION_TYPE_INTERACTIVE_GRAPHICS;
			}
			ClipInfo clipInfo = writer.buildClipInfo(descriptor, outputName, applicationType);
			new ClipInfoWriter().write(clipInfo, clpiPath);
			System.out.println("CLPI written → " + clpiPath);
		}

	}

	// =========================================================================
	// m2ts-igs-mux
	// =========================================================================

	public static class M2tsIgsMuxOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--descriptor", required = true, usage = "Path to the .m2ts-descriptor.json file describing the mux")
		File descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for the .m2ts file (e.g. BDMV/STREAM/)")
		File outputDir;

		@Option(name = "--clpi", usage = "Output directory for the matching .clpi file (e.g. BDMV/CLIPINF/); "
				+ "if omitted the CLPI is written next to the M2TS")
		File clpiDir;

	}

	public static class M2tsIgsMux extends FeatureRunner<M2tsIgsMuxOptions> {

		@Override
		public String getCommandName() {
			return "m2ts-igs-mux";
		}

		@Override
		public String getDescription() {
			return "Mux an IGS stream into a new M2TS file with matching CLPI";
		}

		@Override
		protected void execute(M2tsIgsMuxOptions opts) throws Exception {
			M2tsDescriptor descriptor = loadJson(opts.descriptor, M2tsDescriptor.class);

			String outputName = descriptor.getOutputName();
			if (outputName == null || outputName.isBlank()) {
				throw new IllegalArgumentException("'outputName' is required in the descriptor");
			}

			Path outputDir = opts.outputDir.toPath();
			Path m2tsPath = outputDir.resolve(outputName + ".m2ts");

			Path clpiDir = opts.clpiDir != null ? opts.clpiDir.toPath() : outputDir;
			Path clpiPath = clpiDir.resolve(outputName + ".clpi");

			M2tsIgsMuxer m2tsigsMuxer = new M2tsIgsMuxer();
			m2tsigsMuxer.mux(Path.of(descriptor.getStreams().get(0).getFile()), m2tsPath);

			ClipInfo clipInfo = new M2tsClpiRegenBuilder().build(m2tsPath, outputName);

			new ClipInfoWriter().write(clipInfo, clpiPath);

			System.out.println("CLPI written → " + clpiPath);
		}

	}

	// =========================================================================
	// m2ts-dump
	// =========================================================================

	public static class DumpOptions extends org.brts.cli.BaseOptions {

		@Option(name = "--input", required = true, usage = "Path to the .m2ts file to dump")
		File input;

		@Option(name = "--start", usage = "First packet number to dump (0-based, default: 0)")
		long start = 0;

		@Option(name = "--stop", usage = "Last packet number to dump (inclusive, default: last packet)")
		long stop = -1;

		@Option(name = "--keep-tables", usage = "Include PAT/PMT/PCR PIDs in the dump")
		boolean keepTables = false;

		@Option(name = "--ansi-colors", usage = "Enable ANSI colors in the output")
		boolean ansiColors = false;

		@Option(name = "--format", usage = "Output format: table (default)")
		String format = "table";

	}

	public static class Dump extends FeatureRunner<DumpOptions> {

		@Override
		public String getCommandName() {
			return "m2ts-dump";
		}

		@Override
		public String getDescription() {
			return "Dump M2TS packet information for debugging";
		}

		@Override
		protected void execute(DumpOptions opts) throws Exception {
			Path inputPath = opts.input.toPath();
			long fileSize = Files.size(inputPath);
			long totalPackets = fileSize / M2tsParser.SOURCE_PACKET_SIZE;

			long start = opts.start;
			long stop = opts.stop >= 0 ? opts.stop : totalPackets - 1;

			if (start < 0 || start >= totalPackets) {
				System.err.println("Start packet " + start + " out of range (0.." + (totalPackets - 1) + ")");
				System.exit(1);
			}
			if (stop >= totalPackets) {
				stop = totalPackets - 1;
			}
			if (stop < start) {
				System.err.println("Stop packet (" + stop + ") must be >= start packet (" + start + ")");
				System.exit(1);
			}

			M2tsInfo info = new M2tsParser().parse(inputPath);

			System.out.printf("Dumping packets %d..%d of %s (%d total packets)%n", start, stop, inputPath.getFileName(),
					totalPackets);

			M2tsDumpFormatter formatter = createFormatter(opts, info);
			new M2tsDumper(info, formatter).dump(inputPath, start, stop, opts.keepTables);
		}

		private M2tsDumpFormatter createFormatter(DumpOptions opts, M2tsInfo info) {
			switch (opts.format) {
			case "table":
				return new M2tsDumpTableFormatter(info, opts.ansiColors);
			default:
				throw new IllegalArgumentException("Unknown dump format: '" + opts.format + "'. Supported: table");
			}
		}

	}

}
