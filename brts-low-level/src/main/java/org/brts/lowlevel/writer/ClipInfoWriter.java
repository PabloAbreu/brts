package org.brts.lowlevel.writer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.io.BinaryWriter;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.clpi.EpMap;

/**
 * Writer for CLPI (Clip Information) binary files.
 * <p>
 * Generates a standards-compliant {@code XXXXX.clpi} file from a {@link ClipInfo} model.
 * The output is compatible with players that implement the Blu-ray Read-Only spec
 * v2.x/3.x.
 * <p>
 * Header layout (40 bytes): <pre>
 *   4 magic ("HDMV") + 4 version ("0200")
 *   5×4 offsets (SequenceInfo, ProgramInfo, CPI, ClipMark, ExtData)
 *   12 reserved bytes
 * </pre> The ClipInfo section always starts immediately at byte 40.
 */
public class ClipInfoWriter implements BlurayFileWriter<ClipInfo> {

	private static final String MAGIC = "HDMV";

	private static final String VERSION = "0200";

	/** Fixed header size: 8 magic/version + 20 offsets + 12 reserved. */
	private static final int HEADER_SIZE = 40;

	@Override
	public void write(ClipInfo model, OutputStream output) throws IOException {
		// Build each section independently, then assemble with correct offsets
		byte[] clipInfoSection = buildClipInfoSection(model);
		byte[] sequenceSection = buildSequenceInfoSection(model);
		byte[] programSection = buildProgramInfoSection(model);
		byte[] cpiSection = buildCpiSection(model);
		byte[] clipMarkSection = buildClipMarkSection();
		byte[] extensionSection = buildExtensionSection();

		int clipInfoEnd = HEADER_SIZE + clipInfoSection.length;
		int sequenceOffset = clipInfoEnd;
		int programOffset = sequenceOffset + sequenceSection.length;
		int cpiOffset = programOffset + programSection.length;
		int clipMarkOffset = cpiOffset + cpiSection.length;
		int extensionOffset = clipMarkOffset + clipMarkSection.length;

		BinaryWriter w = new BinaryWriter(output);

		// File header (40 bytes)
		w.writeAscii(MAGIC);
		w.writeAscii(VERSION);
		w.writeInt(sequenceOffset);
		w.writeInt(programOffset);
		w.writeInt(cpiOffset);
		w.writeInt(clipMarkOffset);
		w.writeInt(extensionOffset);
		w.writePadding(12); // reserved

		w.writeBytes(clipInfoSection);
		w.writeBytes(sequenceSection);
		w.writeBytes(programSection);
		w.writeBytes(cpiSection);
		w.writeBytes(clipMarkSection);
		w.writeBytes(extensionSection);
	}

	private byte[] buildExtensionSection() {
		return new byte[4]; // empty extension section (just length=0 ?)
	}

	// -------------------------------------------------------------------------
	// Section builders
	// -------------------------------------------------------------------------

	private byte[] buildClipInfoSection(ClipInfo m) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);

		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);
		wi.writePadding(2); // reserved (2 bytes per spec)
		wi.writeByte(m.getClipStreamType());
		wi.writeByte(m.getApplicationType());
		// 31 reserved bits + 1 is_atc_delta bit
		wi.writeInt(m.isAtcDelta() ? 1 : 0);
		wi.writeInt(m.getTsRecordingRate());
		wi.writeInt(m.getNumSourcePackets());
		wi.writePadding(128); // 128 reserved bytes
		// ts_type_info_block
		wi.writeShort(30);// length=30
		wi.writeByte(0x80);// no clue what that is
		wi.writeAscii("HDMV");// always seems to be there
		wi.writePadding(25);// up to 32 bytes total for ts_type_info_block with length

		w.writeInt(inner.size()); // section length
		w.writeBytes(inner.toByteArray());
		return buf.toByteArray();
	}

	private byte[] buildSequenceInfoSection(ClipInfo m) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);

		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);
		wi.writePadding(1); // reserved
		wi.writeByte(1); // num_atc_sequence
		// ATC sequence
		wi.writeInt(0); // spn_atc_start
		wi.writeByte(1); // num_stc_sequence
		wi.writeByte(0); // offset_stc_id
		// STC sequence
		long startPts45 = m.getTsRecordingStartPts() != null ? m.getTsRecordingStartPts().getTicks() / 2 : 0;
		long endPts45 = m.getTsRecordingEndPts() != null ? m.getTsRecordingEndPts().getTicks() / 2 : 0;
		wi.writeShort(0x1001); // pcr_pid (standard default)
		wi.writeInt(0); // spn_stc_start
		wi.writeInt(startPts45); // presentation_start_time (45 kHz)
		wi.writeInt(endPts45); // presentation_end_time (45 kHz)

		w.writeInt(inner.size());
		w.writeBytes(inner.toByteArray());

		w.padToFour();
		return buf.toByteArray();
	}

	private byte[] buildProgramInfoSection(ClipInfo m) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);

		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);
		wi.writePadding(1); // reserved
		wi.writeByte(1); // num_program = 1

		// For each program:
		wi.writeInt(0); // spn_program_sequence_begin (always 0 = file start ?)
		wi.writeShort(0x0100); // program_map_PID = 256
		List<ClipStream> streams = m.getStreams() != null ? m.getStreams() : List.of();
		wi.writeByte(streams.size());
		wi.writeByte(0); // num_groups (what is that ?)

		for (ClipStream s : streams) {
			wi.writeShort(s.getPid());
			int streamInfoSize = 21;// seen in actual files
			// codingType + payload + 12 '0' + 4 0
			wi.writeByte(streamInfoSize);

			wi.writeByte(s.getCodingType().getCodingTypeByte());
			if (s.getCodingType().isVideo()) {
				// does not manage HEVC for now
				int vf = (s.getVideoFormat() != null ? s.getVideoFormat() : 0);
				int fr = (s.getFrameRate() != null ? s.getFrameRate() : 0);
				wi.writeByte((vf << 4) | fr);
				// we ignore the "OCFlag", whatever that is
				wi.writeByte((s.getAspectRatio() != null ? s.getAspectRatio() : 0) << 4);
				wi.writeShort(0);// 17 bits reserved, counting the last 1 from the
									// previous byte
				// pad with '0' and 4 zeroes (seen in actual files, not sure if
				// required or just reserved)
			}
			else if (s.getCodingType().isAudio()) {
				int ch = (s.getAudioChannelLayout() != null ? s.getAudioChannelLayout() : 0);
				int sr = (s.getSampleRate() != null ? s.getSampleRate() : 0);
				wi.writeByte((ch << 4) | sr);
				String lang = s.getLanguage() != null ? s.getLanguage() : "und";
				wi.writeAscii(String.format("%-3s", lang).substring(0, 3));
			}
			// never seen that in real files
			else if (s.getCodingType() == StreamCodingType.TEXT_SUBTITLE) {
				wi.writeByte(s.getCharacterCode() != null ? s.getCharacterCode() : 0);
				String lang = s.getLanguage() != null ? s.getLanguage() : "und";
				wi.writeAscii(String.format("%-3s", lang).substring(0, 3));
			}
			else /*
					 * if (s.getCodingType()== StreamCodingType.PRESENTATION_GRAPHICS ||
					 * s.getCodingType() == StreamCodingType.INTERACTIVE_GRAPHICS)
					 */ {
				String lang = s.getLanguage() != null ? s.getLanguage() : "und";
				wi.writeAscii(String.format("%-3s", lang).substring(0, 3));
				wi.writeByte(0);// found in real files, not sure what it is (null
								// terminator ?)
			}
			wi.writePadding(streamInfoSize - 9, '0');
			wi.writePadding(4);
		}

		w.writeInt(inner.size());
		w.writeBytes(inner.toByteArray());
		w.padToFour();
		return buf.toByteArray();
	}

	private byte[] buildCpiSection(ClipInfo m) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);

		if (m.getEpMap() == null || m.getEpMap().getStreams() == null || m.getEpMap().getStreams().isEmpty()) {
			w.writeInt(0); // empty CPI
			return buf.toByteArray();
		}

		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);

		// 12 reserved bits + 4-bit CPI type = 2 bytes
		wi.writeShort(0x0001); // type = 1 (EP_map)

		// After this 2-byte type field, the EP_map starts. Offsets in stream headers
		// are relative to the start of this EP_map block (i.e., right after the type
		// field).
		// We track the ep_map content separately so we know the offset base.
		List<EpMap.EpMapStream> streams = m.getEpMap().getStreams();

		// reserved + num_stream_pid
		wi.writeByte(0); // reserved
		wi.writeByte(streams.size());

		// Per-stream header size: 2 (pid) + 4 (block1) + 2 (block2) + 4 (addr) = 12 bytes
		int streamHeadersSize = 2 + streams.size() * 12; // +2 for reserved+count above

		// Build per-stream coarse/fine data to calculate offsets
		List<byte[]> streamDataBlocks = new ArrayList<>();
		List<int[]> streamCoarseCounts = new ArrayList<>();
		List<int[]> streamFineCounts = new ArrayList<>();

		for (EpMap.EpMapStream eps : streams) {
			List<EpMap.EpMapEntry> entries = eps.getEntries() != null ? eps.getEntries() : List.of();
			byte[] streamData = buildEpMapStreamData(entries);
			streamDataBlocks.add(streamData);

			int[] counts = computeCoarseFineCounts(entries);
			streamCoarseCounts.add(counts);
			streamFineCounts.add(new int[] { entries.size() });
		}

		// Calculate stream offsets (relative to ep_map start = right after the 2-byte
		// type field)
		int currentStreamOffset = streamHeadersSize;
		for (int i = 0; i < streams.size(); i++) {
			EpMap.EpMapStream eps = streams.get(i);
			List<EpMap.EpMapEntry> entries = eps.getEntries() != null ? eps.getEntries() : List.of();
			int numCoarse = streamCoarseCounts.get(i)[0];
			int numFine = entries.size();

			wi.writeShort(eps.getPid());

			// Pack: 10 reserved + 4 ep_stream_type + 16 num_coarse + 2 high_fine = 32
			// bits
			long block1 = ((long) (eps.getEpType() & 0x0F) << 18) | ((long) (numCoarse & 0xFFFF) << 2)
					| ((numFine >> 16) & 0x3);
			wi.writeInt(block1);
			// 16 low bits of num_fine
			wi.writeShort(numFine & 0xFFFF);
			// ep_map_stream_start_addr (relative to ep_map block start)
			wi.writeInt(currentStreamOffset);

			currentStreamOffset += streamDataBlocks.get(i).length;
		}

		// Write all stream data blocks
		for (byte[] block : streamDataBlocks) {
			wi.writeBytes(block);
		}

		w.writeInt(inner.size()); // section length
		w.writeBytes(inner.toByteArray());
		return buf.toByteArray();
	}

	/**
	 * Builds the binary EP_map data block for one stream (coarse + fine entries). Layout:
	 * fine_start_offset (4 bytes) + coarse entries + fine entries.
	 */
	private byte[] buildEpMapStreamData(List<EpMap.EpMapEntry> entries) throws IOException {
		if (entries.isEmpty()) {
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			new BinaryWriter(buf).writeInt(4); // fine_start = after this field itself
			return buf.toByteArray();
		}

		// Decompose into coarse/fine
		List<int[]> coarseList = new ArrayList<>(); // [ref_fine_id, pts_coarse,
													// spn_coarse]
		List<int[]> fineList = new ArrayList<>(); // [block: 1+3+11+17 = 32 bits]

		int prevCoarsePtsKey = -1;
		long prevCoarseSpnKey = -1;

		for (int i = 0; i < entries.size(); i++) {
			EpMap.EpMapEntry e = entries.get(i);
			long pts45 = e.getPtsTicks() / 2; // 90 kHz → 45 kHz
			long spn = e.getSpn();

			int coarsePtsKey = (int) (pts45 >> 18);
			long coarseSpnKey = spn >> 17;

			if (coarsePtsKey != prevCoarsePtsKey || coarseSpnKey != prevCoarseSpnKey) {
				int coarsePts = (int) ((pts45 >> 18) & 0x3FFF);
				int coarseSpn = (int) (spn & 0xFFFFFFFFL);
				coarseList.add(new int[] { i, coarsePts, coarseSpn });
				prevCoarsePtsKey = coarsePtsKey;
				prevCoarseSpnKey = coarseSpnKey;
			}

			// Fine entry
			int ptsEpFine = (int) ((pts45 >> 8) & 0x7FF);
			int spnEpFine = (int) (spn & 0x1FFFF);
			int fineBlock = ((e.isAngleChangePoint() ? 1 : 0) << 31) | ((e.getIEndPositionOffset() & 0x07) << 28)
					| ((ptsEpFine & 0x7FF) << 17) | (spnEpFine & 0x1FFFF);
			fineList.add(new int[] { fineBlock });
		}

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);

		// fine_start_offset: 4 bytes for this field + coarse entries (8 bytes each)
		int fineStartOffset = 4 + coarseList.size() * 8;
		w.writeInt(fineStartOffset);

		// Write coarse entries (8 bytes each: 18+14=32 bits + 32 bits)
		for (int[] c : coarseList) {
			long cBlock = ((long) (c[0] & 0x3FFFF) << 14) | (c[1] & 0x3FFF);
			w.writeInt(cBlock);
			w.writeInt(c[2]); // spn_ep (full 32 bits)
		}

		// Write fine entries (4 bytes each)
		for (int[] f : fineList) {
			w.writeInt(f[0] & 0xFFFFFFFFL);
		}

		return buf.toByteArray();
	}

	/** Computes the number of coarse entries for a set of EP entries. */
	private int[] computeCoarseFineCounts(List<EpMap.EpMapEntry> entries) {
		if (entries.isEmpty())
			return new int[] { 0 };

		int count = 0;
		int prevCoarsePtsKey = -1;
		long prevCoarseSpnKey = -1;

		for (EpMap.EpMapEntry e : entries) {
			long pts45 = e.getPtsTicks() / 2;
			int coarsePtsKey = (int) (pts45 >> 18);
			long coarseSpnKey = e.getSpn() >> 17;
			if (coarsePtsKey != prevCoarsePtsKey || coarseSpnKey != prevCoarseSpnKey) {
				count++;
				prevCoarsePtsKey = coarsePtsKey;
				prevCoarseSpnKey = coarseSpnKey;
			}
		}
		return new int[] { count };
	}

	private byte[] buildClipMarkSection() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);
		w.writeInt(0);// found in real files
		// or maybe it could be more complicated:
		// w.writeInt(2); // section length = 2
		// w.writeShort(0); // num_clip_mark = 0
		return buf.toByteArray();
	}

}
