package org.brts.lowlevel.roundtrip;

import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.clpi.ClipStream;
import org.brts.lowlevel.model.clpi.EpMap;
import org.brts.lowlevel.parser.ClipInfoParser;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

/**
 * Round-trip test using real CLPI files from the samples directory.
 * <p>
 * For each sample: parse the real file, write the model back to bytes,
 * re-parse the written bytes, and compare the two parsed models.
 */
class ClipInfoRealFileRoundTripTest {

    private static final Path SAMPLES_DIR = Path.of("samples/PB/BDMV/CLIPINF");

    private final ClipInfoParser parser = new ClipInfoParser();
    private final ClipInfoWriter writer = new ClipInfoWriter();

    @Test
    void roundTrip_largestSampleFile_allFieldsPreserved() throws Exception {
        // 00705.clpi is the largest sample (~39 KB, has EP_map data)
        Path sampleFile = SAMPLES_DIR.resolve("00705.clpi");
        if (!Files.exists(sampleFile)) return; // skip if samples not present

        ClipInfo first = parser.parse(sampleFile);
        ClipInfo second = writeAndReparse(first);

        assertClipInfoEquivalent(first, second);
    }

    @Test
    void roundTrip_mediumSampleFile_allFieldsPreserved() throws Exception {
        // 00748.clpi is a medium-sized sample with EP_map
        Path sampleFile = SAMPLES_DIR.resolve("00748.clpi");
        if (!Files.exists(sampleFile)) return;

        ClipInfo first = parser.parse(sampleFile);
        ClipInfo second = writeAndReparse(first);

        assertClipInfoEquivalent(first, second);
    }

    @Test
    void roundTrip_smallSampleFile_allFieldsPreserved() throws Exception {
        // 00300.clpi — a smaller sample
        Path sampleFile = SAMPLES_DIR.resolve("00300.clpi");
        if (!Files.exists(sampleFile)) return;

        ClipInfo first = parser.parse(sampleFile);
        ClipInfo second = writeAndReparse(first);

        assertClipInfoEquivalent(first, second);
    }

    @Test
    void roundTrip_allSamples_basicFieldsPreserved() throws Exception {
        if (!Files.isDirectory(SAMPLES_DIR)) return;

        List<Path> sampleFiles = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(SAMPLES_DIR, "*.clpi")) {
            ds.forEach(sampleFiles::add);
        }
        assertThat(sampleFiles).isNotEmpty();

        for (Path file : sampleFiles) {
            ClipInfo first = parser.parse(file);
            ClipInfo second = writeAndReparse(first);

            assertThat(second.getClipStreamType())
                    .as("clipStreamType for %s", file.getFileName())
                    .isEqualTo(first.getClipStreamType());
            assertThat(second.getApplicationType())
                    .as("applicationType for %s", file.getFileName())
                    .isEqualTo(first.getApplicationType());
            assertThat(second.getTsRecordingStartPts().getTicks())
                    .as("startPts for %s", file.getFileName())
                    .isEqualTo(first.getTsRecordingStartPts().getTicks());
            assertThat(second.getTsRecordingEndPts().getTicks())
                    .as("endPts for %s", file.getFileName())
                    .isEqualTo(first.getTsRecordingEndPts().getTicks());
            assertThat(second.getStreams()).hasSameSizeAs(first.getStreams());

            // EP map: either both null or same stream count
            if (first.getEpMap() != null) {
                assertThat(second.getEpMap()).isNotNull();
                assertThat(second.getEpMap().getStreams())
                        .hasSameSizeAs(first.getEpMap().getStreams());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ClipInfo writeAndReparse(ClipInfo model) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.write(model, out);
        return parser.parse(new ByteArrayInputStream(out.toByteArray()));
    }

    private void assertClipInfoEquivalent(ClipInfo first, ClipInfo second) {
        // Top-level fields
        assertThat(second.getClipStreamType()).isEqualTo(first.getClipStreamType());
        assertThat(second.getApplicationType()).isEqualTo(first.getApplicationType());
        assertThat(second.isAtcDelta()).isEqualTo(first.isAtcDelta());
        assertThat(second.getTsRecordingRate()).isEqualTo(first.getTsRecordingRate());
        assertThat(second.getNumSourcePackets()).isEqualTo(first.getNumSourcePackets());

        // Timing
        assertThat(second.getTsRecordingStartPts().getTicks())
                .isEqualTo(first.getTsRecordingStartPts().getTicks());
        assertThat(second.getTsRecordingEndPts().getTicks())
                .isEqualTo(first.getTsRecordingEndPts().getTicks());
        assertThat(second.getDuration().getTicks())
                .isCloseTo(first.getDuration().getTicks(), offset(2L));

        // Streams
        assertThat(second.getStreams()).hasSameSizeAs(first.getStreams());
        for (int i = 0; i < first.getStreams().size(); i++) {
            ClipStream s1 = first.getStreams().get(i);
            ClipStream s2 = second.getStreams().get(i);
            assertThat(s2.getPid()).as("stream[%d].pid", i).isEqualTo(s1.getPid());
            if (s1.getCodingType() != null) {
                assertThat(s2.getCodingType()).as("stream[%d].codingType", i).isEqualTo(s1.getCodingType());
            }
            if (s1.getLanguage() != null) {
                assertThat(s2.getLanguage()).as("stream[%d].language", i).isEqualTo(s1.getLanguage());
            }
        }

        // EP map
        if (first.getEpMap() == null) {
            assertThat(second.getEpMap()).isNull();
            return;
        }
        assertThat(second.getEpMap()).isNotNull();
        assertThat(second.getEpMap().getStreams()).hasSameSizeAs(first.getEpMap().getStreams());

        for (int s = 0; s < first.getEpMap().getStreams().size(); s++) {
            EpMap.EpMapStream eps1 = first.getEpMap().getStreams().get(s);
            EpMap.EpMapStream eps2 = second.getEpMap().getStreams().get(s);
            assertThat(eps2.getPid()).as("epStream[%d].pid", s).isEqualTo(eps1.getPid());
            assertThat(eps2.getEpType()).as("epStream[%d].epType", s).isEqualTo(eps1.getEpType());
            assertThat(eps2.getEntries()).as("epStream[%d].entries.size", s).hasSameSizeAs(eps1.getEntries());

            for (int e = 0; e < eps1.getEntries().size(); e++) {
                EpMap.EpMapEntry e1 = eps1.getEntries().get(e);
                EpMap.EpMapEntry e2 = eps2.getEntries().get(e);
                assertThat(e2.getPtsTicks())
                        .as("ep[%d][%d].ptsTicks", s, e)
                        .isEqualTo(e1.getPtsTicks());
                assertThat(e2.getSpn())
                        .as("ep[%d][%d].spn", s, e)
                        .isEqualTo(e1.getSpn());
                assertThat(e2.isAngleChangePoint())
                        .as("ep[%d][%d].angleChange", s, e)
                        .isEqualTo(e1.isAngleChangePoint());
                assertThat(e2.getIEndPositionOffset())
                        .as("ep[%d][%d].iEndPosOffset", s, e)
                        .isEqualTo(e1.getIEndPositionOffset());
            }
        }
    }
}
