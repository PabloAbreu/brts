package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.common.m2ts.M2tsDemuxer;
import org.brts.common.m2ts.M2tsExtractor;
import org.brts.common.m2ts.M2tsPacketHandler;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.M2tsWriter;
import org.brts.common.m2ts.model.M2tsDescriptor;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.igs.IgsDemuxer;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CLI for M2TS (Blu-ray MPEG-2 Transport Stream) operations.
 */
public class M2tsCli {

	// =========================================================================
	// m2ts-info
	// =========================================================================

	static class InfoOptions {

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
		protected InfoOptions createOptions() {
			return new InfoOptions();
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

	static class ExtractOptions {

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
		protected ExtractOptions createOptions() {
			return new ExtractOptions();
		}

		@Override
		protected void execute(ExtractOptions opts) throws Exception {
			Path inputPath = opts.input.toPath();
			Path outputDir = opts.outputDir.toPath();

			M2tsParser parser = new M2tsParser();
			M2tsInfo info = parser.parse(inputPath);

			Set<Integer> pidFilter = null;
			if (opts.pids != null && !opts.pids.isBlank()) {
				pidFilter = Arrays.stream(opts.pids.split(","))
					.map(String::trim)
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

	static class IgsDemuxOptions {

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
		protected IgsDemuxOptions createOptions() {
			return new IgsDemuxOptions();
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

	static class IgsMuxOptions {

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
		protected IgsMuxOptions createOptions() {
			return new IgsMuxOptions();
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

	static class CreateOptions {

		@Option(name = "--descriptor", required = true,
				usage = "Path to the .m2ts-descriptor.json file describing the mux")
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
		protected CreateOptions createOptions() {
			return new CreateOptions();
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
			ClipInfo clipInfo = writer.buildClipInfo(descriptor, outputName);
			new ClipInfoWriter().write(clipInfo, clpiPath);
			System.out.println("CLPI written → " + clpiPath);
		}

	}

	// =========================================================================
	// m2ts-dump
	// =========================================================================

	static class DumpOptions {

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
		protected DumpOptions createOptions() {
			return new DumpOptions();
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
			System.out.printf("%-10s %-10s %-10s %-12s %-6s  %-7s %-12s (ms)     %-12s (ms)     %-7s%n", "Packet#",
					"Nb Packets", "Pkts totl", "ATS", "PID", "Type", "PTS", "DTS", "PES LN");

			try (DumpPacketHandler handler = new DumpPacketHandler(info)) {
				new M2tsDemuxer().demux(inputPath, info, handler, null, start, stop, opts.keepTables);
			}
		}

	}

	private static float convertPtsToSeconds(long pts) {
		return pts / 90000f;
	}

	/**
	 * {@link M2tsPacketHandler} that prints a summary line for each group of consecutive
	 * packets sharing the same PID.
	 */
	private static class DumpPacketHandler implements M2tsPacketHandler {

		// for faster access
		private final Map<Integer, M2tsStreamInfo> pidToStreamInfo = new LinkedHashMap<>();

		private final M2tsInfo info;

		private int previousPid = -1;

		private long groupStartPacket;

		private long groupCount;

		private long groupAts;

		private long groupPts;

		private long groupDts;

		private long groupPesLength;

		// maintain a running count of packets per PID to show totals in the dump output
		private Map<Integer, Long> pidToPacketCount = new HashMap<>();

		// output PID → ANSI color code for better readability of dumps with many
		// interleaved PIDs
		private Map<Integer, String> pidToColor = new HashMap<>();

		private static final String DEFAULT_ANSI_COLOR = "\u001B[0m";

		private static final String[] ANSI_COLORS = { "\u001B[31m", // red
				"\u001B[32m", // green
				"\u001B[33m", // yellow
				"\u001B[34m", // blue
				"\u001B[35m", // magenta
				"\u001B[36m", // cyan
		};

		DumpPacketHandler(M2tsInfo info) {
			int colorIndex = 0;
			if (info.getStreams() != null) {
				for (M2tsStreamInfo s : info.getStreams()) {
					int pid = s.getPid();
					pidToStreamInfo.put(pid, s);
					if (colorIndex < ANSI_COLORS.length) {
						pidToColor.put(pid, ANSI_COLORS[colorIndex++]);
					}
					else {
						pidToColor.put(pid, DEFAULT_ANSI_COLOR);
					}
				}
			}
			this.info = info;
		}

		private long parsePts(byte[] payload, int offset) {
			int PTS_DTS_flags = payload[offset + 1] & 0xC0;
			if ((PTS_DTS_flags & 0x80) != 0) {
				return parsePtsOrDtsTimestamp(payload, offset + 3);
			}
			return -1;
		}

		private long parseDts(byte[] payload, int offset) {
			int PTS_DTS_flags = payload[offset + 1] & 0xC0;
			if ((PTS_DTS_flags & 0x40) != 0) {
				return parsePtsOrDtsTimestamp(payload, offset + 8);
			}
			return -1;
		}

		private static long parsePtsOrDtsTimestamp(byte[] data, int offset) {
			return ((long) (data[offset] & 0x0E) << 29) | ((data[offset + 1] & 0xFF) << 22)
					| ((data[offset + 2] & 0xFE) << 14) | ((data[offset + 3] & 0xFF) << 7)
					| ((data[offset + 4] & 0xFE) >> 1);
		}

		private String bytesToHex(byte[] bytes, int offset, int length) {
			StringBuilder sb = new StringBuilder();
			for (int i = offset; i < offset + length; i++) {
				sb.append(String.format("%02X ", bytes[i]));
			}
			return sb.toString().trim();
		}

		@Override
		public void onPayload(int pid, byte[] payload, int offset, int length, boolean payloadUnitStart,
				long packetIndex, long ats) throws IOException {
			if (pid != previousPid) {
				flushGroup();
				groupStartPacket = packetIndex;
				groupCount = 1;
				groupAts = ats;
				groupPesLength = -1;

				// decode PES header when new group starts
				if (payloadUnitStart && detectPesStart(payload, offset, length)) {
					groupPesLength = ((payload[offset + 4] & 0xFF) << 8) | (payload[offset + 5] & 0xFF);
					// if PES header is complete, try to extract PTS and DTS values
					if (length >= 12) {
						long pts = parsePts(payload, offset + 6);
						long dts = parseDts(payload, offset + 6);
						if (pts >= 0) {
							groupPts = pts;
						}
						if (dts >= 0) {
							groupDts = dts;
						}
					}
				}
				else {
					groupPts = -1;
					groupDts = -1;
				}
			}
			else {
				groupCount++;
			}
			previousPid = pid;
		}

		private boolean detectPesStart(byte[] payload, int offset, int length) {
			return length >= 6 && payload[offset] == 0x00 && payload[offset + 1] == 0x00 && payload[offset + 2] == 0x01;
		}

		@Override
		public void close() {
			flushGroup();
		}

		private void flushGroup() {
			if (previousPid < 0)
				return;
			M2tsStreamInfo stream = pidToStreamInfo.get(previousPid);
			String streamType = "UNKNOWN";
			if (stream != null) {
				StreamCodingType codingType = stream.getCodingType();
				if (codingType != null)
					streamType = stream.getCategory();
			}
			else {
				if (previousPid == 0) {
					streamType = "PAT";
				}
				else if (previousPid == info.getPmtPid()) {
					streamType = "PMT";
				}
				else if (previousPid == info.getPcrPid()) {
					streamType = "PCR";
				}
				else if (previousPid == 0x1FFF) {
					streamType = "NULL";// padding
				}
			}
			// update total count of packets for this PID
			long packetsSoFar = pidToPacketCount.compute(previousPid,
					(k, v) -> (v == null) ? groupCount : v + groupCount);
			// output stats with color if enabled and available for this PID
			String color = pidToColor.getOrDefault(previousPid, DEFAULT_ANSI_COLOR);
			System.out.print(color);
			float ptsSeconds = convertPtsToSeconds(groupPts);
			float dtsSeconds = convertPtsToSeconds(groupDts);
			System.out.printf("%-10d %-10d %-10d %-12d 0x%04X  %-7s %-12d (%.3f) %-12d (%.3f) %-7d%n", groupStartPacket,
					groupCount, packetsSoFar, groupAts, previousPid, streamType, groupPts, ptsSeconds, groupDts,
					dtsSeconds, groupPesLength);
			System.out.print(DEFAULT_ANSI_COLOR);
		}

	}

}
