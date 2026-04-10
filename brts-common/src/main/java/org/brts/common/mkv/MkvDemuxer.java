package org.brts.common.mkv;

import org.ebml.io.FileDataSource;
import org.ebml.matroska.MatroskaFile;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Demuxes an MKV (Matroska) container into separate elementary stream (ES) files,
 * one per track.
 * <p>
 * For H.264 (AVC) tracks stored in MKV's "avcC" codec-private format, the demuxer
 * prepends the SPS/PPS from the codec private data and converts length-prefixed
 * NAL units to Annex B start codes (00 00 00 01).
 * <p>
 * For other codecs (audio, HEVC, PGS, text subtitles), raw frame data is written
 * sequentially.
 * <p>
 * Usage:
 * <pre>{@code
 * MkvDemuxer demuxer = new MkvDemuxer();
 * Map<Integer, Path> files = demuxer.demux(mkvPath, outputDir);
 * // files maps track number → extracted ES file path
 * }</pre>
 */
public class MkvDemuxer {

    private static final Logger log = LoggerFactory.getLogger(MkvDemuxer.class);

    private static final byte[] ANNEX_B_START_CODE = {0x00, 0x00, 0x00, 0x01};

    /**
     * Demuxes all tracks from the given MKV file into separate ES files.
     *
     * @param mkvPath   path to the MKV file
     * @param outputDir directory where ES files are written
     * @return map from track number to the extracted ES file path
     * @throws IOException on I/O error
     */
    public Map<Integer, Path> demux(Path mkvPath, Path outputDir) throws IOException {
        return demux(mkvPath, outputDir, null);
    }

    /**
     * Demuxes selected tracks from the given MKV file into separate ES files.
     *
     * @param mkvPath      path to the MKV file
     * @param outputDir    directory where ES files are written
     * @param trackFilter  set of track numbers to extract (null = all)
     * @return map from track number to the extracted ES file path
     * @throws IOException on I/O error
     */
    public Map<Integer, Path> demux(Path mkvPath, Path outputDir, Set<Integer> trackFilter) throws IOException {
        Files.createDirectories(outputDir);
        Map<Integer, Path> result = new LinkedHashMap<>();

        try (FileDataSource dataSource = new FileDataSource(mkvPath.toAbsolutePath().toString())) {
            MatroskaFile mkv = new MatroskaFile(dataSource);
            mkv.readFile();

            // Build track metadata map
            Map<Integer, MatroskaFileTrack> trackMap = new LinkedHashMap<>();
            Map<Integer, Integer> nalLengthSizeMap = new HashMap<>();
            MatroskaFileTrack[] tracks = mkv.getTrackList();
            if (tracks != null) {
                for (MatroskaFileTrack t : tracks) {
                    int trackNo = t.getTrackNo();
                    if (trackFilter != null && !trackFilter.contains(trackNo)) {
                        continue;
                    }
                    trackMap.put(trackNo, t);

                    // For H.264 avcC format, parse the NAL length size
                    String codecId = t.getCodecID();
                    if ("V_MPEG4/ISO/AVC".equals(codecId)) {
                        ByteBuffer cp = t.getCodecPrivate();
                        if (cp != null && cp.remaining() >= 6) {
                            ByteBuffer dup = cp.duplicate();
                            dup.get(); // configVersion
                            dup.get(); // profile
                            dup.get(); // compat
                            dup.get(); // level
                            nalLengthSizeMap.put(trackNo, (dup.get() & 0x03) + 1);
                        } else {
                            nalLengthSizeMap.put(trackNo, 4);
                        }
                    }
                }
            }

            // Open output streams for each track
            Map<Integer, OutputStream> outputs = new LinkedHashMap<>();
            try {
                for (var entry : trackMap.entrySet()) {
                    int trackNo = entry.getKey();
                    MatroskaFileTrack t = entry.getValue();
                    String ext = extensionForCodec(t.getCodecID());
                    Path outFile = outputDir.resolve("track_" + trackNo + "." + ext);
                    result.put(trackNo, outFile);
                    OutputStream os = new BufferedOutputStream(Files.newOutputStream(outFile), 1 << 16);
                    outputs.put(trackNo, os);

                    // For H.264 tracks, write SPS/PPS from codec private data first
                    if ("V_MPEG4/ISO/AVC".equals(t.getCodecID())) {
                        writeAvcParameterSets(t.getCodecPrivate(), os);
                    }
                }

                // Read all frames
                long frameCount = 0;
                MatroskaFileFrame frame;
                while ((frame = mkv.getNextFrame()) != null) {
                    int trackNo = frame.getTrackNo();
                    OutputStream os = outputs.get(trackNo);
                    if (os == null) continue;

                    ByteBuffer data = frame.getData();
                    if (data == null || !data.hasRemaining()) continue;

                    MatroskaFileTrack track = trackMap.get(trackNo);
                    if (track != null && "V_MPEG4/ISO/AVC".equals(track.getCodecID())) {
                        // Convert length-prefixed NALUs to Annex B
                        int nalLenSize = nalLengthSizeMap.getOrDefault(trackNo, 4);
                        writeAnnexBFrame(data, nalLenSize, os);
                    } else {
                        // Raw copy
                        byte[] buf = new byte[data.remaining()];
                        data.get(buf);
                        os.write(buf);
                    }
                    frameCount++;
                }

                log.info("Demuxed {} frames from {} tracks in {}", frameCount, outputs.size(), mkvPath.getFileName());
            } finally {
                for (OutputStream os : outputs.values()) {
                    os.close();
                }
            }
        }

        return result;
    }

    /**
     * Writes SPS and PPS NAL units from the AVCDecoderConfigurationRecord
     * (codec private data) using Annex B start codes.
     */
    private void writeAvcParameterSets(ByteBuffer codecPrivate, OutputStream os) throws IOException {
        if (codecPrivate == null || codecPrivate.remaining() < 7) return;
        ByteBuffer cp = codecPrivate.duplicate();

        cp.get(); // configurationVersion
        cp.get(); // AVCProfileIndication
        cp.get(); // profile_compatibility
        cp.get(); // AVCLevelIndication
        cp.get(); // lengthSizeMinusOne (lower 2 bits)

        // SPS
        int numSps = cp.get() & 0x1F;
        for (int i = 0; i < numSps; i++) {
            int spsLen = (cp.get() & 0xFF) << 8 | (cp.get() & 0xFF);
            byte[] sps = new byte[spsLen];
            cp.get(sps);
            os.write(ANNEX_B_START_CODE);
            os.write(sps);
        }

        // PPS
        if (!cp.hasRemaining()) return;
        int numPps = cp.get() & 0xFF;
        for (int i = 0; i < numPps; i++) {
            int ppsLen = (cp.get() & 0xFF) << 8 | (cp.get() & 0xFF);
            byte[] pps = new byte[ppsLen];
            cp.get(pps);
            os.write(ANNEX_B_START_CODE);
            os.write(pps);
        }
    }

    /**
     * Converts a length-prefixed NAL unit frame to Annex B format, or passes through
     * data that is already in Annex B format (starts with a 3- or 4-byte start code).
     *
     * <p>Some MKV files with codec ID {@code V_MPEG4/ISO/AVC} store H.264 data in
     * Annex B format (start-code delimited) rather than the spec-mandated AVCC
     * (length-prefixed) format. Misidentifying such frames as AVCC leads to near-total
     * data loss: the start code {@code 00 00 00 01} is read as nalLen=1, only one byte
     * is written, and the following byte (e.g. {@code F0} from the AUD primary_pic_type)
     * produces a negative Java int nalLen which triggers the safety break.
     */
    private void writeAnnexBFrame(ByteBuffer data, int nalLenSize, OutputStream os) throws IOException {
        ByteBuffer buf = data.duplicate();
        if (!buf.hasRemaining()) return;

        // If the frame already carries Annex B start codes, pass it through as-is.
        if (isAnnexBFormat(buf)) {
            byte[] raw = new byte[buf.remaining()];
            buf.get(raw);
            os.write(raw);
            return;
        }

        // AVCC → Annex B conversion: each NAL is preceded by a nalLenSize-byte length prefix.
        while (buf.remaining() >= nalLenSize) {
            int nalLen = 0;
            for (int i = 0; i < nalLenSize; i++) {
                nalLen = (nalLen << 8) | (buf.get() & 0xFF);
            }
            if (nalLen <= 0 || nalLen > buf.remaining()) break;
            byte[] nalData = new byte[nalLen];
            buf.get(nalData);
            os.write(ANNEX_B_START_CODE);
            os.write(nalData);
        }
    }

    /**
     * Returns {@code true} if {@code buf} starts with a 3-byte ({@code 00 00 01})
     * or 4-byte ({@code 00 00 00 01}) Annex B start code.
     */
    private static boolean isAnnexBFormat(ByteBuffer buf) {
        int pos = buf.position();
        int rem = buf.remaining();
        if (rem >= 4
                && (buf.get(pos)   & 0xFF) == 0x00
                && (buf.get(pos+1) & 0xFF) == 0x00
                && (buf.get(pos+2) & 0xFF) == 0x00
                && (buf.get(pos+3) & 0xFF) == 0x01) {
            return true;
        }
        if (rem >= 3
                && (buf.get(pos)   & 0xFF) == 0x00
                && (buf.get(pos+1) & 0xFF) == 0x00
                && (buf.get(pos+2) & 0xFF) == 0x01) {
            return true;
        }
        return false;
    }

    /**
     * Maps a Matroska codec ID to an appropriate file extension.
     */
    static String extensionForCodec(String codecId) {
        if (codecId == null) return "bin";
        return switch (codecId) {
            case "V_MPEG4/ISO/AVC"  -> "h264";
            case "V_MPEGH/ISO/HEVC" -> "h265";
            case "V_MPEG2"          -> "m2v";
            case "V_MS/VFW/FOURCC"  -> "vc1";
            case "A_AC3"            -> "ac3";
            case "A_EAC3"           -> "eac3";
            case "A_TRUEHD"         -> "thd";
            case "A_DTS"            -> "dts";
            case "A_DTS/HD/MA"      -> "dtsma";
            case "A_DTS/HD/HRA"     -> "dtshd";
            case "A_PCM/INT/BIG"    -> "lpcm";
            case "S_HDMV/PGS"       -> "pgs";
            case "S_TEXT/UTF8"      -> "srt";
            case "S_TEXT/ASS"       -> "ass";
            case "S_TEXT/SSA"       -> "ssa";
            default                 -> "bin";
        };
    }
}
