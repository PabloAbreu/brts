package org.brts.lowlevel.roundtrip;

import org.brts.lowlevel.igs.IgsDemuxer;
import org.brts.lowlevel.igs.IgsMuxer;
import org.brts.lowlevel.igs.IgsParser;
import org.brts.lowlevel.igs.IgsPgsCodec;
import org.brts.lowlevel.igs.IgsSegmentType;
import org.brts.lowlevel.igs.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip tests for the IGS demuxer / muxer pipeline.
 * <p>
 * Builds a synthetic IGS elementary stream in memory, demuxes it to a folder structure, muxes it back to bytes, and
 * verifies byte-level equality with the original.
 */
class IgsRoundTripTest {

	@TempDir
	Path tempDir;

	// -------------------------------------------------------------------------
	// Round-trip: raw bytes → demux → mux → raw bytes
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_syntheticIgs_producesIdenticalBytes() throws Exception {
		// 1. Build a synthetic IGS stream
		byte[] original = buildSyntheticIgsStream();

		// 2. Write to a file and demux
		Path igsFile = tempDir.resolve("test.igs");
		Files.write(igsFile, original);

		Path demuxDir = tempDir.resolve("demuxed");
		IgsDemuxer demuxer = new IgsDemuxer();
		demuxer.demux(igsFile, demuxDir);

		// Verify demux output structure
		assertThat(demuxDir.resolve("igs_manifest.json")).exists();
		assertThat(demuxDir.resolve("ds_0000/display_set.json")).exists();
		assertThat(demuxDir.resolve("ds_0000/obj_0000.rle")).exists();

		// 3. Mux back
		Path remuxedFile = tempDir.resolve("remuxed.igs");
		IgsMuxer muxer = new IgsMuxer();
		muxer.mux(demuxDir, remuxedFile);

		// 4. Compare
		byte[] remuxed = Files.readAllBytes(remuxedFile);
		assertThat(remuxed).as("Round-tripped IGS bytes should match original").isEqualTo(original);
	}

	@Test
	void roundTrip_multipleDisplaySets_preservesAll() throws Exception {
		byte[] original = buildMultiDisplaySetStream();

		Path igsFile = tempDir.resolve("multi.igs");
		Files.write(igsFile, original);

		Path demuxDir = tempDir.resolve("demuxed_multi");
		new IgsDemuxer().demux(igsFile, demuxDir);

		// Verify two display sets
		assertThat(demuxDir.resolve("ds_0000/display_set.json")).exists();
		assertThat(demuxDir.resolve("ds_0001/display_set.json")).exists();

		Path remuxedFile = tempDir.resolve("remuxed_multi.igs");
		new IgsMuxer().mux(demuxDir, remuxedFile);

		byte[] remuxed = Files.readAllBytes(remuxedFile);
		assertThat(remuxed).isEqualTo(original);
	}

	@Test
	void roundTrip_fragmentedObject_demuxesOneLogicalRleAndPreservesCanonicalBytes() throws Exception {
		IgsObject logicalObject = new IgsObject();
		logicalObject.setId(7);
		logicalObject.setVersion(2);
		logicalObject.setWidth(1920);
		logicalObject.setHeight(1080);
		logicalObject.setRleData(new byte[IgsPgsCodec.MAX_ODS_DATA_LENGTH]);

		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		writeSegment(stream, IgsSegmentType.IG_COMPOSITION.getCode(), buildIcsBody());
		for (IgsObject fragment : IgsPgsCodec.fragmentObject(logicalObject)) {
			writeSegment(stream, IgsSegmentType.OBJECT_DEFINITION.getCode(), IgsPgsCodec.encodeObject(fragment));
		}
		writeSegment(stream, IgsSegmentType.END_OF_DISPLAY.getCode(), new byte[0]);
		byte[] original = stream.toByteArray();

		Path igsFile = tempDir.resolve("fragmented.igs");
		Files.write(igsFile, original);
		Path demuxDir = tempDir.resolve("demuxed_fragmented");
		List<IgsDisplaySet> displaySets = new IgsDemuxer().demux(igsFile, demuxDir);

		assertThat(displaySets.get(0).getObjects()).singleElement()
				.satisfies(object -> assertThat(object.getRleData()).isEqualTo(logicalObject.getRleData()));
		assertThat(Files.readAllBytes(demuxDir.resolve("ds_0000/obj_0000.rle"))).isEqualTo(logicalObject.getRleData());

		Path remuxedFile = tempDir.resolve("remuxed_fragmented.igs");
		new IgsMuxer().mux(demuxDir, remuxedFile);
		assertThat(Files.readAllBytes(remuxedFile)).isEqualTo(original);
	}

	@Test
	void muxer_acceptsMaximumSegmentDataLength() throws Exception {
		IgsObject object = new IgsObject();
		object.setRleData(new byte[0xFFFF - 4]);
		IgsDisplaySet displaySet = new IgsDisplaySet();
		displaySet.getObjects().add(object);

		byte[] encoded = new IgsMuxer().encodeDisplaySet(displaySet);

		assertThat(encoded[0] & 0xFF).isEqualTo(IgsSegmentType.OBJECT_DEFINITION.getCode());
		assertThat(encoded[1] & 0xFF).isEqualTo(0xFF);
		assertThat(encoded[2] & 0xFF).isEqualTo(0xFF);
	}

	@Test
	void muxer_rejectsSegmentDataLengthAboveUnsignedShort() {
		IgsObject object = new IgsObject();
		object.setRleData(new byte[0xFFFF - 3]);
		IgsDisplaySet displaySet = new IgsDisplaySet();
		displaySet.getObjects().add(object);

		assertThatThrownBy(() -> new IgsMuxer().encodeDisplaySet(displaySet))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Segment data length 65536 exceeds maximum 65535");
	}

	// -------------------------------------------------------------------------
	// Parser unit tests
	// -------------------------------------------------------------------------

	@Test
	void parser_detectsSegmentTypes() throws Exception {
		byte[] stream = buildSyntheticIgsStream();
		List<IgsRawSegment> segments = IgsParser.parseSegments(stream);

		assertThat(segments).isNotEmpty();
		assertThat(segments.get(0).getType()).isEqualTo(IgsSegmentType.IG_COMPOSITION);

		// Should have: ICS, PDS, WDS, ODS, END
		assertThat(segments).extracting(IgsRawSegment::getType).containsExactly(IgsSegmentType.IG_COMPOSITION,
				IgsSegmentType.PALETTE_DEFINITION, IgsSegmentType.WINDOW_DEFINITION, IgsSegmentType.OBJECT_DEFINITION,
				IgsSegmentType.END_OF_DISPLAY);
	}

	@Test
	void parser_decodesIcsFields() throws Exception {
		byte[] stream = buildSyntheticIgsStream();
		List<IgsRawSegment> segments = IgsParser.parseSegments(stream);
		List<IgsDisplaySet> displaySets = IgsParser.groupIntoDisplaySets(segments);

		assertThat(displaySets).hasSize(1);
		IgsDisplaySet ds = displaySets.get(0);

		assertThat(ds.isEpochStart()).isTrue();
		assertThat(ds.isComplete()).isTrue();

		IgsCompositionSegment ics = ds.getCompositionSegment();
		assertThat(ics).isNotNull();
		assertThat(ics.getVideoDescriptor().getWidth()).isEqualTo(1920);
		assertThat(ics.getVideoDescriptor().getHeight()).isEqualTo(1080);

		IgsInteractiveComposition ic = ics.getInteractiveComposition();
		assertThat(ic).isNotNull();
		assertThat(ic.getPages()).hasSize(1);

		IgsPage page = ic.getPages().get(0);
		assertThat(page.getBogs()).hasSize(1);

		IgsButton btn = page.getBogs().get(0).getButtons().get(0);
		assertThat(btn.getId()).isEqualTo(1);
		assertThat(btn.getXPos()).isEqualTo(100);
		assertThat(btn.getYPos()).isEqualTo(200);
	}

	@Test
	void parser_decodesPalette() throws Exception {
		byte[] stream = buildSyntheticIgsStream();
		List<IgsRawSegment> segments = IgsParser.parseSegments(stream);
		List<IgsDisplaySet> displaySets = IgsParser.groupIntoDisplaySets(segments);

		IgsDisplaySet ds = displaySets.get(0);
		assertThat(ds.getPalettes()).hasSize(1);
		IgsPalette pal = ds.getPalettes().get(0);
		assertThat(pal.getId()).isEqualTo(0);
		assertThat(pal.getEntries()).hasSize(2);

		PaletteEntry e0 = pal.getEntries().get(0);
		assertThat(e0.getEntryId()).isEqualTo(0);
		assertThat(e0.getAlpha()).isEqualTo(0); // transparent

		PaletteEntry e1 = pal.getEntries().get(1);
		assertThat(e1.getEntryId()).isEqualTo(1);
		assertThat(e1.getY()).isEqualTo(235);
		assertThat(e1.getAlpha()).isEqualTo(255); // opaque white
	}

	@Test
	void parser_decodesObject() throws Exception {
		byte[] stream = buildSyntheticIgsStream();
		List<IgsRawSegment> segments = IgsParser.parseSegments(stream);
		List<IgsDisplaySet> displaySets = IgsParser.groupIntoDisplaySets(segments);

		IgsDisplaySet ds = displaySets.get(0);
		assertThat(ds.getObjects()).hasSize(1);
		IgsObject obj = ds.getObjects().get(0);
		assertThat(obj.getId()).isEqualTo(0);
		assertThat(obj.getWidth()).isEqualTo(64);
		assertThat(obj.getHeight()).isEqualTo(32);
		assertThat(obj.getRleData()).isNotEmpty();
	}

	@Test
	void parser_decodesWindow() throws Exception {
		byte[] stream = buildSyntheticIgsStream();
		List<IgsRawSegment> segments = IgsParser.parseSegments(stream);
		List<IgsDisplaySet> displaySets = IgsParser.groupIntoDisplaySets(segments);

		IgsDisplaySet ds = displaySets.get(0);
		assertThat(ds.getWindowDefinitions()).hasSize(1);
		IgsWindow w = ds.getWindowDefinitions().get(0).getWindows().get(0);
		assertThat(w.getId()).isEqualTo(0);
		assertThat(w.getX()).isEqualTo(0);
		assertThat(w.getY()).isEqualTo(0);
		assertThat(w.getWidth()).isEqualTo(1920);
		assertThat(w.getHeight()).isEqualTo(1080);
	}

	@Test
	void muxer_encodesPaletteCorrectly() throws Exception {
		IgsPalette pal = new IgsPalette();
		pal.setId(5);
		pal.setVersion(2);
		PaletteEntry e = new PaletteEntry();
		e.setEntryId(10);
		e.setY(128);
		e.setCr(64);
		e.setCb(32);
		e.setAlpha(200);
		pal.getEntries().add(e);

		IgsMuxer muxer = new IgsMuxer();
		byte[] encoded = muxer.encodePalette(pal);

		// Re-parse
		IgsRawSegment raw = new IgsRawSegment();
		raw.setPts(-1);
		raw.setType(IgsSegmentType.PALETTE_DEFINITION);
		raw.setSegmentData(encoded);

		IgsPalette decoded = IgsParser.decodePalette(raw);
		assertThat(decoded.getId()).isEqualTo(5);
		assertThat(decoded.getVersion()).isEqualTo(2);
		assertThat(decoded.getEntries()).hasSize(1);
		assertThat(decoded.getEntries().get(0).getEntryId()).isEqualTo(10);
		assertThat(decoded.getEntries().get(0).getY()).isEqualTo(128);
		assertThat(decoded.getEntries().get(0).getCr()).isEqualTo(64);
		assertThat(decoded.getEntries().get(0).getCb()).isEqualTo(32);
		assertThat(decoded.getEntries().get(0).getAlpha()).isEqualTo(200);
	}

	// -------------------------------------------------------------------------
	// Synthetic stream builders
	// -------------------------------------------------------------------------

	/**
	 * Builds a minimal valid IGS stream with one display set containing: ICS + PDS + WDS + ODS + END.
	 */
	private byte[] buildSyntheticIgsStream() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// --- ICS ---
		byte[] icsBody = buildIcsBody();
		writeSegment(out, 0x18, icsBody);

		// --- PDS (2 entries) ---
		byte[] pdsBody = buildPdsBody();
		writeSegment(out, 0x14, pdsBody);

		// --- WDS (1 window: full screen) ---
		byte[] wdsBody = buildWdsBody();
		writeSegment(out, 0x17, wdsBody);

		// --- ODS (single object, 64x32, simple RLE) ---
		byte[] odsBody = buildOdsBody();
		writeSegment(out, 0x15, odsBody);

		// --- END ---
		writeSegment(out, 0x80, new byte[0]);

		return out.toByteArray();
	}

	private byte[] buildMultiDisplaySetStream() throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// Display set 0 (epoch start)
		writeSegment(out, 0x18, buildIcsBody());
		writeSegment(out, 0x14, buildPdsBody());
		writeSegment(out, 0x17, buildWdsBody());
		writeSegment(out, 0x15, buildOdsBody());
		writeSegment(out, 0x80, new byte[0]);

		// Display set 1 (normal update — state=0)
		writeSegment(out, 0x18, buildIcsBody(0)); // state=0
		writeSegment(out, 0x14, buildPdsBody());
		writeSegment(out, 0x80, new byte[0]);

		return out.toByteArray();
	}

	private byte[] buildIcsBody() throws IOException {
		return buildIcsBody(2); // epoch start
	}

	private byte[] buildIcsBody(int compositionState) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();

		// Video descriptor: 1920x1080, frame rate code 1
		writeU16(buf, 1920);
		writeU16(buf, 1080);
		buf.write(0x10); // frameRateCode=1, skip=0

		// Composition descriptor: number=0, state=compositionState
		writeU16(buf, 0);
		buf.write(compositionState << 6); // state in bits 7-6

		// Sequence descriptor: first+last
		buf.write(0xC0);

		// Interactive composition
		byte[] icData = buildInteractiveCompositionBody();
		// data_len (24 bits)
		writeU24(buf, icData.length);
		buf.write(icData);

		return buf.toByteArray();
	}

	private byte[] buildInteractiveCompositionBody() throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();

		// stream_model=0, ui_model=0, skip(6)
		buf.write(0x00);

		// composition_timeout_pts (5 bytes: skip(7) + 33-bit PTS)
		buf.write(0x00);
		buf.write(0x00);
		buf.write(0x00);
		buf.write(0x00);
		buf.write(0x00);
		// selection_timeout_pts
		buf.write(0x00);
		buf.write(0x00);
		buf.write(0x00);
		buf.write(0x00);
		buf.write(0x00);

		// user_timeout_duration (24 bits) = 0
		writeU24(buf, 0);

		// num_pages = 1
		buf.write(1);

		// Page
		buf.write(0); // id
		buf.write(0); // version

		// UO mask (8 bytes)
		buf.write(new byte[8]);

		// In-effects: 0 windows, 0 effects
		buf.write(0); // num_windows
		buf.write(0); // num_effects

		// Out-effects: 0 windows, 0 effects
		buf.write(0); // num_windows
		buf.write(0); // num_effects

		// animation_frame_rate_code
		buf.write(0);

		// default_selected_button_id_ref
		writeU16(buf, 1);
		// default_activated_button_id_ref
		writeU16(buf, 0xFFFF);

		// palette_id_ref
		buf.write(0);

		// num_bogs = 1
		buf.write(1);

		// BOG: default_valid_button_id_ref + num_buttons + button
		writeU16(buf, 1); // default_valid = button 1
		buf.write(1); // 1 button

		// Button: id=1
		writeU16(buf, 1); // id
		writeU16(buf, 0); // numeric_select_value
		buf.write(0x00); // auto_action=false
		writeU16(buf, 100); // x_pos
		writeU16(buf, 200); // y_pos

		// Neighbors: all point to self (id=1)
		writeU16(buf, 1); // upper
		writeU16(buf, 1); // lower
		writeU16(buf, 1); // left
		writeU16(buf, 1); // right

		// Normal state: start=0, end=0, repeat=0
		writeU16(buf, 0);
		writeU16(buf, 0);
		buf.write(0x00);

		// Selected state: sound=0, start=0, end=0, repeat=0
		buf.write(0);
		writeU16(buf, 0);
		writeU16(buf, 0);
		buf.write(0x00);

		// Activated state: sound=0, start=0, end=0
		buf.write(0);
		writeU16(buf, 0);
		writeU16(buf, 0);

		// num_nav_cmds = 0
		writeU16(buf, 0);

		return buf.toByteArray();
	}

	private byte[] buildPdsBody() {
		// id=0, version=0, 2 entries
		byte[] d = new byte[2 + 2 * 5];
		d[0] = 0; // id
		d[1] = 0; // version
		// Entry 0: transparent black
		d[2] = 0; // entryId
		d[3] = 16; // Y (black in BT.709)
		d[4] = (byte) 128; // Cr
		d[5] = (byte) 128; // Cb
		d[6] = 0; // alpha = transparent
		// Entry 1: opaque white
		d[7] = 1; // entryId
		d[8] = (byte) 235; // Y (white)
		d[9] = (byte) 128; // Cr
		d[10] = (byte) 128; // Cb
		d[11] = (byte) 255; // alpha = opaque
		return d;
	}

	private byte[] buildWdsBody() {
		// 1 window: full screen
		byte[] d = new byte[1 + 9];
		d[0] = 1; // num_windows
		d[1] = 0; // id
		// x=0
		d[2] = 0;
		d[3] = 0;
		// y=0
		d[4] = 0;
		d[5] = 0;
		// width=1920
		d[6] = (byte) 0x07;
		d[7] = (byte) 0x80;
		// height=1080
		d[8] = (byte) 0x04;
		d[9] = (byte) 0x38;
		return d;
	}

	private byte[] buildOdsBody() {
		// Simple RLE: 64x32 filled with palette index 1
		// RLE encoding: for each line, emit {color=1, len=64}, then end-of-line (0x00,
		// 0x00)
		ByteArrayOutputStream rle = new ByteArrayOutputStream();
		for (int row = 0; row < 32; row++) {
			// RLE run: non-zero byte = single pixel of that color; or use extended run
			// Extended: 0x00, 0x80|len_hi, len_lo, color — for runs with color
			// For simplicity, use: 0x00, 0xC0 | (len>>8), len&0xFF, color
			// 64 pixels of color 1: 0x00 0xC0 0x40 0x01
			rle.write(0x00);
			rle.write(0xC0);
			rle.write(64);
			rle.write(1);
			// End of line
			rle.write(0x00);
			rle.write(0x00);
		}
		byte[] rleBytes = rle.toByteArray();

		// dataLength = rleBytes.length + 4 (width + height)
		int dataLength = rleBytes.length + 4;

		// ODS body: id(16) + version(8) + seq_desc(8) + dataLength(24) + width(16) +
		// height(16) + rle
		byte[] d = new byte[4 + 3 + 2 + 2 + rleBytes.length];
		int pos = 0;
		d[pos++] = 0;
		d[pos++] = 0; // id=0
		d[pos++] = 0; // version=0
		d[pos++] = (byte) 0xC0; // first+last in sequence
		// dataLength (24 bits)
		d[pos++] = (byte) ((dataLength >> 16) & 0xFF);
		d[pos++] = (byte) ((dataLength >> 8) & 0xFF);
		d[pos++] = (byte) (dataLength & 0xFF);
		// width=64
		d[pos++] = 0;
		d[pos++] = 64;
		// height=32
		d[pos++] = 0;
		d[pos++] = 32;
		// RLE data
		System.arraycopy(rleBytes, 0, d, pos, rleBytes.length);

		return d;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void writeSegment(ByteArrayOutputStream out, int type, byte[] data) throws IOException {
		out.write(type);
		out.write((data.length >> 8) & 0xFF);
		out.write(data.length & 0xFF);
		out.write(data);
	}

	private void writeU16(ByteArrayOutputStream out, int value) {
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
	}

	private void writeU24(ByteArrayOutputStream out, int value) {
		out.write((value >> 16) & 0xFF);
		out.write((value >> 8) & 0xFF);
		out.write(value & 0xFF);
	}

}
