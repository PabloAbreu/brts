package org.brts.lowlevel.parser;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-low-level/src/main/java/org/brts/lowlevel/parser/ClipInfoParser.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.brts.common.exception.ParseException;
import org.brts.common.io.BinaryReader;
import org.brts.common.model.StreamCodingType;
import org.brts.common.model.Timestamp;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.clpi.EpMap;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for CLPI (Clip Information) binary files.
 * <p>
 * Reference: Blu-ray Disc Read-Only Format Part 3 Section 8 (CLPI file format).
 */

@Slf4j
public class ClipInfoParser implements BinaryParser<ClipInfo> {

	private static final String MAGIC = "HDMV";

	private static final String VERSION_300 = "0300";

	private static final String VERSION_200 = "0200";

	private static final String VERSION_100 = "0100";

	/**
	 * Absolute byte offset where the ClipInfo section always starts (8 bytes magic/version + 20 bytes offsets + 12
	 * bytes reserved).
	 */
	@SuppressWarnings("unused")
	private static final int CLIP_INFO_START = 40;

	@Override
	public ClipInfo parse(InputStream input) throws IOException {
		try (BinaryReader r = new BinaryReader(input)) {
			return readClipInfo(r);
		}
	}

	@Override
	public ClipInfo parse(Path path) throws IOException {
		ClipInfo parsed = BinaryParser.super.parse(path);
		// get "00001" from "path/to/00001.clpi"
		parsed.setClipName(path.getFileName().toString().replaceFirst("\\.clpi$", ""));
		return parsed;
	}

	@SuppressWarnings("unused")
	private ClipInfo readClipInfo(BinaryReader r) throws IOException {
		// --- File header (4 bytes magic + 4 bytes version) ---
		String magic = r.readAscii(4);
		if (!MAGIC.equals(magic)) {
			throw new ParseException("Not a CLPI file: expected magic '" + MAGIC + "', got '" + magic + "'");
		}
		String version = r.readAscii(4);
		if (!VERSION_300.equals(version) && !VERSION_200.equals(version) && !VERSION_100.equals(version)) {
			throw new ParseException("Unsupported CLPI version: " + version);
		}

		// --- 5 section offsets (each 4 bytes) ---
		long sequenceInfoOffset = r.readUnsignedInt();
		long programInfoOffset = r.readUnsignedInt();
		long cpiOffset = r.readUnsignedInt();
		long clipMarkOffset = r.readUnsignedInt();
		long extensionOffset = r.readUnsignedInt();
		// 12 reserved bytes after the offsets
		r.skip(12);

		// --- ClipInfo section (always at byte 40) ---
		ClipInfo clipInfo = new ClipInfo();
		long clipInfoSectionLength = r.readUnsignedInt();
		log.debug("sequenceInfoOffset={}, programInfoOffset={}, cpiOffset={}, clipInfoSectionLength={}",
				hex(sequenceInfoOffset), hex(programInfoOffset), hex(cpiOffset), hex(clipInfoSectionLength));
		r.skip(2); // reserved

		int clipStreamType = r.readUnsignedByte();
		clipInfo.setClipStreamType(clipStreamType);
		int applicationType = r.readUnsignedByte();
		clipInfo.setApplicationType(applicationType);

		// 31 reserved bits + 1 is_atc_delta bit
		long flags = r.readUnsignedInt();
		clipInfo.setAtcDelta((flags & 1) != 0);

		long tsRecordingRate = r.readUnsignedInt();
		clipInfo.setTsRecordingRate(tsRecordingRate);
		long numSourcePackets = r.readUnsignedInt();
		clipInfo.setNumSourcePackets(numSourcePackets);

		// Skip the rest of ClipInfo section (128 reserved bytes, ts_type_info, etc.)
		long clipInfoContentRead = 2 + 1 + 1 + 4 + 4 + 4; // 16 bytes read after section
															// length
		r.skip(clipInfoSectionLength - clipInfoContentRead);

		// --- SequenceInfo section ---
		long currentPos = r.getPosition();
		r.skip(sequenceInfoOffset - currentPos);
		readSequenceInfo(r, clipInfo);

		// --- ProgramInfo section ---
		currentPos = r.getPosition();
		r.skip(programInfoOffset - currentPos);
		List<ClipStream> streams = readProgramInfo(r);
		clipInfo.setStreams(streams);

		// --- CPI (EP_map) section ---
		currentPos = r.getPosition();
		r.skip(cpiOffset - currentPos);
		EpMap epMap = readCpi(r, cpiOffset);
		clipInfo.setEpMap(epMap);

		return clipInfo;
	}

	private String hex(long value) {
		return "0x" + Long.toHexString(value);
	}

	// -------------------------------------------------------------------------
	// SequenceInfo
	// -------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private void readSequenceInfo(BinaryReader r, ClipInfo clipInfo) throws IOException {
		long sectionLength = r.readUnsignedInt();
		r.skip(1); // reserved
		int numAtcSeq = r.readUnsignedByte();
		log.debug("readSequenceInfo: numAtcSeq={}", numAtcSeq);

		long firstStart = 0;
		long lastEnd = 0;
		for (int a = 0; a < numAtcSeq; a++) {
			long spnAtcStart = r.readUnsignedInt();
			int numStcSeq = r.readUnsignedByte();
			int offsetStcId = r.readUnsignedByte();
			for (int s = 0; s < numStcSeq; s++) {
				int pcrPid = r.readUnsignedShort();
				long spnStcStart = r.readUnsignedInt();
				long presentationStartTime = r.readUnsignedInt(); // 45 kHz
				long presentationEndTime = r.readUnsignedInt(); // 45 kHz
				log.debug("  stc_seq[{}.{}]: start={}({}s), end={}({}s)", a, s, presentationStartTime,
						presentationStartTime / 45000.0, presentationEndTime, presentationEndTime / 45000.0);
				if (a == 0 && s == 0) {
					firstStart = presentationStartTime;
				}
				lastEnd = presentationEndTime;
			}
		}
		clipInfo.setTsRecordingStartPts(Timestamp.ofTicks(firstStart * 2)); // 45 kHz → 90 kHz
		clipInfo.setTsRecordingEndPts(Timestamp.ofTicks(lastEnd * 2));
		clipInfo.setDuration(Timestamp.ofTicks((lastEnd - firstStart) * 2));
	}

	// -------------------------------------------------------------------------
	// ProgramInfo
	// -------------------------------------------------------------------------

	@SuppressWarnings("unused")
	private List<ClipStream> readProgramInfo(BinaryReader r) throws IOException {
		long sectionLength = r.readUnsignedInt();
		log.debug("readProgramInfo.sectionLength {}. position is {}", hex(sectionLength), hex(r.getPosition()));
		r.skip(1); // reserved
		int numPrograms = r.readUnsignedByte();
		log.debug("readProgramInfo.numPrograms {}. position is {}", hex(numPrograms), hex(r.getPosition()));

		List<ClipStream> all = new ArrayList<>();
		for (int p = 0; p < numPrograms; p++) {
			long spnProgramSequenceBegin = r.readUnsignedInt();
			int programMapPid = r.readUnsignedShort();
			int numStreams = r.readUnsignedByte();
			int numGroups = r.readUnsignedByte();
			log.debug(
					"readProgramInfo.prog {}. spnProgramSequenceBegin is {}. programMapPid is {}. numStreams is {}. numGroups is {}",
					p, spnProgramSequenceBegin, programMapPid, numStreams, numGroups);

			for (int s = 0; s < numStreams; s++) {
				ClipStream stream = new ClipStream();
				stream.setPid(r.readUnsignedShort());
				log.debug("readProgramInfo.stream# {}. PID {}, pos={}", s, hex(stream.getPid()), hex(r.getPosition()));
				int streamInfoSize = r.readUnsignedByte();

				int codingTypeByte = r.readUnsignedByte();
				StreamCodingType codingType;
				try {
					codingType = StreamCodingType.fromByte(codingTypeByte);
				} catch (IllegalArgumentException e) {
					r.skip(streamInfoSize - 1);
					all.add(stream);
					continue;
				}
				stream.setCodingType(codingType);

				if (codingType.isVideo()) {
					int vfr = r.readUnsignedByte();
					stream.setVideoFormat((vfr >> 4) & 0x0F);
					stream.setFrameRate(vfr & 0x0F);
					int ar = r.readUnsignedByte();
					stream.setAspectRatio((ar >> 4) & 0x0F);
					r.skip(streamInfoSize - 3); // reserved
				} else if (codingType.isAudio()) {
					int channelSample = r.readUnsignedByte();
					stream.setAudioChannelLayout((channelSample >> 4) & 0x0F);
					stream.setSampleRate(channelSample & 0x0F);
					stream.setLanguage(r.readAscii(3));
					r.skip(streamInfoSize - 5); // reserved
				} else if (codingType.isSubtitle() || codingType.isMenu()) {
					int offset = 1;
					if (codingType == StreamCodingType.TEXT_SUBTITLE) {
						offset = 0;
						stream.setCharacterCode(r.readUnsignedByte());
					}
					stream.setLanguage(r.readAscii(3));
					r.skip(streamInfoSize - 5 + offset); // reserved
				}

				all.add(stream);
			}
		}
		return all;
	}

	// -------------------------------------------------------------------------
	// CPI (EP_map)
	// -------------------------------------------------------------------------

	private EpMap readCpi(BinaryReader r, long cpiSectionFileOffset) throws IOException {
		long sectionLength = r.readUnsignedInt();
		if (sectionLength == 0)
			return null;

		// 12 reserved bits + 4-bit CPI type
		int typeWord = r.readUnsignedShort();
		int cpiType = typeWord & 0x0F;

		// ep_map_pos: absolute file position right after the type word
		long epMapPos = r.getPosition();
		log.debug("readCpi: sectionLength={}, cpiType={}, epMapPos={}", hex(sectionLength), cpiType, hex(epMapPos));

		r.skip(1); // reserved
		int numStreamPidEntries = r.readUnsignedByte();
		log.debug("readCpi: numStreamPidEntries={}", numStreamPidEntries);

		// Read stream headers
		int[] pids = new int[numStreamPidEntries];
		int[] epStreamTypes = new int[numStreamPidEntries];
		int[] numCoarseArr = new int[numStreamPidEntries];
		int[] numFineArr = new int[numStreamPidEntries];
		long[] epStreamStartAddrs = new long[numStreamPidEntries];

		for (int i = 0; i < numStreamPidEntries; i++) {
			pids[i] = r.readUnsignedShort();
			// 10 reserved + 4 ep_stream_type + 16 num_coarse + 2 high bits num_fine = 32
			// bits
			long block1 = r.readUnsignedInt();
			epStreamTypes[i] = (int) ((block1 >> 18) & 0x0F);
			numCoarseArr[i] = (int) ((block1 >> 2) & 0xFFFF);
			int numFineHigh = (int) (block1 & 0x3);
			// 16 low bits of num_fine
			int numFineLow = r.readUnsignedShort();
			numFineArr[i] = (numFineHigh << 16) | numFineLow;
			// ep_map_stream_start_addr (relative to ep_map_pos)
			long relAddr = r.readUnsignedInt();
			epStreamStartAddrs[i] = relAddr + epMapPos;
			log.debug("CPI stream[{}]: pid={}, type={}, coarse={}, fine={}, addr={}", i, hex(pids[i]), epStreamTypes[i],
					numCoarseArr[i], numFineArr[i], hex(epStreamStartAddrs[i]));
		}

		// Parse each stream's coarse/fine entries
		List<EpMap.EpMapStream> epStreams = new ArrayList<>();
		for (int i = 0; i < numStreamPidEntries; i++) {
			EpMap.EpMapStream epStream = new EpMap.EpMapStream();
			epStream.setPid(pids[i]);
			epStream.setEpType(epStreamTypes[i]);

			// Seek to stream start
			long currentPos = r.getPosition();
			r.skip(epStreamStartAddrs[i] - currentPos);

			long fineStartOffset = r.readUnsignedInt();

			// Read coarse entries (8 bytes each: 18+14 bits = 32 bits + 32 bits SPN)
			int[][] coarseData = new int[numCoarseArr[i]][3]; // [refFineId, ptsCoarse,
																// spnCoarse]
			for (int c = 0; c < numCoarseArr[i]; c++) {
				long cBlock = r.readUnsignedInt();
				coarseData[c][0] = (int) ((cBlock >> 14) & 0x3FFFF); // ref_ep_fine_id
				coarseData[c][1] = (int) (cBlock & 0x3FFF); // pts_ep (14 bits)
				coarseData[c][2] = (int) r.readUnsignedInt(); // spn_ep (32 bits)
			}

			// Seek to fine entries
			long fineAbsOffset = epStreamStartAddrs[i] + fineStartOffset;
			currentPos = r.getPosition();
			r.skip(fineAbsOffset - currentPos);

			// Read fine entries (4 bytes each: 1+3+11+17 = 32 bits)
			List<EpMap.EpMapEntry> entries = new ArrayList<>();
			int coarseIdx = 0;
			for (int f = 0; f < numFineArr[i]; f++) {
				// Advance to the correct coarse entry for this fine entry
				while (coarseIdx + 1 < numCoarseArr[i] && coarseData[coarseIdx + 1][0] <= f) {
					coarseIdx++;
				}

				long fBlock = r.readUnsignedInt();
				boolean isAngleChange = ((fBlock >> 31) & 1) != 0;
				int iEndPosOffset = (int) ((fBlock >> 28) & 0x07);
				int ptsEpFine = (int) ((fBlock >> 17) & 0x7FF);
				int spnEpFine = (int) (fBlock & 0x1FFFF);

				// Reconstruct full PTS (45 kHz) and SPN
				int coarsePts = coarseData[coarseIdx][1];
				int coarseSpn = coarseData[coarseIdx][2];
				long fullPts45 = ((long) (coarsePts & ~1) << 18) + ((long) ptsEpFine << 8);
				long fullSpn = ((long) coarseSpn & 0xFFFE0000L) + spnEpFine;

				EpMap.EpMapEntry entry = new EpMap.EpMapEntry();
				entry.setPtsTicks(fullPts45 * 2); // 45 kHz → 90 kHz
				entry.setSpn(fullSpn);
				entry.setAngleChangePoint(isAngleChange);
				entry.setIEndPositionOffset(iEndPosOffset);
				entries.add(entry);
			}
			epStream.setEntries(entries);
			epStreams.add(epStream);
		}

		EpMap epMap = new EpMap();
		epMap.setStreams(epStreams);
		return epMap;
	}

}
