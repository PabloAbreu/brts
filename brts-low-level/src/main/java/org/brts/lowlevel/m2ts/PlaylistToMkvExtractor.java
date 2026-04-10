package org.brts.lowlevel.m2ts;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.brts.common.m2ts.M2tsDemuxer;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.PlayItemStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts a full Blu-ray playlist into a single Matroska (MKV) file.
 * <p>
 * The extractor iterates over the playlist's {@link PlayItem}s, demuxes the
 * corresponding M2TS files, and writes all selected elementary streams into a
 * single MKV container using jebml. Chapter marks from the playlist are
 * preserved.
 *
 * <h2>Stream selection</h2>
 * The caller controls which audio and subtitle streams are retained via PID
 * filters. The <em>video</em> stream is always included (exactly one video
 * track per playlist is expected).
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MoviePlaylist playlist = new MoviePlaylistParser().parse(mplsPath);
 * new PlaylistToMkvExtractor().extract(
 *         bdmvStreamDir,      // e.g. /disc/BDMV/STREAM
 *         playlist,
 *         outputMkvPath,
 *         Set.of(4352),       // audio PIDs to keep (null = all)
 *         Set.of(4608)        // subtitle PIDs to keep (null = all)
 * );
 * }</pre>
 */
public class PlaylistToMkvExtractor {

    private static final Logger log = LoggerFactory.getLogger(PlaylistToMkvExtractor.class);

    private final M2tsParser parser = new M2tsParser();
    private final M2tsDemuxer demuxer = new M2tsDemuxer();

    /**
     * Extracts the given playlist into an MKV file, retaining all audio and
     * subtitle streams.
     */
    public void extract(Path streamDir, MoviePlaylist playlist, Path outputMkv) throws IOException {
        extract(streamDir, playlist, outputMkv, null, null);
    }

    /**
     * Extracts the given playlist into an MKV file.
     *
     * @param streamDir   directory containing M2TS files
     *                    (e.g. {@code /disc/BDMV/STREAM})
     * @param playlist    parsed MPLS playlist
     * @param outputMkv   path to the output MKV file
     * @param audioPids   audio PIDs to retain; {@code null} or empty = all audio
     * @param subtitlePids subtitle PIDs to retain; {@code null} or empty = all subs
     * @throws IOException on I/O error
     */
    public void extract(Path streamDir, MoviePlaylist playlist, Path outputMkv,
                        Set<Integer> audioPids, Set<Integer> subtitlePids) throws IOException {

        List<PlayItem> items = playlist.getPlayItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Playlist has no PlayItems");
        }

        log.info("Playlist→MKV: {} play items → {}", items.size(), outputMkv);

        // --- Determine PIDs from the first play item's stream table ----------
        // (Blu-ray playlists typically have the same streams across all items)
        Set<Integer> selectedPids = resolveSelectedPids(items.get(0), audioPids, subtitlePids);
        log.info("  Selected PIDs: {}", selectedPids.stream()
                .map(p -> "0x" + Integer.toHexString(p))
                .collect(Collectors.joining(", ")));

        // --- Build a merged M2tsInfo from the first clip for track setup -----
        Path firstClip = resolveClipPath(streamDir, items.get(0).getClipName());
        M2tsInfo firstInfo = parser.parse(firstClip);

        // Filter the M2tsInfo to only contain selected PIDs
        M2tsInfo filteredInfo = filterInfo(firstInfo, selectedPids);

        // --- Open the MKV handler and demux each play item sequentially ------
        try (MkvPacketHandler handler = new MkvPacketHandler(filteredInfo, outputMkv, selectedPids)) {

            for (int i = 0; i < items.size(); i++) {
                PlayItem item = items.get(i);
                Path clipPath = resolveClipPath(streamDir, item.getClipName());

                log.info("  Demuxing play item {}/{}: {} (in={} out={})",
                        i + 1, items.size(), item.getClipName(),
                        item.getInTimeTicks(), item.getOutTimeTicks());

                M2tsInfo clipInfo = (i == 0) ? firstInfo : parser.parse(clipPath);
                demuxer.demux(clipPath, clipInfo, handler, selectedPids);
            }

            // Write chapter marks into the MKV (must happen after demux so basePts is known)
            if (playlist.getPlayMarks() != null && !playlist.getPlayMarks().isEmpty()) {
                handler.writeChapters(playlist.getPlayMarks());
            }
        }

        log.info("Playlist→MKV complete: {}", outputMkv);
    }

    // -------------------------------------------------------------------------
    // PID selection
    // -------------------------------------------------------------------------

    /**
     * Builds the set of PIDs to include: all video + selected audio + selected
     * subtitles.
     */
    private Set<Integer> resolveSelectedPids(PlayItem playItem,
                                             Set<Integer> audioPids,
                                             Set<Integer> subtitlePids) {
        Set<Integer> result = new LinkedHashSet<>();
        if (playItem.getStreams() == null)
            return result;

        for (PlayItemStream s : playItem.getStreams()) {
            StreamCodingType ct = s.getCodingType();
            if (ct == null) continue;

            if (ct.isVideo()) {
                // Always include the video stream
                result.add(s.getPid());
            } else if (ct.isAudio()) {
                if (audioPids == null || audioPids.isEmpty() || audioPids.contains(s.getPid()))
                    result.add(s.getPid());
            } else if (ct.isSubtitle()) {
                if (subtitlePids == null || subtitlePids.isEmpty() || subtitlePids.contains(s.getPid()))
                    result.add(s.getPid());
            }
            // Menus (IG) are not included in MKV output
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path resolveClipPath(Path streamDir, String clipName) {
        return streamDir.resolve(clipName + ".m2ts");
    }

    /**
     * Returns a copy of the given info with only the streams matching
     * {@code pids}.
     */
    private M2tsInfo filterInfo(M2tsInfo info, Set<Integer> pids) {
        M2tsInfo filtered = new M2tsInfo();
        filtered.setSourcePath(info.getSourcePath());
        filtered.setTotalPackets(info.getTotalPackets());
        filtered.setFirstAts27MHz(info.getFirstAts27MHz());
        filtered.setLastAts27MHz(info.getLastAts27MHz());
        filtered.setFirstPcr27MHz(info.getFirstPcr27MHz());
        filtered.setLastPcr27MHz(info.getLastPcr27MHz());
        filtered.setPmtPid(info.getPmtPid());
        filtered.setPcrPid(info.getPcrPid());

        if (info.getStreams() != null) {
            List<M2tsStreamInfo> filteredStreams = info.getStreams().stream()
                    .filter(s -> pids.contains(s.getPid()))
                    .collect(Collectors.toList());
            filtered.setStreams(filteredStreams);
        }
        return filtered;
    }
}
