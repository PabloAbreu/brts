package org.brts.lowlevel.realdata;

import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.parser.IndexBdmvParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Integration tests for {@link IndexBdmvParser} against the real disc sample at
 * {@code samples/PB/BDMV/index.bdmv}.
 * <p>
 * All tests are skipped when the sample data is absent (e.g. on a CI agent without the disc files).
 * <p>
 * Disc structure notes verified by binary analysis:
 * <ul>
 *   <li>Version {@code "0200"}.</li>
 *   <li>AppInfoBDMV contains provider name {@code "Provider Name"}.</li>
 *   <li>First-play and top-menu are BD-J entries referencing BDJO files {@code "00002"} and {@code "00000"}
 *       respectively.</li>
 *   <li>90 title entries: a mix of BD-J (referencing {@code 00000}–{@code 00003} and {@code 12345}
 *       BDJO files) and HDMV stubs.</li>
 *   <li>All BDJO names referenced in title entries correspond to files actually present under
 *       {@code BDMV/BDJO/}.</li>
 * </ul>
 */
class RealDiscIndexBdmvParserTest {

    private static final Path BDMV_DIR = Paths.get("../samples/PB/BDMV");
    private static final Path INDEX_FILE = BDMV_DIR.resolve("index.bdmv");
    private static final Path BDJO_DIR   = BDMV_DIR.resolve("BDJO");

    private final IndexBdmvParser parser = new IndexBdmvParser();

    @BeforeAll
    static void requireSampleData() {
        assumeThat(Files.isRegularFile(INDEX_FILE))
                .as("sample disc data must be present at " + INDEX_FILE.toAbsolutePath())
                .isTrue();
    }

    // ------------------------------------------------------------------
    // Basic parseability
    // ------------------------------------------------------------------

    @Test
    void indexBdmvParsesWithoutException() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        assertThat(index).isNotNull();
    }

    // ------------------------------------------------------------------
    // Header / version
    // ------------------------------------------------------------------

    @Test
    void version_is_0200() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        assertThat(index.getVersion()).isEqualTo("0200");
    }

    // ------------------------------------------------------------------
    // AppInfoBDMV — content provider name
    // ------------------------------------------------------------------

    @Test
    void contentProviderName_isProviderName() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        assertThat(index.getContentProviderName()).isEqualTo("Provider Name");
    }

    // ------------------------------------------------------------------
    // First-play title
    // ------------------------------------------------------------------

    @Test
    void firstPlayTitle_isBdj() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        IndexBdmv.TitleEntry fp = index.getFirstPlayTitle();
        assertThat(fp).isNotNull();
        assertThat(fp.isBdj()).as("first-play must be a BD-J entry").isTrue();
    }

    @Test
    void firstPlayTitle_bdjoName_is_00002() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        assertThat(index.getFirstPlayTitle().getBdjObjectName()).isEqualTo("00002");
    }

    @Test
    void firstPlayTitle_accessType_isProhibited() throws IOException {
        // access_type == 0 in first-play entry of this disc
        IndexBdmv index = parser.parse(INDEX_FILE);
        assertThat(index.getFirstPlayTitle().getAccessType()).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Top-menu title
    // ------------------------------------------------------------------

    @Test
    void topMenuTitle_isBdj() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        IndexBdmv.TitleEntry tm = index.getTopMenuTitle();
        assertThat(tm).isNotNull();
        assertThat(tm.isBdj()).as("top-menu must be a BD-J entry").isTrue();
    }

    @Test
    void topMenuTitle_bdjoName_is_00000() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        assertThat(index.getTopMenuTitle().getBdjObjectName()).isEqualTo("00000");
    }

    // ------------------------------------------------------------------
    // Title table — count and type breakdown
    // ------------------------------------------------------------------

    @Test
    void numberOfTitles_is90() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        assertThat(index.getTitles()).hasSize(90);
    }

    @Test
    void titleList_containsBdjEntries() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        long bdjCount = index.getTitles().stream()
                .filter(IndexBdmv.TitleEntry::isBdj)
                .count();
        assertThat(bdjCount).as("at least some titles must be BD-J").isGreaterThan(0);
    }

    @Test
    void titleList_containsHdmvEntries() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        long hdmvCount = index.getTitles().stream()
                .filter(IndexBdmv.TitleEntry::isHdmv)
                .count();
        assertThat(hdmvCount).as("at least some titles must be HDMV stubs").isGreaterThan(0);
    }

    /**
     * Titles 1–48 (indices 0–47) are all BD-J entries on this disc.
     */
    @Test
    void firstFortyEightTitles_areAllBdj() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        List<IndexBdmv.TitleEntry> titles = index.getTitles();
        for (int i = 0; i < 48; i++) {
            assertThat(titles.get(i).isBdj())
                    .as("title %d (index %d) should be BD-J", i + 1, i)
                    .isTrue();
        }
    }

    /**
     * Exact title-type breakdown verified by binary analysis:
     * <ul>
     *   <li>56 BD-J entries</li>
     *   <li>34 HDMV entries</li>
     * </ul>
     */
    @Test
    void titleTypeBreakdown_bdjAndHdmvCounts() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        long bdjCount  = index.getTitles().stream().filter(IndexBdmv.TitleEntry::isBdj).count();
        long hdmvCount = index.getTitles().stream().filter(IndexBdmv.TitleEntry::isHdmv).count();
        assertThat(bdjCount).as("BD-J title count").isEqualTo(56);
        assertThat(hdmvCount).as("HDMV title count").isEqualTo(34);
    }

    // ------------------------------------------------------------------
    // Specific HDMV entries — verified by binary analysis
    // ------------------------------------------------------------------

    /**
     * Title 49 (index 48) is an HDMV entry with object ID 2.
     */
    @Test
    void title49_isHdmvWithObjectId2() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        IndexBdmv.TitleEntry t = index.getTitles().get(48); // 0-based
        assertThat(t.isHdmv()).as("title 49 must be HDMV").isTrue();
        assertThat(t.getHdmvObjectId()).as("title 49 hdmv_object_id").isEqualTo(2);
    }

    /**
     * Title 90 (index 89) is an HDMV entry with object ID 1.
     */
    @Test
    void title90_isHdmvWithObjectId1() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        IndexBdmv.TitleEntry t = index.getTitles().get(89); // 0-based
        assertThat(t.isHdmv()).as("title 90 must be HDMV").isTrue();
        assertThat(t.getHdmvObjectId()).as("title 90 hdmv_object_id").isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Specific BD-J entries — verified by binary analysis
    // ------------------------------------------------------------------

    /** Title 1 (index 0) is BD-J, referencing BDJO "00001". */
    @Test
    void title1_isBdj_00001() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        IndexBdmv.TitleEntry t = index.getTitles().get(0);
        assertThat(t.isBdj()).isTrue();
        assertThat(t.getBdjObjectName()).isEqualTo("00001");
    }

    /** Title 2 (index 1) is BD-J, referencing BDJO "00003". */
    @Test
    void title2_isBdj_00003() throws IOException {
        IndexBdmv index = parser.parse(INDEX_FILE);
        IndexBdmv.TitleEntry t = index.getTitles().get(1);
        assertThat(t.isBdj()).isTrue();
        assertThat(t.getBdjObjectName()).isEqualTo("00003");
    }

    // ------------------------------------------------------------------
    // Consistency with disc file system: all BDJO names must exist on disc
    // ------------------------------------------------------------------

    /**
     * Every BD-J BDJO name referenced in first-play, top-menu, or any title entry
     * must correspond to an actual {@code .bdjo} file present under {@code BDMV/BDJO/}.
     */
    @Test
    void allBdjoNames_haveCorrespondingFileOnDisc() throws IOException {
        assumeThat(Files.isDirectory(BDJO_DIR))
                .as("BDJO directory must be present at " + BDJO_DIR.toAbsolutePath())
                .isTrue();

        Set<String> availableBdjos = Files.list(BDJO_DIR)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".bdjo"))
                .map(n -> n.substring(0, n.length() - 5)) // strip ".bdjo"
                .collect(Collectors.toSet());

        IndexBdmv index = parser.parse(INDEX_FILE);

        // Collect all BD-J names from the index
        List<String> referencedNames = new java.util.ArrayList<>();
        if (index.getFirstPlayTitle() != null && index.getFirstPlayTitle().isBdj()) {
            referencedNames.add(index.getFirstPlayTitle().getBdjObjectName());
        }
        if (index.getTopMenuTitle() != null && index.getTopMenuTitle().isBdj()) {
            referencedNames.add(index.getTopMenuTitle().getBdjObjectName());
        }
        if (index.getTitles() != null) {
            index.getTitles().stream()
                    .filter(IndexBdmv.TitleEntry::isBdj)
                    .map(IndexBdmv.TitleEntry::getBdjObjectName)
                    .forEach(referencedNames::add);
        }

        assertThat(referencedNames).isNotEmpty();

        for (String name : referencedNames) {
            assertThat(availableBdjos)
                    .as("BDJO file '%s.bdjo' referenced in index must exist in BDMV/BDJO/", name)
                    .contains(name);
        }
    }

    /**
     * The set of BDJO names referenced in the index matches exactly the set of {@code .bdjo}
     * files present on the disc — neither references any file that doesn't exist, nor are there
     * unreferenced BDJO files.
     */
    @Test
    void referencedBdjoNames_matchExactlyFilesOnDisc() throws IOException {
        assumeThat(Files.isDirectory(BDJO_DIR))
                .as("BDJO directory must be present at " + BDJO_DIR.toAbsolutePath())
                .isTrue();

        Set<String> availableBdjos = Files.list(BDJO_DIR)
                .map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".bdjo"))
                .map(n -> n.substring(0, n.length() - 5))
                .collect(Collectors.toSet());

        // Expected on this disc: 00000, 00001, 00002, 00003, 12345
        assertThat(availableBdjos).containsExactlyInAnyOrder("00000", "00001", "00002", "00003", "12345");

        IndexBdmv index = parser.parse(INDEX_FILE);

        Set<String> referencedBdjos = new java.util.HashSet<>();
        if (index.getFirstPlayTitle() != null && index.getFirstPlayTitle().isBdj()) {
            referencedBdjos.add(index.getFirstPlayTitle().getBdjObjectName());
        }
        if (index.getTopMenuTitle() != null && index.getTopMenuTitle().isBdj()) {
            referencedBdjos.add(index.getTopMenuTitle().getBdjObjectName());
        }
        if (index.getTitles() != null) {
            index.getTitles().stream()
                    .filter(IndexBdmv.TitleEntry::isBdj)
                    .map(IndexBdmv.TitleEntry::getBdjObjectName)
                    .forEach(referencedBdjos::add);
        }

        // Every referenced BDJO must exist on disc
        assertThat(availableBdjos).containsAll(referencedBdjos);

        // Every BDJO file on disc must be referenced somewhere in the index
        assertThat(referencedBdjos).containsAll(availableBdjos);
    }
}
