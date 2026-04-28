package org.brts.lowlevel.roundtrip;

import org.brts.common.exception.WriteException;
import org.brts.lowlevel.bdmv.IndexBdmvWriter;
import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.parser.IndexBdmvParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip tests for {@code index.bdmv}: write via {@link IndexBdmvWriter}, re-parse via {@link IndexBdmvParser}, and
 * verify the model is preserved. Also covers version-specific validation rules.
 */
class IndexBdmvRoundTripTest {

	private final IndexBdmvWriter writer = new IndexBdmvWriter();

	private final IndexBdmvParser parser = new IndexBdmvParser();

	// -------------------------------------------------------------------------
	// Round-trip correctness
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_hdmvDisc_fieldsPreserved() throws Exception {
		IndexBdmv original = buildHdmvIndex("0300", 2);

		IndexBdmv reparsed = roundTrip(original);

		assertThat(reparsed.getVersion()).isEqualTo("0300");
		assertThat(reparsed.getDiscApplicationType()).isEqualTo(1);

		assertThat(reparsed.getFirstPlayTitle()).isNotNull();
		assertThat(reparsed.getFirstPlayTitle().isHdmv()).isTrue();
		assertThat(reparsed.getFirstPlayTitle().getHdmvObjectId()).isEqualTo(0);
		assertThat(reparsed.getFirstPlayTitle().getAccessType()).isEqualTo(2);

		assertThat(reparsed.getTopMenuTitle()).isNotNull();
		assertThat(reparsed.getTopMenuTitle().isHdmv()).isTrue();
		assertThat(reparsed.getTopMenuTitle().getHdmvObjectId()).isEqualTo(1);

		assertThat(reparsed.getTitles()).hasSize(2);
		assertThat(reparsed.getTitles().get(0).getHdmvObjectId()).isEqualTo(2);
		assertThat(reparsed.getTitles().get(1).getHdmvObjectId()).isEqualTo(3);
	}

	@Test
	void roundTrip_bdjDisc_fieldsPreserved() throws Exception {
		IndexBdmv original = buildBdjIndex("0200");

		IndexBdmv reparsed = roundTrip(original);

		assertThat(reparsed.getVersion()).isEqualTo("0200");
		assertThat(reparsed.getFirstPlayTitle().isBdj()).isTrue();
		assertThat(reparsed.getFirstPlayTitle().getBdjObjectName()).isEqualTo("00002");
		assertThat(reparsed.getTopMenuTitle().isBdj()).isTrue();
		assertThat(reparsed.getTopMenuTitle().getBdjObjectName()).isEqualTo("00000");
		assertThat(reparsed.getTitles()).hasSize(1);
		assertThat(reparsed.getTitles().get(0).isBdj()).isTrue();
		assertThat(reparsed.getTitles().get(0).getBdjObjectName()).isEqualTo("00001");
	}

	@Test
	void roundTrip_nullVersion_defaultsTo0300() throws Exception {
		IndexBdmv original = buildHdmvIndex(null, 1);

		IndexBdmv reparsed = roundTrip(original);

		assertThat(reparsed.getVersion()).isEqualTo("0300");
	}

	@Test
	void roundTrip_blankVersion_defaultsTo0300() throws Exception {
		IndexBdmv original = buildHdmvIndex("", 1);

		IndexBdmv reparsed = roundTrip(original);

		assertThat(reparsed.getVersion()).isEqualTo("0300");
	}

	@Test
	void roundTrip_version0200_preserved() throws Exception {
		IndexBdmv original = buildHdmvIndex("0200", 1);
		assertThat(roundTrip(original).getVersion()).isEqualTo("0200");
	}

	@Test
	void roundTrip_contentProviderName_preserved() throws Exception {
		IndexBdmv original = buildHdmvIndex("0300", 0);
		original.setContentProviderName("My Studio");

		IndexBdmv reparsed = roundTrip(original);

		assertThat(reparsed.getContentProviderName()).isEqualTo("My Studio");
	}

	@Test
	void roundTrip_nullTopMenu_writtenAsZeroEntry() throws Exception {
		IndexBdmv original = buildHdmvIndex("0300", 1);
		original.setTopMenuTitle(null);

		IndexBdmv reparsed = roundTrip(original);

		// null → all-zero 12-byte entry → objectType=0, accessType=0, hdmvObjectId=0
		assertThat(reparsed.getTopMenuTitle()).isNotNull();
		assertThat(reparsed.getTopMenuTitle().getObjectType()).isEqualTo(0);
		assertThat(reparsed.getTopMenuTitle().getAccessType()).isEqualTo(0);
		assertThat(reparsed.getTopMenuTitle().getHdmvObjectId()).isEqualTo(0);
	}

	@Test
	void roundTrip_accessTypePacked() throws Exception {
		IndexBdmv original = buildHdmvIndex("0300", 1);
		original.getTitles().get(0).setAccessType(2);

		IndexBdmv reparsed = roundTrip(original);

		assertThat(reparsed.getTitles().get(0).getAccessType()).isEqualTo(2);
	}

	@Test
	void roundTrip_playbackTypePacked() throws Exception {
		IndexBdmv original = buildHdmvIndex("0300", 1);
		original.getFirstPlayTitle().setPlaybackType(1);

		IndexBdmv reparsed = roundTrip(original);

		assertThat(reparsed.getFirstPlayTitle().getPlaybackType()).isEqualTo(1);
	}

	// -------------------------------------------------------------------------
	// File-level structure assertions
	// -------------------------------------------------------------------------

	@Test
	void magic_isINDX() throws Exception {
		byte[] bytes = writeToBytes(buildHdmvIndex("0300", 1));
		assertThat(new String(bytes, 0, 4)).isEqualTo("INDX");
	}

	@Test
	void version_writtenFromModel() throws Exception {
		assertThat(new String(writeToBytes(buildHdmvIndex("0300", 1)), 4, 4)).isEqualTo("0300");
		assertThat(new String(writeToBytes(buildHdmvIndex("0200", 1)), 4, 4)).isEqualTo("0200");
		assertThat(new String(writeToBytes(buildHdmvIndex("0100", 1)), 4, 4)).isEqualTo("0100");
	}

	@Test
	void indexTableAddress_is0x4e() throws Exception {
		byte[] bytes = writeToBytes(buildHdmvIndex("0300", 1));
		int addr = ((bytes[8] & 0xFF) << 24) | ((bytes[9] & 0xFF) << 16) | ((bytes[10] & 0xFF) << 8)
				| (bytes[11] & 0xFF);
		assertThat(addr).as("IndexTableStartAddress").isEqualTo(0x4e);
	}

	@Test
	void appInfoBdmv_presentAtOffset40() throws Exception {
		byte[] bytes = writeToBytes(buildHdmvIndex("0300", 1));
		// section_length at offset 40 should be 34 (0x22)
		int sectionLength = ((bytes[40] & 0xFF) << 24) | ((bytes[41] & 0xFF) << 16) | ((bytes[42] & 0xFF) << 8)
				| (bytes[43] & 0xFF);
		assertThat(sectionLength).as("AppInfoBDMV section_length").isEqualTo(34);
	}

	// -------------------------------------------------------------------------
	// Version-specific validation
	// -------------------------------------------------------------------------

	@Test
	void version0100_rejectsFirstPlayBdj() {
		IndexBdmv bdj = buildBdjIndex("0100");
		assertThatThrownBy(() -> writeToBytes(bdj)).isInstanceOf(WriteException.class).hasMessageContaining("0100")
				.hasMessageContaining("BD-J");
	}

	@Test
	void version0100_rejectsTopMenuBdj() {
		IndexBdmv idx = buildHdmvIndex("0100", 0);
		idx.setTopMenuTitle(bdjEntry("00001"));
		assertThatThrownBy(() -> writeToBytes(idx)).isInstanceOf(WriteException.class).hasMessageContaining("BD-J");
	}

	@Test
	void version0100_rejectsTitleBdj() {
		IndexBdmv idx = buildHdmvIndex("0100", 1);
		idx.getTitles().set(0, bdjEntry("00001"));
		assertThatThrownBy(() -> writeToBytes(idx)).isInstanceOf(WriteException.class).hasMessageContaining("BD-J");
	}

	@Test
	void version0100_hdmvOnly_succeeds() throws Exception {
		IndexBdmv idx = buildHdmvIndex("0100", 2);
		IndexBdmv reparsed = roundTrip(idx);
		assertThat(reparsed.getVersion()).isEqualTo("0100");
		assertThat(reparsed.getTitles()).hasSize(2);
		assertThat(reparsed.getTitles().stream().noneMatch(IndexBdmv.TitleEntry::isBdj)).isTrue();
	}

	@Test
	void version0200_acceptsBdj() throws Exception {
		IndexBdmv idx = buildBdjIndex("0200");
		assertThat(roundTrip(idx).getFirstPlayTitle().isBdj()).isTrue();
	}

	@Test
	void version0300_acceptsBdj() throws Exception {
		IndexBdmv idx = buildBdjIndex("0300");
		assertThat(roundTrip(idx).getFirstPlayTitle().isBdj()).isTrue();
	}

	// -------------------------------------------------------------------------
	// Builders
	// -------------------------------------------------------------------------

	private IndexBdmv buildHdmvIndex(String version, int numTitles) {
		IndexBdmv idx = new IndexBdmv();
		idx.setVersion(version);
		idx.setDiscApplicationType(1);
		idx.setFirstPlayTitle(hdmvEntry(0, 2));
		idx.setTopMenuTitle(hdmvEntry(1, 2));
		List<IndexBdmv.TitleEntry> titles = new ArrayList<>();
		for (int i = 0; i < numTitles; i++) {
			titles.add(hdmvEntry(i + 2, 0));
		}
		idx.setTitles(titles);
		return idx;
	}

	private IndexBdmv buildBdjIndex(String version) {
		IndexBdmv idx = new IndexBdmv();
		idx.setVersion(version);
		idx.setDiscApplicationType(1);
		idx.setFirstPlayTitle(bdjEntry("00002"));
		idx.setTopMenuTitle(bdjEntry("00000"));
		idx.setTitles(List.of(bdjEntry("00001")));
		return idx;
	}

	private IndexBdmv.TitleEntry hdmvEntry(int hdmvObjectId, int accessType) {
		IndexBdmv.TitleEntry e = new IndexBdmv.TitleEntry();
		e.setObjectType(1);
		e.setHdmvObjectId(hdmvObjectId);
		e.setAccessType(accessType);
		return e;
	}

	private IndexBdmv.TitleEntry bdjEntry(String name) {
		IndexBdmv.TitleEntry e = new IndexBdmv.TitleEntry();
		e.setObjectType(2);
		e.setBdjObjectName(name);
		e.setAccessType(2);
		return e;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private IndexBdmv roundTrip(IndexBdmv original) throws Exception {
		return parser.parse(new ByteArrayInputStream(writeToBytes(original)));
	}

	private byte[] writeToBytes(IndexBdmv idx) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(idx, out);
		return out.toByteArray();
	}

}
