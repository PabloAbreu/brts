package org.brts.lowlevel.realdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.brts.lowlevel.bdmv.BdjoWriter;
import org.brts.lowlevel.model.bdmv.Bdjo;
import org.brts.lowlevel.parser.BdjoParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link BdjoParser} against real disc samples at {@code samples/PB/BDMV/BDJO/}.
 * <p>
 * All tests are skipped when the sample data is absent (e.g. on a CI agent without the disc files).
 * <p>
 * Sample disc notes (verified by binary analysis against libbluray structure):
 * <ul>
 * <li>All files are version {@code "0200"}.</li>
 * <li>{@code 00000.bdjo} and {@code 00003.bdjo} have 2 applications: StandardMenuXlet (control_code=2) and
 * TitleBoundXlet (control_code=1).</li>
 * <li>{@code 00001.bdjo} differs from 00000 only in terminal flags and key interest table.</li>
 * <li>{@code 12345.bdjo} is a minimal BDJO with 0 applications.</li>
 * <li>Organization ID is consistently {@code 0x7FFF0C8E}.</li>
 * </ul>
 */
class RealDiscBdjoParserTest {

	private static final Path BDJO_DIR = Paths.get("../samples/PB/BDMV/BDJO");

	private static final Path BDJO_00000 = BDJO_DIR.resolve("00000.bdjo");

	private static final Path BDJO_00001 = BDJO_DIR.resolve("00001.bdjo");

	private static final Path BDJO_12345 = BDJO_DIR.resolve("12345.bdjo");

	private final BdjoParser parser = new BdjoParser();

	@BeforeAll
	static void requireSampleData() {
		assumeThat(Files.isRegularFile(BDJO_00000))
				.as("sample disc data must be present at " + BDJO_00000.toAbsolutePath()).isTrue();
	}

	// ------------------------------------------------------------------
	// Basic parseability
	// ------------------------------------------------------------------

	@Test
	void bdjo00000_parsesWithoutException() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo).isNotNull();
	}

	@Test
	void bdjo12345_parsesWithoutException() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_12345);
		assertThat(bdjo).isNotNull();
	}

	// ------------------------------------------------------------------
	// Version
	// ------------------------------------------------------------------

	@Test
	void version_is0200() throws IOException {
		assertThat(parser.parse(BDJO_00000).getVersion()).isEqualTo("0200");
	}

	// ------------------------------------------------------------------
	// TerminalInfo
	// ------------------------------------------------------------------

	@Test
	void terminalInfo_defaultFont_isStars() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getTerminalInfo().getDefaultFont()).isEqualTo("*****");
	}

	@Test
	void terminalInfo_haviConfig() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getTerminalInfo().getInitialHaviConfigId()).isEqualTo(1);
	}

	@Test
	void terminalInfo_00000_menuCallMask_true() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getTerminalInfo().isMenuCallMask()).isTrue();
	}

	@Test
	void terminalInfo_00000_titleSearchMask_true() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getTerminalInfo().isTitleSearchMask()).isTrue();
	}

	@Test
	void terminalInfo_00001_menuCallMask_false() throws IOException {
		// 00001.bdjo flags byte is 0x14 → havi=1, menu_call=0, title_search=1
		Bdjo bdjo = parser.parse(BDJO_00001);
		assertThat(bdjo.getTerminalInfo().isMenuCallMask()).isFalse();
	}

	// ------------------------------------------------------------------
	// AppCacheInfo
	// ------------------------------------------------------------------

	@Test
	void appCacheInfo_00000_hasOneItem() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getAppCacheInfo().getItems()).hasSize(1);
	}

	@Test
	void appCacheInfo_00000_firstItem_isJar() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		Bdjo.AppCacheItem item = bdjo.getAppCacheInfo().getItems().get(0);
		assertThat(item.getType()).isEqualTo(1);
		assertThat(item.getRefToName()).isEqualTo("00000");
		assertThat(item.getLanguageCode()).isEqualTo("*.*");
	}

	// ------------------------------------------------------------------
	// AccessiblePlaylists
	// ------------------------------------------------------------------

	@Test
	void accessiblePlaylists_00000_accessToAll() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getAccessiblePlaylists().isAccessToAllFlag()).isTrue();
	}

	@Test
	void accessiblePlaylists_00000_noExplicitPlaylists() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getAccessiblePlaylists().getPlaylistNames()).isEmpty();
	}

	// ------------------------------------------------------------------
	// Application Management Table
	// ------------------------------------------------------------------

	@Test
	void appTable_00000_hasTwoApps() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications()).hasSize(2);
	}

	@Test
	void appTable_12345_hasNoApps() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_12345);
		assertThat(bdjo.getApplications()).isEmpty();
	}

	@Test
	void appTable_00000_app0_controlCode() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		// App 0 is StandardMenuXlet with controlCode=2 (present)
		assertThat(bdjo.getApplications().get(0).getControlCode()).isEqualTo(2);
	}

	@Test
	void appTable_00000_app1_controlCode() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		// App 1 is TitleBoundXlet with controlCode=1 (autostart)
		assertThat(bdjo.getApplications().get(1).getControlCode()).isEqualTo(1);
	}

	@Test
	void appTable_00000_app0_organizationId() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getOrganizationId()).isEqualTo(0x7FFF0C8EL);
	}

	@Test
	void appTable_00000_app0_applicationId() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getApplicationId()).isEqualTo(0x4001);
	}

	@Test
	void appTable_00000_app0_profiles() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		Bdjo.BdjoApp app = bdjo.getApplications().get(0);
		assertThat(app.getProfiles()).hasSize(3);
		assertThat(app.getProfiles().get(0).getProfileNumber()).isEqualTo(1);
		assertThat(app.getProfiles().get(0).getMajorVersion()).isEqualTo(1);
		assertThat(app.getProfiles().get(0).getMinorVersion()).isEqualTo(0);
		assertThat(app.getProfiles().get(0).getMicroVersion()).isEqualTo(0);
	}

	@Test
	void appTable_00000_app0_priority() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getPriority()).isEqualTo(1);
	}

	@Test
	void appTable_00000_app0_binding_discBound() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getBinding()).isEqualTo(1);
	}

	@Test
	void appTable_00000_app1_binding_titleBound() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(1).getBinding()).isEqualTo(3);
	}

	@Test
	void appTable_00000_app0_visibility() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getVisibility()).isEqualTo(1);
	}

	@Test
	void appTable_00000_app0_name() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		Bdjo.BdjoApp app = bdjo.getApplications().get(0);
		assertThat(app.getNames()).hasSize(1);
		assertThat(app.getNames().get(0).getLanguage()).isEqualTo("eng");
		assertThat(app.getNames().get(0).getName()).isEqualTo("StandardMenuXlet");
	}

	@Test
	void appTable_00000_app1_name() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		Bdjo.BdjoApp app = bdjo.getApplications().get(1);
		assertThat(app.getNames()).hasSize(1);
		assertThat(app.getNames().get(0).getLanguage()).isEqualTo("eng");
		assertThat(app.getNames().get(0).getName()).isEqualTo("TitleBoundXlet");
	}

	@Test
	void appTable_00000_app0_baseDir() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getBaseDir()).isEqualTo("00000");
	}

	@Test
	void appTable_00000_app0_initialClass() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getInitialClass()).isEqualTo("com.dis.radius.StandardMenuXlet");
	}

	@Test
	void appTable_00000_app1_initialClass() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(1).getInitialClass()).isEqualTo("com.dis.radius.TitleBoundXlet");
	}

	@Test
	void appTable_00000_app0_emptyIconLocator() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getIconLocator()).isEmpty();
	}

	@Test
	void appTable_00000_app0_emptyClasspathExt() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getClasspathExtension()).isEmpty();
	}

	@Test
	void appTable_00000_app0_noParams() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getApplications().get(0).getParameters()).isEmpty();
	}

	// ------------------------------------------------------------------
	// KeyInterestTable
	// ------------------------------------------------------------------

	@Test
	void keyInterestTable_00000_trackNext_true() throws IOException {
		// 00000.bdjo key interest byte 0 is 0x08 → vk_track_next=1
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getKeyInterestTable().isVkTrackNext()).isTrue();
	}

	@Test
	void keyInterestTable_00000_play_false() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getKeyInterestTable().isVkPlay()).isFalse();
	}

	@Test
	void keyInterestTable_00001_allFalse() throws IOException {
		// 00001.bdjo key interest is all zeros
		Bdjo bdjo = parser.parse(BDJO_00001);
		assertThat(bdjo.getKeyInterestTable().isVkPlay()).isFalse();
		assertThat(bdjo.getKeyInterestTable().isVkTrackNext()).isFalse();
	}

	// ------------------------------------------------------------------
	// FileAccessInfo
	// ------------------------------------------------------------------

	@Test
	void fileAccessInfo_00000_isDot() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_00000);
		assertThat(bdjo.getFileAccessInfo()).isEqualTo(".");
	}

	@Test
	void fileAccessInfo_12345_isEmpty() throws IOException {
		Bdjo bdjo = parser.parse(BDJO_12345);
		assertThat(bdjo.getFileAccessInfo()).isEmpty();
	}

	// ------------------------------------------------------------------
	// Binary round-trip: parse → write → compare bytes
	// ------------------------------------------------------------------

	@Test
	void binaryRoundTrip_00000_bytesMatch() throws IOException {
		byte[] original = Files.readAllBytes(BDJO_00000);
		Bdjo parsed = parser.parse(BDJO_00000);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new BdjoWriter().write(parsed, out);
		byte[] rewritten = out.toByteArray();

		assertThat(rewritten).as("round-trip of 00000.bdjo").isEqualTo(original);
	}

	@Test
	void binaryRoundTrip_00001_bytesMatch() throws IOException {
		byte[] original = Files.readAllBytes(BDJO_00001);
		Bdjo parsed = parser.parse(BDJO_00001);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new BdjoWriter().write(parsed, out);
		byte[] rewritten = out.toByteArray();

		assertThat(rewritten).as("round-trip of 00001.bdjo").isEqualTo(original);
	}

	@Test
	void binaryRoundTrip_12345_bytesMatch() throws IOException {
		byte[] original = Files.readAllBytes(BDJO_12345);
		Bdjo parsed = parser.parse(BDJO_12345);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new BdjoWriter().write(parsed, out);
		byte[] rewritten = out.toByteArray();

		assertThat(rewritten).as("round-trip of 12345.bdjo").isEqualTo(original);
	}

	@Test
	void allBdjoFiles_parseSuccessfully() throws IOException {
		for (Path file : Files.list(BDJO_DIR).filter(p -> p.toString().endsWith(".bdjo")).toList()) {
			Bdjo bdjo = parser.parse(file);
			assertThat(bdjo).as("parsed " + file.getFileName()).isNotNull();
		}
	}

	@Test
	void allBdjoFiles_binaryRoundTrip() throws IOException {
		BdjoWriter writer = new BdjoWriter();
		for (Path file : Files.list(BDJO_DIR).filter(p -> p.toString().endsWith(".bdjo")).toList()) {
			byte[] original = Files.readAllBytes(file);
			Bdjo parsed = parser.parse(file);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			writer.write(parsed, out);
			assertThat(out.toByteArray()).as("binary round-trip of " + file.getFileName()).isEqualTo(original);
		}
	}

}
