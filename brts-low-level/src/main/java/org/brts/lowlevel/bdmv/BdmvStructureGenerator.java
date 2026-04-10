package org.brts.lowlevel.bdmv;

import org.brts.lowlevel.model.bdmv.IndexBdmv;
import org.brts.lowlevel.model.bdmv.MovieObjects;
import org.brts.lowlevel.model.clpi.ClipInfo;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.writer.ClipInfoWriter;
import org.brts.lowlevel.writer.MoviePlaylistWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the complete Blu-ray disc folder structure from the low-level model objects.
 * <p>
 * Output layout:
 * <pre>
 * &lt;outputRoot&gt;/
 *   BDMV/
 *     index.bdmv
 *     MovieObject.bdmv
 *     BACKUP/
 *       index.bdmv       (identical copy)
 *       MovieObject.bdmv (identical copy)
 *     PLAYLIST/
 *       00001.mpls  ...
 *     CLIPINF/
 *       00001.clpi  ...
 *     STREAM/
 *       00001.m2ts  ...  (not generated here — written by M2tsWriter)
 *     META/
 *       DL/              (placeholder, empty)
 *   CERTIFICATE/
 *     BACKUP/            (placeholder, empty)
 * </pre>
 */
public class BdmvStructureGenerator {

    private static final Logger log = LoggerFactory.getLogger(BdmvStructureGenerator.class);

    private final ClipInfoWriter     clipInfoWriter     = new ClipInfoWriter();
    private final MoviePlaylistWriter playlistWriter     = new MoviePlaylistWriter();
    private final IndexBdmvWriter                              indexWriter         = new IndexBdmvWriter();
    private final MovieObjectsWriter                           movieObjectsWriter  = new MovieObjectsWriter();

    /**
     * Creates the full Blu-ray directory skeleton at {@code outputRoot}.
     * Call {@link #writeClip}, {@link #writePlaylist} etc. to populate content.
     */
    public void initStructure(Path outputRoot) throws IOException {
        log.info("Initialising BDMV structure at {}", outputRoot);
        createDir(outputRoot.resolve("BDMV/PLAYLIST"));
        createDir(outputRoot.resolve("BDMV/CLIPINF"));
        createDir(outputRoot.resolve("BDMV/STREAM"));
        createDir(outputRoot.resolve("BDMV/BACKUP/PLAYLIST"));
        createDir(outputRoot.resolve("BDMV/BACKUP/CLIPINF"));
        createDir(outputRoot.resolve("BDMV/META/DL"));
        createDir(outputRoot.resolve("CERTIFICATE/BACKUP"));
    }

    public void writeIndex(Path outputRoot, IndexBdmv index) throws IOException {
        Path out = outputRoot.resolve("BDMV/index.bdmv");
        indexWriter.write(index, out);
        indexWriter.write(index, outputRoot.resolve("BDMV/BACKUP/index.bdmv"));
        log.info("Wrote {}", out);
    }

    public void writeMovieObjects(Path outputRoot, MovieObjects objects) throws IOException {
        Path out = outputRoot.resolve("BDMV/MovieObject.bdmv");
        movieObjectsWriter.write(objects, out);
        movieObjectsWriter.write(objects, outputRoot.resolve("BDMV/BACKUP/MovieObject.bdmv"));
        log.info("Wrote {}", out);
    }

    public void writePlaylist(Path outputRoot, String name, MoviePlaylist playlist) throws IOException {
        Path out = outputRoot.resolve("BDMV/PLAYLIST/" + name + ".mpls");
        playlistWriter.write(playlist, out);
        playlistWriter.write(playlist, outputRoot.resolve("BDMV/BACKUP/PLAYLIST/" + name + ".mpls"));
        log.info("Wrote {}", out);
    }

    public void writeClip(Path outputRoot, String name, ClipInfo clip) throws IOException {
        Path out = outputRoot.resolve("BDMV/CLIPINF/" + name + ".clpi");
        clipInfoWriter.write(clip, out);
        clipInfoWriter.write(clip, outputRoot.resolve("BDMV/BACKUP/CLIPINF/" + name + ".clpi"));
        log.info("Wrote {}", out);
    }

    private void createDir(Path dir) throws IOException {
        Files.createDirectories(dir);
    }
}
