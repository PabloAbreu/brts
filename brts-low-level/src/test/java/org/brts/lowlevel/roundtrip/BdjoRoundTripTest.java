package org.brts.lowlevel.roundtrip;

import org.brts.lowlevel.bdmv.BdjoWriter;
import org.brts.lowlevel.model.bdmv.Bdjo;
import org.brts.lowlevel.parser.BdjoParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip tests for {@code .bdjo}: write via {@link BdjoWriter}, re-parse via
 * {@link BdjoParser}, and verify the model is preserved.
 */
class BdjoRoundTripTest {

	private final BdjoWriter writer = new BdjoWriter();

	private final BdjoParser parser = new BdjoParser();

	// -------------------------------------------------------------------------
	// Header / version
	// -------------------------------------------------------------------------

	@Test
	void magic_isBDJO() throws Exception {
		byte[] bytes = writeToBytes(buildMinimalBdjo());
		assertThat(new String(bytes, 0, 4)).isEqualTo("BDJO");
	}

	@Test
	void version_written() throws Exception {
		byte[] bytes = writeToBytes(buildMinimalBdjo());
		assertThat(new String(bytes, 4, 4)).isEqualTo("0200");
	}

	@Test
	void roundTrip_minimalBdjo() throws Exception {
		Bdjo original = buildMinimalBdjo();
		Bdjo reparsed = roundTrip(original);

		assertThat(reparsed.getVersion()).isEqualTo("0200");
		assertThat(reparsed.getApplications()).isEmpty();
		assertThat(reparsed.getFileAccessInfo()).isEmpty();
	}

	// -------------------------------------------------------------------------
	// TerminalInfo
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_terminalInfo_defaultFont() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.getTerminalInfo().setDefaultFont("MyFnt");

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getTerminalInfo().getDefaultFont()).isEqualTo("MyFnt");
	}

	@Test
	void roundTrip_terminalInfo_haviConfig() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.getTerminalInfo().setInitialHaviConfigId(5);

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getTerminalInfo().getInitialHaviConfigId()).isEqualTo(5);
	}

	@Test
	void roundTrip_terminalInfo_menuCallMask() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.getTerminalInfo().setMenuCallMask(true);

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getTerminalInfo().isMenuCallMask()).isTrue();
	}

	@Test
	void roundTrip_terminalInfo_titleSearchMask() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.getTerminalInfo().setTitleSearchMask(true);

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getTerminalInfo().isTitleSearchMask()).isTrue();
	}

	@Test
	void roundTrip_terminalInfo_allFlagsFalse() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.getTerminalInfo().setMenuCallMask(false);
		original.getTerminalInfo().setTitleSearchMask(false);

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getTerminalInfo().isMenuCallMask()).isFalse();
		assertThat(reparsed.getTerminalInfo().isTitleSearchMask()).isFalse();
	}

	// -------------------------------------------------------------------------
	// AppCacheInfo
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_appCacheInfo_empty() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.setAppCacheInfo(new Bdjo.AppCacheInfo());
		original.getAppCacheInfo().setItems(List.of());

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getAppCacheInfo().getItems()).isEmpty();
	}

	@Test
	void roundTrip_appCacheInfo_singleJar() throws Exception {
		Bdjo original = buildMinimalBdjo();
		Bdjo.AppCacheItem item = new Bdjo.AppCacheItem();
		item.setType(1);
		item.setRefToName("00001");
		item.setLanguageCode("*.*");
		original.getAppCacheInfo().setItems(List.of(item));

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getAppCacheInfo().getItems()).hasSize(1);
		Bdjo.AppCacheItem parsed = reparsed.getAppCacheInfo().getItems().get(0);
		assertThat(parsed.getType()).isEqualTo(1);
		assertThat(parsed.getRefToName()).isEqualTo("00001");
		assertThat(parsed.getLanguageCode()).isEqualTo("*.*");
	}

	// -------------------------------------------------------------------------
	// AccessiblePlaylists
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_accessiblePlaylists_accessToAll() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.getAccessiblePlaylists().setAccessToAllFlag(true);
		original.getAccessiblePlaylists().setPlaylistNames(List.of());

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getAccessiblePlaylists().isAccessToAllFlag()).isTrue();
	}

	@Test
	void roundTrip_accessiblePlaylists_withNames() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.getAccessiblePlaylists().setPlaylistNames(List.of("00001", "00002"));

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getAccessiblePlaylists().getPlaylistNames()).containsExactly("00001", "00002");
	}

	// -------------------------------------------------------------------------
	// Application Management Table
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_noApps() throws Exception {
		Bdjo original = buildMinimalBdjo();
		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getApplications()).isEmpty();
	}

	@Test
	void roundTrip_singleApp_coreFields() throws Exception {
		Bdjo original = buildBdjoWithOneApp();

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getApplications()).hasSize(1);

		Bdjo.BdjoApp app = reparsed.getApplications().get(0);
		assertThat(app.getControlCode()).isEqualTo(1);
		assertThat(app.getType()).isEqualTo(1);
		assertThat(app.getOrganizationId()).isEqualTo(0x7FFF0C8EL);
		assertThat(app.getApplicationId()).isEqualTo(0x4001);
	}

	@Test
	void roundTrip_singleApp_profiles() throws Exception {
		Bdjo reparsed = roundTrip(buildBdjoWithOneApp());
		Bdjo.BdjoApp app = reparsed.getApplications().get(0);

		assertThat(app.getProfiles()).hasSize(2);
		assertThat(app.getProfiles().get(0).getProfileNumber()).isEqualTo(1);
		assertThat(app.getProfiles().get(0).getMajorVersion()).isEqualTo(1);
		assertThat(app.getProfiles().get(0).getMinorVersion()).isEqualTo(0);
		assertThat(app.getProfiles().get(0).getMicroVersion()).isEqualTo(0);
		assertThat(app.getProfiles().get(1).getProfileNumber()).isEqualTo(2);
	}

	@Test
	void roundTrip_singleApp_priority() throws Exception {
		Bdjo reparsed = roundTrip(buildBdjoWithOneApp());
		assertThat(reparsed.getApplications().get(0).getPriority()).isEqualTo(200);
	}

	@Test
	void roundTrip_singleApp_bindingAndVisibility() throws Exception {
		Bdjo reparsed = roundTrip(buildBdjoWithOneApp());
		Bdjo.BdjoApp app = reparsed.getApplications().get(0);
		assertThat(app.getBinding()).isEqualTo(3);
		assertThat(app.getVisibility()).isEqualTo(1);
	}

	@Test
	void roundTrip_singleApp_names() throws Exception {
		Bdjo reparsed = roundTrip(buildBdjoWithOneApp());
		Bdjo.BdjoApp app = reparsed.getApplications().get(0);
		assertThat(app.getNames()).hasSize(1);
		assertThat(app.getNames().get(0).getLanguage()).isEqualTo("eng");
		assertThat(app.getNames().get(0).getName()).isEqualTo("MyXlet");
	}

	@Test
	void roundTrip_singleApp_strings() throws Exception {
		Bdjo reparsed = roundTrip(buildBdjoWithOneApp());
		Bdjo.BdjoApp app = reparsed.getApplications().get(0);
		assertThat(app.getIconLocator()).isEmpty();
		assertThat(app.getBaseDir()).isEqualTo("00000");
		assertThat(app.getClasspathExtension()).isEmpty();
		assertThat(app.getInitialClass()).isEqualTo("com.example.MyXlet");
	}

	@Test
	void roundTrip_singleApp_parameters() throws Exception {
		Bdjo reparsed = roundTrip(buildBdjoWithOneApp());
		assertThat(reparsed.getApplications().get(0).getParameters()).isEmpty();
	}

	@Test
	void roundTrip_twoApps() throws Exception {
		Bdjo original = buildBdjoWithOneApp();
		Bdjo.BdjoApp app2 = buildApp(2, "SecondXlet", "com.example.SecondXlet");
		original.getApplications().add(app2);

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getApplications()).hasSize(2);
		assertThat(reparsed.getApplications().get(0).getInitialClass()).isEqualTo("com.example.MyXlet");
		assertThat(reparsed.getApplications().get(1).getInitialClass()).isEqualTo("com.example.SecondXlet");
	}

	@Test
	void roundTrip_appWithParameters() throws Exception {
		Bdjo original = buildBdjoWithOneApp();
		original.getApplications().get(0).setParameters(List.of("param1", "param2"));

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getApplications().get(0).getParameters()).containsExactly("param1", "param2");
	}

	@Test
	void roundTrip_appWithIconLocator() throws Exception {
		Bdjo original = buildBdjoWithOneApp();
		original.getApplications().get(0).setIconLocator("icon.png");

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getApplications().get(0).getIconLocator()).isEqualTo("icon.png");
	}

	// -------------------------------------------------------------------------
	// KeyInterestTable
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_keyInterestTable_allFalse() throws Exception {
		Bdjo reparsed = roundTrip(buildMinimalBdjo());
		Bdjo.KeyInterestTable kit = reparsed.getKeyInterestTable();
		assertThat(kit.isVkPlay()).isFalse();
		assertThat(kit.isVkStop()).isFalse();
		assertThat(kit.isVkTrackNext()).isFalse();
	}

	@Test
	void roundTrip_keyInterestTable_variousFlags() throws Exception {
		Bdjo original = buildMinimalBdjo();
		Bdjo.KeyInterestTable kit = original.getKeyInterestTable();
		kit.setVkPlay(true);
		kit.setVkStop(true);
		kit.setVkTrackNext(true);
		kit.setVkPause(true);
		kit.setPgTextstEnaDis(true);

		Bdjo reparsed = roundTrip(original);
		Bdjo.KeyInterestTable rKit = reparsed.getKeyInterestTable();
		assertThat(rKit.isVkPlay()).isTrue();
		assertThat(rKit.isVkStop()).isTrue();
		assertThat(rKit.isVkFfw()).isFalse();
		assertThat(rKit.isVkRew()).isFalse();
		assertThat(rKit.isVkTrackNext()).isTrue();
		assertThat(rKit.isVkTrackPrev()).isFalse();
		assertThat(rKit.isVkPause()).isTrue();
		assertThat(rKit.isVkStillOff()).isFalse();
		assertThat(rKit.isVkSecAudioEnaDis()).isFalse();
		assertThat(rKit.isVkSecVideoEnaDis()).isFalse();
		assertThat(rKit.isPgTextstEnaDis()).isTrue();
	}

	// -------------------------------------------------------------------------
	// FileAccessInfo
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_fileAccessInfo_dot() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.setFileAccessInfo(".");

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getFileAccessInfo()).isEqualTo(".");
	}

	@Test
	void roundTrip_fileAccessInfo_empty() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.setFileAccessInfo("");

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getFileAccessInfo()).isEmpty();
	}

	@Test
	void roundTrip_fileAccessInfo_multiPath() throws Exception {
		Bdjo original = buildMinimalBdjo();
		original.setFileAccessInfo("BDMV;CERTIFICATE");

		Bdjo reparsed = roundTrip(original);
		assertThat(reparsed.getFileAccessInfo()).isEqualTo("BDMV;CERTIFICATE");
	}

	// -------------------------------------------------------------------------
	// Full round-trip with complex model
	// -------------------------------------------------------------------------

	@Test
	void roundTrip_fullBdjo() throws Exception {
		Bdjo original = buildFullBdjo();
		Bdjo reparsed = roundTrip(original);

		// Terminal info
		assertThat(reparsed.getTerminalInfo().getDefaultFont()).isEqualTo("*****");
		assertThat(reparsed.getTerminalInfo().getInitialHaviConfigId()).isEqualTo(1);
		assertThat(reparsed.getTerminalInfo().isMenuCallMask()).isTrue();
		assertThat(reparsed.getTerminalInfo().isTitleSearchMask()).isTrue();

		// App cache
		assertThat(reparsed.getAppCacheInfo().getItems()).hasSize(1);

		// Accessible playlists
		assertThat(reparsed.getAccessiblePlaylists().isAccessToAllFlag()).isTrue();

		// Apps
		assertThat(reparsed.getApplications()).hasSize(2);

		// Key interest
		assertThat(reparsed.getKeyInterestTable().isVkTrackNext()).isTrue();

		// File access
		assertThat(reparsed.getFileAccessInfo()).isEqualTo(".");
	}

	// -------------------------------------------------------------------------
	// Builders
	// -------------------------------------------------------------------------

	private Bdjo buildMinimalBdjo() {
		Bdjo bdjo = new Bdjo();
		bdjo.setVersion("0200");
		bdjo.setTerminalInfo(new Bdjo.TerminalInfo());
		Bdjo.AppCacheInfo aci = new Bdjo.AppCacheInfo();
		aci.setItems(List.of());
		bdjo.setAppCacheInfo(aci);
		Bdjo.AccessiblePlaylists ap = new Bdjo.AccessiblePlaylists();
		ap.setPlaylistNames(List.of());
		bdjo.setAccessiblePlaylists(ap);
		bdjo.setApplications(new ArrayList<>());
		bdjo.setKeyInterestTable(new Bdjo.KeyInterestTable());
		bdjo.setFileAccessInfo("");
		return bdjo;
	}

	private Bdjo buildBdjoWithOneApp() {
		Bdjo bdjo = buildMinimalBdjo();
		bdjo.setApplications(new ArrayList<>(List.of(buildApp(1, "MyXlet", "com.example.MyXlet"))));
		bdjo.setFileAccessInfo(".");
		return bdjo;
	}

	private Bdjo.BdjoApp buildApp(int controlCode, String name, String initialClass) {
		Bdjo.BdjoApp app = new Bdjo.BdjoApp();
		app.setControlCode(controlCode);
		app.setType(1);
		app.setOrganizationId(0x7FFF0C8EL);
		app.setApplicationId(0x4001);

		Bdjo.AppProfile prof1 = new Bdjo.AppProfile();
		prof1.setProfileNumber(1);
		prof1.setMajorVersion(1);
		Bdjo.AppProfile prof2 = new Bdjo.AppProfile();
		prof2.setProfileNumber(2);
		prof2.setMajorVersion(1);
		app.setProfiles(List.of(prof1, prof2));

		app.setPriority(200);
		app.setBinding(3);
		app.setVisibility(1);

		Bdjo.AppName appName = new Bdjo.AppName();
		appName.setLanguage("eng");
		appName.setName(name);
		app.setNames(List.of(appName));

		app.setIconLocator("");
		app.setIconFlags(0);
		app.setBaseDir("00000");
		app.setClasspathExtension("");
		app.setInitialClass(initialClass);
		app.setParameters(List.of());
		return app;
	}

	private Bdjo buildFullBdjo() {
		Bdjo bdjo = new Bdjo();
		bdjo.setVersion("0200");

		Bdjo.TerminalInfo ti = new Bdjo.TerminalInfo();
		ti.setDefaultFont("*****");
		ti.setInitialHaviConfigId(1);
		ti.setMenuCallMask(true);
		ti.setTitleSearchMask(true);
		bdjo.setTerminalInfo(ti);

		Bdjo.AppCacheItem cacheItem = new Bdjo.AppCacheItem();
		cacheItem.setType(1);
		cacheItem.setRefToName("00000");
		cacheItem.setLanguageCode("*.*");
		Bdjo.AppCacheInfo aci = new Bdjo.AppCacheInfo();
		aci.setItems(List.of(cacheItem));
		bdjo.setAppCacheInfo(aci);

		Bdjo.AccessiblePlaylists ap = new Bdjo.AccessiblePlaylists();
		ap.setAccessToAllFlag(true);
		ap.setPlaylistNames(List.of());
		bdjo.setAccessiblePlaylists(ap);

		bdjo.setApplications(new ArrayList<>(List.of(buildApp(2, "StandardMenuXlet", "com.dis.radius.StandardMenuXlet"),
				buildApp(1, "TitleBoundXlet", "com.dis.radius.TitleBoundXlet"))));

		Bdjo.KeyInterestTable kit = new Bdjo.KeyInterestTable();
		kit.setVkTrackNext(true);
		bdjo.setKeyInterestTable(kit);

		bdjo.setFileAccessInfo(".");
		return bdjo;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private Bdjo roundTrip(Bdjo original) throws Exception {
		return parser.parse(new ByteArrayInputStream(writeToBytes(original)));
	}

	private byte[] writeToBytes(Bdjo bdjo) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writer.write(bdjo, out);
		return out.toByteArray();
	}

}
