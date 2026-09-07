package org.brts.lowlevel.igs;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.igs.model.CompositionDescriptor;
import org.brts.lowlevel.igs.model.CompositionObject;
import org.brts.lowlevel.igs.model.IgsBog;
import org.brts.lowlevel.igs.model.IgsButton;
import org.brts.lowlevel.igs.model.IgsCompositionSegment;
import org.brts.lowlevel.igs.model.IgsDisplaySet;
import org.brts.lowlevel.igs.model.IgsEffect;
import org.brts.lowlevel.igs.model.IgsEffectSequence;
import org.brts.lowlevel.igs.model.IgsInteractiveComposition;
import org.brts.lowlevel.igs.model.IgsObject;
import org.brts.lowlevel.igs.model.IgsPage;
import org.brts.lowlevel.igs.model.IgsPalette;
import org.brts.lowlevel.igs.model.IgsWindow;
import org.brts.lowlevel.igs.model.IgsWindowDefinition;
import org.brts.lowlevel.igs.model.SequenceDescriptor;
import org.brts.lowlevel.igs.model.VideoDescriptor;
import org.brts.lowlevel.model.bdmv.MovieObjects.NavigationCommand;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * IGS Muxer — reassembles extracted IGS resources (from {@link IgsDemuxer}) back into a single raw IGS elementary
 * stream file.
 * <p>
 * The muxer reads the manifest and per-display-set JSON + RLE files, re-encodes each segment in binary form, and
 * concatenates them into a single {@code .igs} file identical to what {@link org.brts.common.m2ts.FilePacketHandler}
 * would produce during extraction.
 *
 * <h2>Segment ordering per display set</h2>
 * <ol>
 * <li>ICS (Interactive Composition Segment)</li>
 * <li>PDS (Palette Definition Segments)</li>
 * <li>WDS (Window Definition Segments)</li>
 * <li>ODS (Object Definition Segments)</li>
 * <li>END (End of Display)</li>
 * </ol>
 */
@Slf4j
public class IgsMuxer {
	private final ObjectMapper mapper = JsonMapperFactory.get();

	/**
	 * Muxes the extracted IGS folder back into a single raw .igs file.
	 *
	 * @param inputDir   directory created by {@link IgsDemuxer} containing {@code igs_manifest.json} and
	 *                   per-display-set sub-folders
	 * @param outputFile target .igs file path
	 * @throws IOException on I/O error
	 */
	public void mux(Path inputDir, Path outputFile) throws IOException {
		Path manifestPath = inputDir.resolve("igs_manifest.json");
		IgsDemuxer.IgsManifest manifest = mapper.readValue(manifestPath.toFile(), IgsDemuxer.IgsManifest.class);

		Files.createDirectories(outputFile.getParent());

		try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputFile))) {

			for (IgsDemuxer.IgsManifest.DisplaySetRef dsRef : manifest.getDisplaySets()) {
				Path dsDir = inputDir.resolve(dsRef.getDirectory());
				Path dsJsonPath = dsDir.resolve("display_set.json");

				IgsDemuxer.DisplaySetJson dsJson = mapper.readValue(dsJsonPath.toFile(),
						IgsDemuxer.DisplaySetJson.class);

				// 1. Write ICS
				if (dsJson.getCompositionSegment() != null) {
					byte[] icsData = encodeIcs(dsJson.getCompositionSegment());
					writeSegment(out, IgsSegmentType.IG_COMPOSITION, icsData);
				}

				// 2. Write PDS
				if (dsJson.getPalettes() != null) {
					for (IgsPalette pal : dsJson.getPalettes()) {
						byte[] pdsData = encodePalette(pal);
						writeSegment(out, IgsSegmentType.PALETTE_DEFINITION, pdsData);
					}
				}

				// 3. Write WDS
				if (dsJson.getWindowDefinitions() != null) {
					for (IgsWindowDefinition wds : dsJson.getWindowDefinitions()) {
						byte[] wdsData = encodeWindowDef(wds);
						writeSegment(out, IgsSegmentType.WINDOW_DEFINITION, wdsData);
					}
				}

				// 4. Write ODS
				if (dsJson.getObjects() != null) {
					for (IgsDemuxer.ObjectMetadata meta : dsJson.getObjects()) {
						byte[] rleData = new byte[0];
						if (meta.getRleFile() != null) {
							Path rlePath = dsDir.resolve(meta.getRleFile());
							if (Files.exists(rlePath)) {
								rleData = Files.readAllBytes(rlePath);
							}
						}
						byte[] odsData = encodeObject(meta, rleData);
						writeSegment(out, IgsSegmentType.OBJECT_DEFINITION, odsData);
					}
				}

				// 5. Write END_OF_DISPLAY
				writeSegment(out, IgsSegmentType.END_OF_DISPLAY, new byte[0]);
			}
		}

		log.info("IGS muxed: {} → {}", inputDir, outputFile);
	}

	/**
	 * Encodes a fully populated {@link IgsDisplaySet} (built in memory) into raw IGS elementary stream bytes.
	 * <p>
	 * Segment order follows the Blu-ray spec: ICS → PDS → WDS → ODS → END_OF_DISPLAY.
	 *
	 * @param displaySet the in-memory display set to encode
	 * @return raw IGS ES bytes ready to be muxed into an M2TS
	 * @throws IOException on encoding error
	 */
	public byte[] encodeDisplaySet(IgsDisplaySet displaySet) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// 1. ICS
		if (displaySet.getCompositionSegment() != null) {
			byte[] icsData = encodeIcs(displaySet.getCompositionSegment());
			writeSegment(out, IgsSegmentType.IG_COMPOSITION, icsData);
		}

		// 2. PDS
		if (displaySet.getPalettes() != null) {
			for (IgsPalette pal : displaySet.getPalettes()) {
				byte[] pdsData = encodePalette(pal);
				writeSegment(out, IgsSegmentType.PALETTE_DEFINITION, pdsData);
			}
		}

		// 3. WDS
		if (displaySet.getWindowDefinitions() != null) {
			for (IgsWindowDefinition wds : displaySet.getWindowDefinitions()) {
				byte[] wdsData = encodeWindowDef(wds);
				writeSegment(out, IgsSegmentType.WINDOW_DEFINITION, wdsData);
			}
		}

		// 4. ODS
		if (displaySet.getObjects() != null) {
			for (IgsObject obj : displaySet.getObjects()) {
				byte[] odsData = encodeObject(obj);
				writeSegment(out, IgsSegmentType.OBJECT_DEFINITION, odsData);
			}
		}

		// 5. END_OF_DISPLAY
		writeSegment(out, IgsSegmentType.END_OF_DISPLAY, new byte[0]);

		return out.toByteArray();
	}

	/**
	 * Encodes an Object Definition Segment body from an in-memory {@link IgsObject}.
	 */
	public byte[] encodeObject(IgsObject obj) {
		return IgsPgsCodec.encodeObject(obj);
	}

	// -------------------------------------------------------------------------
	// Segment encoders
	// -------------------------------------------------------------------------

	/**
	 * Writes a segment: 1-byte type + 2-byte BE length + data.
	 */
	private void writeSegment(OutputStream out, IgsSegmentType type, byte[] data) throws IOException {
		log.debug("Writing segment: type={}, length={}", type, data.length);
		out.write(type.getCode());
		out.write((data.length >> 8) & 0xFF);
		out.write(data.length & 0xFF);
		out.write(data);
	}

	/**
	 * Encodes a Palette Definition Segment body.
	 */
	public byte[] encodePalette(IgsPalette pal) {
		return IgsPgsCodec.encodePalette(pal);
	}

	/**
	 * Encodes an Object Definition Segment body.
	 */
	public byte[] encodeObject(IgsDemuxer.ObjectMetadata meta, byte[] rleData) {
		boolean firstInSeq = meta.getSequenceDescriptor() != null && meta.getSequenceDescriptor().isFirstInSequence();
		boolean lastInSeq = meta.getSequenceDescriptor() != null && meta.getSequenceDescriptor().isLastInSequence();

		int headerSize = 4; // id(16) + version(8) + seq_desc(8)
		if (firstInSeq) {
			headerSize += 7; // dataLength(24) + width(16) + height(16)
		}

		byte[] d = new byte[headerSize + rleData.length];
		int pos = 0;

		// id (16 bits)
		d[pos++] = (byte) ((meta.getId() >> 8) & 0xFF);
		d[pos++] = (byte) (meta.getId() & 0xFF);
		// version (8 bits)
		d[pos++] = (byte) meta.getVersion();
		// sequence descriptor (8 bits)
		int seqByte = 0;
		if (firstInSeq)
			seqByte |= 0x80;
		if (lastInSeq)
			seqByte |= 0x40;
		d[pos++] = (byte) seqByte;

		if (firstInSeq) {
			// dataLength (24 bits)
			d[pos++] = (byte) ((meta.getDataLength() >> 16) & 0xFF);
			d[pos++] = (byte) ((meta.getDataLength() >> 8) & 0xFF);
			d[pos++] = (byte) (meta.getDataLength() & 0xFF);
			// width (16 bits)
			d[pos++] = (byte) ((meta.getWidth() >> 8) & 0xFF);
			d[pos++] = (byte) (meta.getWidth() & 0xFF);
			// height (16 bits)
			d[pos++] = (byte) ((meta.getHeight() >> 8) & 0xFF);
			d[pos++] = (byte) (meta.getHeight() & 0xFF);
		}

		System.arraycopy(rleData, 0, d, pos, rleData.length);
		return d;
	}

	/**
	 * Encodes a Window Definition Segment body.
	 */
	public byte[] encodeWindowDef(IgsWindowDefinition wds) {
		return IgsPgsCodec.encodeWindowDef(wds);
	}

	/**
	 * Encodes an Interactive Composition Segment body.
	 */
	public byte[] encodeIcs(IgsCompositionSegment ics) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();

		// Video descriptor: width(16), height(16), frameRateCode(4)+skip(4)
		VideoDescriptor vd = ics.getVideoDescriptor();
		writeU16(buf, vd.getWidth());
		writeU16(buf, vd.getHeight());
		buf.write((vd.getFrameRateCode() << 4) & 0xF0);

		// Composition descriptor: number(16), state(2)+skip(6)
		CompositionDescriptor cd = ics.getCompositionDescriptor();
		writeU16(buf, cd.getNumber());
		buf.write((cd.getState() << 6) & 0xC0);

		// Sequence descriptor
		SequenceDescriptor sd = ics.getSequenceDescriptor();
		int seqByte = 0;
		if (sd != null) {
			if (sd.isFirstInSequence())
				seqByte |= 0x80;
			if (sd.isLastInSequence())
				seqByte |= 0x40;
		}
		buf.write(seqByte);

		// Interactive composition
		byte[] icData = encodeInteractiveComposition(ics.getInteractiveComposition());
		buf.write(icData);

		return buf.toByteArray();
	}

	private byte[] encodeInteractiveComposition(IgsInteractiveComposition ic) throws IOException {
		ByteArrayOutputStream icBuf = new ByteArrayOutputStream();

		// bits : stream_model(1), ui_model(1), skip(6)
		icBuf.write(((ic.getStreamModel() & 1) << 7) | ((ic.getUiModel() & 1) << 6));

		if (ic.getStreamModel() == IgsInteractiveComposition.STREAM_MODEL_IN_MUX) {
			// composition_timeout_pts: skip(7) + 33-bit PTS
			writePts33(icBuf, ic.getCompositionTimeoutPts());
			writePts33(icBuf, ic.getSelectionTimeoutPts());
		}

		// user_timeout_duration (24 bits)
		writeU24(icBuf, ic.getUserTimeoutDuration());

		// num_pages (8 bits)
		icBuf.write(ic.getPages().size());

		for (IgsPage page : ic.getPages()) {
			encodePage(icBuf, page);
		}

		byte[] icData = icBuf.toByteArray();

		// Wrap with data_len (24 bits) prefix
		ByteArrayOutputStream wrapped = new ByteArrayOutputStream();
		writeU24(wrapped, icData.length);
		wrapped.write(icData);
		return wrapped.toByteArray();
	}

	private void encodePage(ByteArrayOutputStream buf, IgsPage page) throws IOException {
		buf.write(page.getId());
		buf.write(page.getVersion());

		// UO mask table (8 bytes)
		buf.write(page.getUoMaskTable());

		// In-effects
		encodeEffectSequence(buf, page.getInEffects());

		// Out-effects
		encodeEffectSequence(buf, page.getOutEffects());

		buf.write(page.getAnimationFrameRateCode());

		writeU16(buf, page.getDefaultSelectedButtonIdRef());
		writeU16(buf, page.getDefaultActivatedButtonIdRef());

		buf.write(page.getPaletteIdRef());

		// num_bogs (8 bits)
		buf.write(page.getBogs().size());

		for (IgsBog bog : page.getBogs()) {
			encodeBog(buf, bog);
		}
	}

	private void encodeBog(ByteArrayOutputStream buf, IgsBog bog) throws IOException {
		writeU16(buf, bog.getDefaultValidButtonIdRef());
		buf.write(bog.getButtons().size());

		for (IgsButton btn : bog.getButtons()) {
			encodeButton(buf, btn);
		}
	}

	private void encodeButton(ByteArrayOutputStream buf, IgsButton btn) throws IOException {
		writeU16(buf, btn.getId());
		writeU16(buf, btn.getNumericSelectValue());
		buf.write(btn.isAutoAction() ? 0x80 : 0x00);

		writeU16(buf, btn.getXPos());
		writeU16(buf, btn.getYPos());

		writeU16(buf, btn.getUpperButtonIdRef());
		writeU16(buf, btn.getLowerButtonIdRef());
		writeU16(buf, btn.getLeftButtonIdRef());
		writeU16(buf, btn.getRightButtonIdRef());

		// Normal state
		writeU16(buf, btn.getNormalStartObjectIdRef());
		writeU16(buf, btn.getNormalEndObjectIdRef());
		buf.write(btn.isNormalRepeatFlag() ? 0x80 : 0x00);

		// Selected state
		buf.write(btn.getSelectedSoundIdRef());
		writeU16(buf, btn.getSelectedStartObjectIdRef());
		writeU16(buf, btn.getSelectedEndObjectIdRef());
		buf.write(btn.isSelectedRepeatFlag() ? 0x80 : 0x00);

		// Activated state
		buf.write(btn.getActivatedSoundIdRef());
		writeU16(buf, btn.getActivatedStartObjectIdRef());
		writeU16(buf, btn.getActivatedEndObjectIdRef());

		// Navigation commands
		writeU16(buf, btn.getNavigationCommands().size());
		for (NavigationCommand cmd : btn.getNavigationCommands()) {
			buf.write(cmd.toParsed().toRaw());
		}
	}

	private void encodeEffectSequence(ByteArrayOutputStream buf, IgsEffectSequence es) throws IOException {
		if (es == null) {
			buf.write(0); // num_windows = 0
			buf.write(0); // num_effects = 0
			return;
		}

		// num_windows
		buf.write(es.getWindows().size());
		for (IgsWindow w : es.getWindows()) {
			buf.write(w.getId());
			writeU16(buf, w.getX());
			writeU16(buf, w.getY());
			writeU16(buf, w.getWidth());
			writeU16(buf, w.getHeight());
		}

		// num_effects
		buf.write(es.getEffects().size());
		for (IgsEffect effect : es.getEffects()) {
			encodeEffect(buf, effect);
		}
	}

	private void encodeEffect(ByteArrayOutputStream buf, IgsEffect effect) throws IOException {
		writeU24(buf, effect.getDuration());
		buf.write(effect.getPaletteIdRef());
		buf.write(effect.getCompositionObjects().size());

		for (CompositionObject co : effect.getCompositionObjects()) {
			encodeCompositionObject(buf, co);
		}
	}

	private void encodeCompositionObject(ByteArrayOutputStream buf, CompositionObject co) throws IOException {
		writeU16(buf, co.getObjectIdRef());
		buf.write(co.getWindowIdRef());

		int flags = 0;
		if (co.isCropFlag())
			flags |= 0x80;
		if (co.isForcedOn())
			flags |= 0x40;
		buf.write(flags);

		writeU16(buf, co.getX());
		writeU16(buf, co.getY());

		if (co.isCropFlag()) {
			writeU16(buf, co.getCropX());
			writeU16(buf, co.getCropY());
			writeU16(buf, co.getCropWidth());
			writeU16(buf, co.getCropHeight());
		}
	}

	// -------------------------------------------------------------------------
	// Binary helpers
	// -------------------------------------------------------------------------

	private void writeU16(OutputStream out, int value) throws IOException {
		IgsPgsCodec.writeU16(out, value);
	}

	private void writeU24(OutputStream out, int value) throws IOException {
		IgsPgsCodec.writeU24(out, value);
	}

	/**
	 * Writes a 33-bit PTS: skip(7) + bit[32] as byte[0].bit0, then 4 bytes.
	 */
	private void writePts33(OutputStream out, long pts) throws IOException {
		IgsPgsCodec.writePts33(out, pts);
	}

}
