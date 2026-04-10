package org.brts.lowlevel.pgs;

import org.brts.lowlevel.igs.PaletteBuilder;
import org.brts.lowlevel.igs.RleEncoder;
import org.brts.lowlevel.igs.model.*;
import org.brts.lowlevel.pgs.model.PgsCompositionSegment;
import org.brts.lowlevel.pgs.model.PgsDisplaySet;
import org.brts.lowlevel.subtitle.model.SubtitleCue;
import org.brts.lowlevel.subtitle.model.SubtitleTrack;
import org.brts.lowlevel.subtitle.parser.SubtitleParser;
import org.brts.lowlevel.subtitle.parser.SubtitleParsers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates a complete PGS (Presentation Graphic Stream) elementary stream
 * file from a parsed {@link SubtitleTrack} or directly from a subtitle file.
 * <p>
 * The generator orchestrates the full pipeline:
 * <ol>
 *   <li>Parse subtitle cues (if starting from a file)</li>
 *   <li>For each cue: render text → {@link BufferedImage}</li>
 *   <li>Build palette via {@link PaletteBuilder}</li>
 *   <li>RLE-encode via {@link RleEncoder}</li>
 *   <li>Build a "show" display set (epoch start) with the bitmap</li>
 *   <li>Build a "clear" display set (normal) to remove it at the cue's end time</li>
 *   <li>Encode all display sets via {@link PgsMuxer} to the output file</li>
 * </ol>
 * <p>
 * The resulting {@code .sup} / {@code .pgs} file can be muxed into an M2TS
 * using the existing {@link org.brts.common.m2ts.M2tsWriter} (which already
 * supports {@code PRESENTATION_GRAPHICS} streams).
 */
public class PgsGenerator {

    private static final Logger log = LoggerFactory.getLogger(PgsGenerator.class);

    /** 90 kHz clock ticks per millisecond. */
    private static final long TICKS_PER_MS = 90;

    private final PgsRenderConfig config;

    public PgsGenerator(PgsRenderConfig config) {
        this.config = config;
    }

    /**
     * Generates a PGS file from a subtitle file (SRT, SSA, ASS).
     *
     * @param subtitleFile path to the source subtitle file
     * @param outputFile   path for the output PGS elementary stream (.sup)
     * @throws IOException on I/O or parse error
     */
    public void generate(Path subtitleFile, Path outputFile) throws IOException {
        SubtitleParser parser = SubtitleParsers.forFile(subtitleFile);
        SubtitleTrack track = parser.parse(subtitleFile);
        generate(track, outputFile);
    }

    /**
     * Generates a PGS file from a pre-parsed subtitle track.
     *
     * @param track      the subtitle track
     * @param outputFile path for the output PGS elementary stream (.sup)
     * @throws IOException on I/O error
     */
    public void generate(SubtitleTrack track, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());

        PgsMuxer muxer = new PgsMuxer();
        PgsSubtitleRenderer renderer = new PgsSubtitleRenderer(config);

        int compositionNumber = 0;

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(outputFile))) {

            List<SubtitleCue> cues = track.getCues();
            log.info("Generating PGS from {} cues ({} format)", cues.size(), track.getFormat());

            for (int i = 0; i < cues.size(); i++) {
                SubtitleCue cue = cues.get(i);

                if (cue.getText() == null || cue.getText().isBlank()) {
                    log.debug("Skipping empty cue #{}", cue.getNumber());
                    continue;
                }

                // Render subtitle text to bitmap
                PgsSubtitleRenderer.RenderResult result =
                        renderer.render(cue.getText(), cue.getPosition());

                BufferedImage image = result.image();
                int screenX = result.screenX();
                int screenY = result.screenY();

                // Build palette from the rendered image
                IgsPalette palette = PaletteBuilder.buildFromImages(0, image);

                // RLE-encode the image
                byte[] rleData = RleEncoder.encode(image, palette);

                // Build the "show" display set (epoch start)
                PgsDisplaySet showDs = buildShowDisplaySet(
                        compositionNumber++, palette, image, rleData,
                        screenX, screenY);

                long showPts = cue.getStartTimeMs() * TICKS_PER_MS;
                muxer.writeDisplaySet(out, showDs, showPts);

                // Build the "clear" display set (removes subtitle at end time)
                // Only emit clear if there's no immediately following cue that overlaps
                long clearPts = cue.getEndTimeMs() * TICKS_PER_MS;

                boolean nextOverlaps = (i + 1 < cues.size())
                        && (cues.get(i + 1).getStartTimeMs() <= cue.getEndTimeMs());

                if (!nextOverlaps) {
                    PgsDisplaySet clearDs = buildClearDisplaySet(compositionNumber++);
                    muxer.writeDisplaySet(out, clearDs, clearPts);
                }
            }
        }

        log.info("PGS written: {} ({} composition segments)",
                outputFile, compositionNumber);
    }

    // ── Display set builders ────────────────────────────────────────────────

    /**
     * Builds a "show" display set that displays a subtitle bitmap.
     * Uses epoch start composition state.
     */
    private PgsDisplaySet buildShowDisplaySet(
            int compositionNumber,
            IgsPalette palette,
            BufferedImage image,
            byte[] rleData,
            int screenX, int screenY) {

        PgsDisplaySet ds = new PgsDisplaySet();
        ds.setEpochStart(true);

        // PCS
        PgsCompositionSegment pcs = new PgsCompositionSegment();

        VideoDescriptor vd = new VideoDescriptor();
        vd.setWidth(config.getScreenWidth());
        vd.setHeight(config.getScreenHeight());
        vd.setFrameRateCode(config.getFrameRateCode());
        pcs.setVideoDescriptor(vd);

        CompositionDescriptor cd = new CompositionDescriptor();
        cd.setNumber(compositionNumber & 0xFFFF);
        cd.setState(2); // epoch start
        pcs.setCompositionDescriptor(cd);

        pcs.setPaletteUpdateFlag(false);
        pcs.setPaletteIdRef(0);

        // Composition object — places the bitmap on screen
        CompositionObject co = new CompositionObject();
        co.setObjectIdRef(0);
        co.setWindowIdRef(0);
        co.setForcedOn(false);
        co.setX(screenX);
        co.setY(screenY);
        pcs.getCompositionObjects().add(co);

        ds.setCompositionSegment(pcs);

        // WDS — one window covering the subtitle area
        IgsWindowDefinition wds = new IgsWindowDefinition();
        IgsWindow window = new IgsWindow();
        window.setId(0);
        window.setX(screenX);
        window.setY(screenY);
        window.setWidth(image.getWidth());
        window.setHeight(image.getHeight());
        wds.getWindows().add(window);
        ds.getWindowDefinitions().add(wds);

        // PDS
        ds.getPalettes().add(palette);

        // ODS
        IgsObject obj = new IgsObject();
        obj.setId(0);
        obj.setVersion(0);
        obj.setWidth(image.getWidth());
        obj.setHeight(image.getHeight());
        obj.setRleData(rleData);
        obj.setDataLength(rleData.length + 4); // +4 for width(2)+height(2)

        SequenceDescriptor sd = new SequenceDescriptor();
        sd.setFirstInSequence(true);
        sd.setLastInSequence(true);
        obj.setSequenceDescriptor(sd);

        ds.getObjects().add(obj);

        return ds;
    }

    /**
     * Builds a "clear" display set that removes the on-screen subtitle.
     * Uses normal composition state with an empty composition object list.
     */
    private PgsDisplaySet buildClearDisplaySet(int compositionNumber) {
        PgsDisplaySet ds = new PgsDisplaySet();
        ds.setEpochStart(false);

        PgsCompositionSegment pcs = new PgsCompositionSegment();

        VideoDescriptor vd = new VideoDescriptor();
        vd.setWidth(config.getScreenWidth());
        vd.setHeight(config.getScreenHeight());
        vd.setFrameRateCode(config.getFrameRateCode());
        pcs.setVideoDescriptor(vd);

        CompositionDescriptor cd = new CompositionDescriptor();
        cd.setNumber(compositionNumber & 0xFFFF);
        cd.setState(0); // normal
        pcs.setCompositionDescriptor(cd);

        pcs.setPaletteUpdateFlag(false);
        pcs.setPaletteIdRef(0);
        // Empty composition objects list = clear screen

        ds.setCompositionSegment(pcs);

        return ds;
    }
}
