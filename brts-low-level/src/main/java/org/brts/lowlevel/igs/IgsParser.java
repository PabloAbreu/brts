package org.brts.lowlevel.igs;

import org.brts.lowlevel.bdmv.ParsedNavigationCommand;
import org.brts.lowlevel.igs.model.*;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a raw IGS elementary stream file (PES payload bytes, as extracted by
 * {@link org.brts.common.m2ts.FilePacketHandler}) into a list of {@link IgsRawSegment}s grouped into
 * {@link IgsDisplaySet}s.
 * <p>
 * The IGS elementary stream is a sequence of PES packets. Each PES packet contains:
 * <ol>
 * <li>A PES header with PTS (and optionally DTS).</li>
 * <li>One segment: 1-byte type + 2-byte big-endian length + data.</li>
 * </ol>
 * <p>
 * Note: the {@link org.brts.common.m2ts.FilePacketHandler} already strips the PES header, so the raw .igs file is a
 * concatenation of bare segments (type + length + data) <strong>without PTS information</strong>. If PTS values are
 * needed, the caller should use the PES-aware overload or supply a separate PTS mapping.
 *
 * <h2>PES-level parsing</h2> For the round-trip use-case we also provide a method that accepts the raw PES-encapsulated
 * bytes (before PES header stripping) so we can preserve PTS.
 */
public class IgsParser {

	private static final Logger log = LoggerFactory.getLogger(IgsParser.class);

	// -------------------------------------------------------------------------
	// Parse raw segment stream (PES-stripped .igs file)
	// -------------------------------------------------------------------------

	/**
	 * Parses an IGS elementary stream file (bare segments, no PES headers) into raw segments. PTS on each segment will
	 * be {@code -1}.
	 *
	 * @param igsFile path to the raw .igs file produced by m2ts-extract
	 * @return ordered list of raw segments
	 * @throws IOException on I/O error
	 */
	public List<IgsRawSegment> parseSegments(Path igsFile) throws IOException {
		byte[] data = Files.readAllBytes(igsFile);
		return parseSegments(data);
	}

	/**
	 * Parses bare segment bytes (no PES headers) into raw segments.
	 *
	 * @param data raw segment bytes
	 * @return ordered list of raw segments
	 */
	public List<IgsRawSegment> parseSegments(byte[] data) {
		List<IgsRawSegment> segments = new ArrayList<>();
		int pos = 0;
		while (pos + 3 <= data.length) {
			int typeByte = data[pos] & 0xFF;
			int segLen = ((data[pos + 1] & 0xFF) << 8) | (data[pos + 2] & 0xFF);
			pos += 3;

			if (pos + segLen > data.length) {
				log.warn("Truncated segment at offset {}: type=0x{}, declared len={}, remaining={}", pos - 3,
						Integer.toHexString(typeByte), segLen, data.length - pos);
				break;
			}

			IgsSegmentType type = IgsSegmentType.fromByte(typeByte);
			if (type == null) {
				log.warn("Unknown segment type 0x{} at offset {}, skipping {} bytes", Integer.toHexString(typeByte),
						pos - 3, segLen);
				pos += segLen;
				continue;
			}

			byte[] segData = new byte[segLen];
			System.arraycopy(data, pos, segData, 0, segLen);
			pos += segLen;

			IgsRawSegment seg = new IgsRawSegment();
			seg.setPts(-1);
			seg.setDts(-1);
			seg.setType(type);
			seg.setSegmentData(segData);
			segments.add(seg);
		}
		log.info("Parsed {} IGS segments from {} bytes", segments.size(), data.length);
		return segments;
	}

	// -------------------------------------------------------------------------
	// Group segments into display sets
	// -------------------------------------------------------------------------

	/**
	 * Groups raw segments into display sets. A new display set begins at each ICS (IG_COMPOSITION) segment. The display
	 * set is marked complete when an END_OF_DISPLAY segment is encountered.
	 *
	 * @param segments ordered list of raw segments
	 * @return ordered list of display sets
	 */
	public List<IgsDisplaySet> groupIntoDisplaySets(List<IgsRawSegment> segments) {
		List<IgsDisplaySet> sets = new ArrayList<>();
		IgsDisplaySet current = null;

		for (IgsRawSegment seg : segments) {
			if (seg.getType() == IgsSegmentType.IG_COMPOSITION) {
				current = new IgsDisplaySet();
				sets.add(current);
			}
			if (current == null) {
				log.warn("Segment type {} before first ICS, skipping", seg.getType());
				continue;
			}

			switch (seg.getType()) {
			case IG_COMPOSITION -> {
				IgsCompositionSegment ics = decodeIcs(seg);
				current.setCompositionSegment(ics);
				if (ics.getCompositionDescriptor() != null && ics.getCompositionDescriptor().getState() == 2) {
					current.setEpochStart(true);
				}
			}
			case PALETTE_DEFINITION -> current.getPalettes().add(decodePalette(seg));
			case OBJECT_DEFINITION -> current.getObjects().add(decodeObject(seg));
			case WINDOW_DEFINITION -> current.getWindowDefinitions().add(decodeWindowDef(seg));
			case END_OF_DISPLAY -> current.setComplete(true);
			default -> log.warn("Unexpected segment type {} in display set", seg.getType());
			}
		}

		log.info("Grouped into {} display sets", sets.size());
		return sets;
	}

	// -------------------------------------------------------------------------
	// Segment decoders
	// -------------------------------------------------------------------------

	/**
	 * Decodes a Palette Definition Segment.
	 */
	public IgsPalette decodePalette(IgsRawSegment seg) {
		byte[] d = seg.getSegmentData();
		IgsPalette pal = new IgsPalette();
		pal.setPts(seg.getPts());
		if (d.length < 2)
			return pal;

		pal.setId(d[0] & 0xFF);
		pal.setVersion(d[1] & 0xFF);

		int pos = 2;
		while (pos + 5 <= d.length) {
			PaletteEntry e = new PaletteEntry();
			e.setEntryId(d[pos] & 0xFF);
			e.setY(d[pos + 1] & 0xFF);
			e.setCr(d[pos + 2] & 0xFF);
			e.setCb(d[pos + 3] & 0xFF);
			e.setAlpha(d[pos + 4] & 0xFF);
			pal.getEntries().add(e);
			pos += 5;
		}
		return pal;
	}

	/**
	 * Decodes an Object Definition Segment (first fragment or single-segment).
	 */
	public IgsObject decodeObject(IgsRawSegment seg) {
		byte[] d = seg.getSegmentData();
		IgsObject obj = new IgsObject();
		obj.setPts(seg.getPts());

		if (d.length < 4)
			return obj;

		obj.setId(((d[0] & 0xFF) << 8) | (d[1] & 0xFF));
		obj.setVersion(d[2] & 0xFF);

		SequenceDescriptor sd = new SequenceDescriptor();
		int seqByte = d[3] & 0xFF;
		sd.setFirstInSequence((seqByte & 0x80) != 0);
		sd.setLastInSequence((seqByte & 0x40) != 0);
		obj.setSequenceDescriptor(sd);

		int pos = 4;
		if (sd.isFirstInSequence() && d.length >= 11) {
			// First-in-sequence carries: dataLength(24), width(16), height(16)
			obj.setDataLength(((d[pos] & 0xFF) << 16) | ((d[pos + 1] & 0xFF) << 8) | (d[pos + 2] & 0xFF));
			pos += 3;
			obj.setWidth(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
			pos += 2;
			obj.setHeight(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
			pos += 2;
		}

		// Remaining bytes are RLE data
		if (pos < d.length) {
			byte[] rle = new byte[d.length - pos];
			System.arraycopy(d, pos, rle, 0, rle.length);
			obj.setRleData(rle);
		} else {
			obj.setRleData(new byte[0]);
		}

		return obj;
	}

	/**
	 * Decodes a Window Definition Segment.
	 */
	public IgsWindowDefinition decodeWindowDef(IgsRawSegment seg) {
		byte[] d = seg.getSegmentData();
		IgsWindowDefinition wds = new IgsWindowDefinition();
		wds.setPts(seg.getPts());

		if (d.length < 1)
			return wds;

		int numWindows = d[0] & 0xFF;
		int pos = 1;
		for (int i = 0; i < numWindows && pos + 9 <= d.length; i++) {
			IgsWindow w = new IgsWindow();
			w.setId(d[pos] & 0xFF);
			w.setX(((d[pos + 1] & 0xFF) << 8) | (d[pos + 2] & 0xFF));
			w.setY(((d[pos + 3] & 0xFF) << 8) | (d[pos + 4] & 0xFF));
			w.setWidth(((d[pos + 5] & 0xFF) << 8) | (d[pos + 6] & 0xFF));
			w.setHeight(((d[pos + 7] & 0xFF) << 8) | (d[pos + 8] & 0xFF));
			wds.getWindows().add(w);
			pos += 9;
		}
		return wds;
	}

	/**
	 * Decodes an Interactive Composition Segment (ICS) into its full model.
	 */
	public IgsCompositionSegment decodeIcs(IgsRawSegment seg) {
		byte[] d = seg.getSegmentData();
		IgsCompositionSegment ics = new IgsCompositionSegment();
		ics.setPts(seg.getPts());
		ics.setDts(seg.getDts());

		if (d.length < 7)
			return ics;

		int pos = 0;

		// Video descriptor: width(16), height(16), frameRateCode(4), skip(4)
		VideoDescriptor vd = new VideoDescriptor();
		vd.setWidth(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		vd.setHeight(((d[pos + 2] & 0xFF) << 8) | (d[pos + 3] & 0xFF));
		vd.setFrameRateCode((d[pos + 4] & 0xFF) >> 4);
		ics.setVideoDescriptor(vd);
		pos += 5;

		// Composition descriptor: number(16), state(2), skip(6)
		CompositionDescriptor cd = new CompositionDescriptor();
		cd.setNumber(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		cd.setState((d[pos + 2] & 0xFF) >> 6);
		ics.setCompositionDescriptor(cd);
		pos += 3;

		// Sequence descriptor
		if (pos >= d.length)
			return ics;
		SequenceDescriptor sd = new SequenceDescriptor();
		int seqByte = d[pos] & 0xFF;
		sd.setFirstInSequence((seqByte & 0x80) != 0);
		sd.setLastInSequence((seqByte & 0x40) != 0);
		ics.setSequenceDescriptor(sd);
		pos++;

		// Interactive composition data
		if (pos + 3 > d.length)
			return ics;
		ics.setInteractiveComposition(decodeInteractiveComposition(d, pos));

		return ics;
	}

	// -------------------------------------------------------------------------
	// Interactive composition sub-decoders
	// -------------------------------------------------------------------------

	private IgsInteractiveComposition decodeInteractiveComposition(byte[] d, int startPos) {
		IgsInteractiveComposition ic = new IgsInteractiveComposition();
		int pos = startPos;

		// data_len (24 bits) — we read but don't enforce boundary
		// int dataLen = ((d[pos] & 0xFF) << 16) | ((d[pos+1] & 0xFF) << 8) | (d[pos+2] &
		// 0xFF);
		pos += 3;

		if (pos >= d.length)
			return ic;

		// stream_model (1 bit), ui_model (1 bit), skip (6 bits)
		int flags = d[pos] & 0xFF;
		ic.setStreamModel((flags >> 7) & 1);
		ic.setUiModel((flags >> 6) & 1);
		pos++;

		// If streamModel == 0: skip(7) + composition_timeout_pts(33),
		// skip(7) + selection_timeout_pts(33)
		if (ic.getStreamModel() == IgsInteractiveComposition.STREAM_MODEL_IN_MUX) {
			if (pos + 10 > d.length)
				return ic;
			// composition_timeout_pts: 5 bytes (skip 7 bits, then 33-bit PTS)
			long ctPts = readPts33(d, pos);
			ic.setCompositionTimeoutPts(ctPts);
			pos += 5;
			long stPts = readPts33(d, pos);
			ic.setSelectionTimeoutPts(stPts);
			pos += 5;
		}

		if (pos + 3 > d.length)
			return ic;

		// user_timeout_duration (24 bits)
		ic.setUserTimeoutDuration(((d[pos] & 0xFF) << 16) | ((d[pos + 1] & 0xFF) << 8) | (d[pos + 2] & 0xFF));
		pos += 3;

		if (pos >= d.length)
			return ic;

		// num_pages (8 bits)
		int numPages = d[pos] & 0xFF;
		pos++;

		for (int i = 0; i < numPages && pos < d.length; i++) {
			int[] newPos = new int[] { pos };
			IgsPage page = decodePage(d, newPos);
			ic.getPages().add(page);
			pos = newPos[0];
		}

		return ic;
	}

	private IgsPage decodePage(byte[] d, int[] posRef) {
		int pos = posRef[0];
		IgsPage page = new IgsPage();

		if (pos + 2 > d.length) {
			posRef[0] = d.length;
			return page;
		}

		page.setId(d[pos] & 0xFF);
		page.setVersion(d[pos + 1] & 0xFF);
		pos += 2;

		// UO mask table (8 bytes)
		if (pos + 8 > d.length) {
			posRef[0] = d.length;
			return page;
		}
		byte[] uo = new byte[8];
		System.arraycopy(d, pos, uo, 0, 8);
		page.setUoMaskTable(uo);
		pos += 8;

		// In-effects
		int[] p = new int[] { pos };
		page.setInEffects(decodeEffectSequence(d, p));
		pos = p[0];

		// Out-effects
		p[0] = pos;
		page.setOutEffects(decodeEffectSequence(d, p));
		pos = p[0];

		if (pos + 4 > d.length) {
			posRef[0] = pos;
			return page;
		}

		page.setAnimationFrameRateCode(d[pos] & 0xFF);
		pos++;

		page.setDefaultSelectedButtonIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		page.setDefaultActivatedButtonIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;

		if (pos >= d.length) {
			posRef[0] = pos;
			return page;
		}

		page.setPaletteIdRef(d[pos] & 0xFF);
		pos++;

		if (pos >= d.length) {
			posRef[0] = pos;
			return page;
		}

		int numBogs = d[pos] & 0xFF;
		pos++;

		for (int i = 0; i < numBogs && pos < d.length; i++) {
			p[0] = pos;
			IgsBog bog = decodeBog(d, p);
			page.getBogs().add(bog);
			pos = p[0];
		}

		posRef[0] = pos;
		return page;
	}

	private IgsBog decodeBog(byte[] d, int[] posRef) {
		int pos = posRef[0];
		IgsBog bog = new IgsBog();

		if (pos + 3 > d.length) {
			posRef[0] = d.length;
			return bog;
		}

		bog.setDefaultValidButtonIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;

		int numButtons = d[pos] & 0xFF;
		pos++;

		for (int i = 0; i < numButtons && pos < d.length; i++) {
			int[] p = new int[] { pos };
			IgsButton btn = decodeButton(d, p);
			bog.getButtons().add(btn);
			pos = p[0];
		}

		posRef[0] = pos;
		return bog;
	}

	private IgsButton decodeButton(byte[] d, int[] posRef) {
		int pos = posRef[0];
		IgsButton btn = new IgsButton();

		// Minimum button size: 2+2+1+2+2+2+2+2+2 + normal(2+2+1) + selected(1+2+2+1) +
		// activated(1+2+2) + numNavCmds(2) = 35 bytes
		if (pos + 35 > d.length) {
			posRef[0] = d.length;
			return btn;
		}

		btn.setId(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setNumericSelectValue(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;

		int flagByte = d[pos] & 0xFF;
		btn.setAutoAction((flagByte & 0x80) != 0);
		pos++;

		btn.setXPos(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setYPos(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;

		// Neighbour refs
		btn.setUpperButtonIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setLowerButtonIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setLeftButtonIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setRightButtonIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;

		// Normal state: start_object_id_ref(16), end_object_id_ref(16), repeat(1)+skip(7)
		btn.setNormalStartObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setNormalEndObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setNormalRepeatFlag((d[pos] & 0x80) != 0);
		pos++;

		// Selected state: sound_id_ref(8), start_object_id_ref(16),
		// end_object_id_ref(16), repeat(1)+skip(7)
		btn.setSelectedSoundIdRef(d[pos] & 0xFF);
		pos++;
		btn.setSelectedStartObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setSelectedEndObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setSelectedRepeatFlag((d[pos] & 0x80) != 0);
		pos++;

		// Activated state: sound_id_ref(8), start_object_id_ref(16),
		// end_object_id_ref(16)
		btn.setActivatedSoundIdRef(d[pos] & 0xFF);
		pos++;
		btn.setActivatedStartObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		btn.setActivatedEndObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;

		// Navigation commands
		if (pos + 2 > d.length) {
			posRef[0] = pos;
			return btn;
		}
		int numNavCmds = ((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF);
		pos += 2;

		for (int i = 0; i < numNavCmds && pos + 12 <= d.length; i++) {
			byte[] raw = new byte[12];
			System.arraycopy(d, pos, raw, 0, 12);
			btn.getNavigationCommands().add(NavigationCommand.fromParsed(ParsedNavigationCommand.fromRaw(raw)));
			pos += 12;
		}

		posRef[0] = pos;
		return btn;
	}

	private IgsEffectSequence decodeEffectSequence(byte[] d, int[] posRef) {
		int pos = posRef[0];
		IgsEffectSequence es = new IgsEffectSequence();

		if (pos >= d.length) {
			posRef[0] = pos;
			return es;
		}

		// num_windows(8)
		int numWindows = d[pos] & 0xFF;
		pos++;

		for (int i = 0; i < numWindows && pos + 9 <= d.length; i++) {
			IgsWindow w = new IgsWindow();
			w.setId(d[pos] & 0xFF);
			w.setX(((d[pos + 1] & 0xFF) << 8) | (d[pos + 2] & 0xFF));
			w.setY(((d[pos + 3] & 0xFF) << 8) | (d[pos + 4] & 0xFF));
			w.setWidth(((d[pos + 5] & 0xFF) << 8) | (d[pos + 6] & 0xFF));
			w.setHeight(((d[pos + 7] & 0xFF) << 8) | (d[pos + 8] & 0xFF));
			es.getWindows().add(w);
			pos += 9;
		}

		if (pos >= d.length) {
			posRef[0] = pos;
			return es;
		}

		// num_effects(8)
		int numEffects = d[pos] & 0xFF;
		pos++;

		for (int i = 0; i < numEffects && pos < d.length; i++) {
			int[] p = new int[] { pos };
			IgsEffect effect = decodeEffect(d, p);
			es.getEffects().add(effect);
			pos = p[0];
		}

		posRef[0] = pos;
		return es;
	}

	private IgsEffect decodeEffect(byte[] d, int[] posRef) {
		int pos = posRef[0];
		IgsEffect effect = new IgsEffect();

		if (pos + 5 > d.length) {
			posRef[0] = d.length;
			return effect;
		}

		// duration (24 bits)
		effect.setDuration(((d[pos] & 0xFF) << 16) | ((d[pos + 1] & 0xFF) << 8) | (d[pos + 2] & 0xFF));
		pos += 3;

		effect.setPaletteIdRef(d[pos] & 0xFF);
		pos++;

		int numCo = d[pos] & 0xFF;
		pos++;

		for (int i = 0; i < numCo && pos < d.length; i++) {
			int[] p = new int[] { pos };
			CompositionObject co = decodeCompositionObject(d, p);
			effect.getCompositionObjects().add(co);
			pos = p[0];
		}

		posRef[0] = pos;
		return effect;
	}

	private CompositionObject decodeCompositionObject(byte[] d, int[] posRef) {
		int pos = posRef[0];
		CompositionObject co = new CompositionObject();

		if (pos + 8 > d.length) {
			posRef[0] = d.length;
			return co;
		}

		co.setObjectIdRef(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		co.setWindowIdRef(d[pos] & 0xFF);
		pos++;

		int flags = d[pos] & 0xFF;
		co.setForcedOn((flags & 0x40) != 0);
		co.setCropFlag((flags & 0x80) != 0);
		pos++;

		co.setX(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;
		co.setY(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
		pos += 2;

		if (co.isCropFlag() && pos + 8 <= d.length) {
			co.setCropX(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
			pos += 2;
			co.setCropY(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
			pos += 2;
			co.setCropWidth(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
			pos += 2;
			co.setCropHeight(((d[pos] & 0xFF) << 8) | (d[pos + 1] & 0xFF));
			pos += 2;
		}

		posRef[0] = pos;
		return co;
	}

	/**
	 * Reads a 33-bit PTS value packed as: skip(7) + 1-bit marker + 32-bit value. Format: byte0[bit0] << 32 |
	 * byte1..byte4
	 */
	private long readPts33(byte[] d, int pos) {
		long hi = d[pos] & 0x01; // bit 0 of first byte
		long lo = ((long) (d[pos + 1] & 0xFF) << 24) | ((long) (d[pos + 2] & 0xFF) << 16)
				| ((long) (d[pos + 3] & 0xFF) << 8) | (d[pos + 4] & 0xFF);
		return (hi << 32) | lo;
	}

}
