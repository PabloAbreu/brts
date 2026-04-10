package org.brts.lowlevel.bdmv;

import org.brts.common.io.BinaryWriter;
import org.brts.lowlevel.model.bdmv.Bdjo;
import org.brts.lowlevel.writer.BlurayFileWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writer for {@code BDMV/BDJO/XXXXX.bdjo} — BD-J Object files.
 * <p>
 * Produces a binary file matching the BDJO format as understood by libbluray.
 * <p>
 * Binary layout written (big-endian throughout):
 * <pre>
 * Header (48 bytes):
 *   magic                              : 4 bytes  ("BDJO")
 *   version                            : 4 bytes  (from model, e.g. "0200")
 *   TerminalInfoStartAddress           : 4 bytes
 *   AppCacheInfoStartAddress           : 4 bytes
 *   AccessiblePlaylistsStartAddress    : 4 bytes
 *   AppManagementTableStartAddress     : 4 bytes
 *   KeyInterestTableStartAddress       : 4 bytes
 *   FileAccessInfoStartAddress         : 4 bytes
 *   reserved                           : 16 bytes
 *
 * Then each section sequentially.
 * </pre>
 */
public class BdjoWriter implements BlurayFileWriter<Bdjo> {

    private static final String MAGIC = "BDJO";
    private static final String DEFAULT_VERSION = "0200";
    private static final int HEADER_SIZE = 48;

    @Override
    public void write(Bdjo model, OutputStream output) throws IOException {
        // Build each section
        byte[] terminalInfo = buildTerminalInfoSection(model.getTerminalInfo());
        byte[] appCacheInfo = buildAppCacheInfoSection(model.getAppCacheInfo());
        byte[] accessiblePlaylists = buildAccessiblePlaylistsSection(model.getAccessiblePlaylists());
        byte[] appMgmtTable = buildAppManagementTableSection(model.getApplications());
        byte[] keyInterestTable = buildKeyInterestTableSection(model.getKeyInterestTable());
        byte[] fileAccessInfo = buildFileAccessInfoSection(model.getFileAccessInfo());

        // Compute section addresses
        int terminalInfoAddr = HEADER_SIZE;
        int appCacheInfoAddr = terminalInfoAddr + terminalInfo.length;
        int accessiblePlaylistsAddr = appCacheInfoAddr + appCacheInfo.length;
        int appMgmtTableAddr = accessiblePlaylistsAddr + accessiblePlaylists.length;
        int keyInterestTableAddr = appMgmtTableAddr + appMgmtTable.length;
        int fileAccessInfoAddr = keyInterestTableAddr + keyInterestTable.length;

        String version = model.getVersion() != null && !model.getVersion().isBlank()
                ? model.getVersion() : DEFAULT_VERSION;

        @SuppressWarnings("resource")
        BinaryWriter w = new BinaryWriter(output);

        // ---- Header (48 bytes) ----
        w.writeAscii(MAGIC);
        w.writeAscii(version);
        w.writeInt(terminalInfoAddr);
        w.writeInt(appCacheInfoAddr);
        w.writeInt(accessiblePlaylistsAddr);
        w.writeInt(appMgmtTableAddr);
        w.writeInt(keyInterestTableAddr);
        w.writeInt(fileAccessInfoAddr);
        w.writePadding(16); // reserved

        // ---- Sections ----
        w.writeBytes(terminalInfo);
        w.writeBytes(appCacheInfo);
        w.writeBytes(accessiblePlaylists);
        w.writeBytes(appMgmtTable);
        w.writeBytes(keyInterestTable);
        w.writeBytes(fileAccessInfo);
    }

    // =====================================================================
    // TerminalInfo
    // =====================================================================

    /**
     * Builds the TerminalInfo section.
     * Body: default_font(5) + flags(1) + padding(4) = 10 bytes.
     */
    private byte[] buildTerminalInfoSection(Bdjo.TerminalInfo ti) throws IOException {
        if (ti == null) ti = new Bdjo.TerminalInfo();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (BinaryWriter w = new BinaryWriter(buf)) {
            int bodyLength = 10; // fixed size
            w.writeInt(bodyLength); // section_length

            // default_font (5 bytes)
            writeFixedAscii(w, ti.getDefaultFont(), 5);

            // flags: havi_config(4) | menu_call_mask(1) | title_search_mask(1) | padding(2)
            int flags = ((ti.getInitialHaviConfigId() & 0x0F) << 4)
                      | ((ti.isMenuCallMask() ? 1 : 0) << 3)
                      | ((ti.isTitleSearchMask() ? 1 : 0) << 2);
            w.writeByte(flags);

            // Write stored padding bytes (preserved for round-trip fidelity)
            byte[] rawPadding = ti.getRawPadding();
            if (rawPadding != null && rawPadding.length > 0) {
                w.writeBytes(rawPadding);
            } else {
                w.writePadding(4); // default padding
            }
        }
        return buf.toByteArray();
    }

    // =====================================================================
    // AppCacheInfo
    // =====================================================================

    /**
     * Builds the AppCacheInfo section.
     * Body: num_items(1) + padding(1) + items[num_items] × 12 bytes each.
     */
    private byte[] buildAppCacheInfoSection(Bdjo.AppCacheInfo aci) throws IOException {
        List<Bdjo.AppCacheItem> items = (aci != null && aci.getItems() != null)
                ? aci.getItems() : List.of();

        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        try (BinaryWriter wi = new BinaryWriter(inner)) {
            wi.writeByte(items.size());
            wi.writePadding(1); // padding
            for (Bdjo.AppCacheItem item : items) {
                wi.writeByte(item.getType());
                writeFixedAscii(wi, item.getRefToName(), 5);
                writeFixedAscii(wi, item.getLanguageCode(), 3);
                wi.writePadding(3); // padding (24 bits)
            }
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (BinaryWriter w = new BinaryWriter(buf)) {
            w.writeInt(inner.size());
            w.writeBytes(inner.toByteArray());
        }
        return buf.toByteArray();
    }

    // =====================================================================
    // AccessiblePlaylists
    // =====================================================================

    /**
     * Builds the AccessiblePlaylists section.
     * Body: flags32(4) + playlists[num_pl] × 6 bytes each.
     */
    private byte[] buildAccessiblePlaylistsSection(Bdjo.AccessiblePlaylists ap) throws IOException {
        if (ap == null) ap = new Bdjo.AccessiblePlaylists();
        List<String> plNames = ap.getPlaylistNames() != null ? ap.getPlaylistNames() : List.of();

        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        try (BinaryWriter wi = new BinaryWriter(inner)) {
            // num_pl(11 bits) | access_to_all(1) | autostart_first(1) | padding(19 bits)
            int flags32 = ((plNames.size() & 0x7FF) << 21)
                        | ((ap.isAccessToAllFlag() ? 1 : 0) << 20)
                        | ((ap.isAutostartFirstPlaylistFlag() ? 1 : 0) << 19);
            wi.writeInt(flags32);

            for (String name : plNames) {
                writeFixedAscii(wi, name, 5);
                wi.writePadding(1); // padding
            }
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (BinaryWriter w = new BinaryWriter(buf)) {
            w.writeInt(inner.size());
            w.writeBytes(inner.toByteArray());
        }
        return buf.toByteArray();
    }

    // =====================================================================
    // Application Management Table
    // =====================================================================

    /**
     * Builds the Application Management Table section.
     */
    private byte[] buildAppManagementTableSection(List<Bdjo.BdjoApp> apps) throws IOException {
        if (apps == null) apps = List.of();

        ByteArrayOutputStream inner = new ByteArrayOutputStream();
        try (BinaryWriter wi = new BinaryWriter(inner)) {
            wi.writeByte(apps.size());
            wi.writePadding(1); // padding
            for (Bdjo.BdjoApp app : apps) {
                writeAppEntry(wi, app);
            }
        }

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (BinaryWriter w = new BinaryWriter(buf)) {
            w.writeInt(inner.size());
            w.writeBytes(inner.toByteArray());
        }
        return buf.toByteArray();
    }

    /**
     * Writes a single application entry.
     */
    private void writeAppEntry(BinaryWriter w, Bdjo.BdjoApp app) throws IOException {
        // control_code(8) + type(4) + padding(4)
        w.writeByte(app.getControlCode());
        w.writeByte((app.getType() & 0x0F) << 4);

        // org_id(32) + app_id(16)
        w.writeInt(app.getOrganizationId());
        w.writeShort(app.getApplicationId());

        // Build the application descriptor body first to get its length
        byte[] descriptorBody = buildAppDescriptorBody(app);

        // Descriptor tag+length header — 10 bytes total
        // Format: 2 bytes (0x0000) + 4 bytes descriptor_length (body + 4) + 4 bytes (0x00000000)
        byte[] header = app.getDescriptorHeader();
        if (header != null && header.length == 10) {
            w.writeBytes(header);
        } else {
            w.writePadding(2);
            w.writeInt(descriptorBody.length + 4);
            w.writePadding(4);
        }

        // Descriptor body
        w.writeBytes(descriptorBody);
    }

    /**
     * Builds the application descriptor body (everything after the 10-byte tag+length header).
     */
    private byte[] buildAppDescriptorBody(Bdjo.BdjoApp app) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (BinaryWriter w = new BinaryWriter(buf)) {
            List<Bdjo.AppProfile> profiles = app.getProfiles() != null ? app.getProfiles() : List.of();

            // num_profile(4 bits) + padding(12 bits)
            w.writeShort((profiles.size() & 0x0F) << 12);

            for (Bdjo.AppProfile prof : profiles) {
                w.writeShort(prof.getProfileNumber());
                w.writeByte(prof.getMajorVersion());
                w.writeByte(prof.getMinorVersion());
                w.writeByte(prof.getMicroVersion());
                w.writePadding(1); // padding
            }

            // priority(8) + binding(2) + visibility(2) + padding(4)
            w.writeByte(app.getPriority());
            int bindVisByte = ((app.getBinding() & 0x03) << 6)
                            | ((app.getVisibility() & 0x03) << 4);
            w.writeByte(bindVisByte);

            // Application names
            writeAppNames(w, app.getNames());

            // icon_locator (word-aligned)
            writeAppString(w, app.getIconLocator());

            // icon_flags (16 bits)
            w.writeShort(app.getIconFlags());

            // base_dir, classpath_extension, initial_class (all word-aligned)
            writeAppString(w, app.getBaseDir());
            writeAppString(w, app.getClasspathExtension());
            writeAppString(w, app.getInitialClass());

            // Application parameters
            writeAppParams(w, app.getParameters());
        }
        return buf.toByteArray();
    }

    /**
     * Writes the application names block.
     * <pre>
     *   data_length(16) + names... + word-align
     * </pre>
     */
    private void writeAppNames(BinaryWriter w, List<Bdjo.AppName> names) throws IOException {
        if (names == null) names = List.of();

        // Build name data to compute length
        ByteArrayOutputStream nameData = new ByteArrayOutputStream();
        try (BinaryWriter nw = new BinaryWriter(nameData)) {
            for (Bdjo.AppName name : names) {
                writeFixedAscii(nw, name.getLanguage(), 3);
                byte[] nameBytes = name.getName().getBytes(StandardCharsets.UTF_8);
                nw.writeByte(nameBytes.length);
                nw.writeBytes(nameBytes);
            }
        }

        int dataLength = nameData.size();
        w.writeShort(dataLength);
        w.writeBytes(nameData.toByteArray());

        // Word-align
        if ((dataLength & 1) != 0) {
            w.writePadding(1);
        }
    }

    /**
     * Writes a word-aligned length-prefixed string.
     * <pre>
     *   length(8) + string(length) + padding(1 if length is even)
     * </pre>
     */
    private void writeAppString(BinaryWriter w, String s) throws IOException {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        w.writeByte(bytes.length);
        if (bytes.length > 0) {
            w.writeBytes(bytes);
        }
        // Word-align: if length is even, total (1+length) is odd, need 1 pad byte
        if ((bytes.length & 1) == 0) {
            w.writePadding(1);
        }
    }

    /**
     * Writes the application parameters block.
     * <pre>
     *   data_length(8) + params... + word-align
     * </pre>
     */
    private void writeAppParams(BinaryWriter w, List<String> params) throws IOException {
        if (params == null) params = List.of();

        ByteArrayOutputStream paramData = new ByteArrayOutputStream();
        try (BinaryWriter pw = new BinaryWriter(paramData)) {
            for (String param : params) {
                byte[] bytes = param.getBytes(StandardCharsets.UTF_8);
                pw.writeByte(bytes.length);
                pw.writeBytes(bytes);
            }
        }

        int dataLength = paramData.size();
        w.writeByte(dataLength);
        if (dataLength > 0) {
            w.writeBytes(paramData.toByteArray());
        }

        // Word-align: if data_length is even, total (1+data_length) is odd, need 1 pad byte
        if ((dataLength & 1) == 0) {
            w.writePadding(1);
        }
    }

    // =====================================================================
    // KeyInterestTable
    // =====================================================================

    /**
     * Builds the Key Interest Table (4 bytes).
     */
    private byte[] buildKeyInterestTableSection(Bdjo.KeyInterestTable kit) throws IOException {
        if (kit == null) kit = new Bdjo.KeyInterestTable();
        ByteArrayOutputStream buf = new ByteArrayOutputStream(4);
        try (BinaryWriter w = new BinaryWriter(buf)) {
            int b0 = (kit.isVkPlay()     ? 0x80 : 0)
                    | (kit.isVkStop()     ? 0x40 : 0)
                    | (kit.isVkFfw()      ? 0x20 : 0)
                    | (kit.isVkRew()      ? 0x10 : 0)
                    | (kit.isVkTrackNext()? 0x08 : 0)
                    | (kit.isVkTrackPrev()? 0x04 : 0)
                    | (kit.isVkPause()    ? 0x02 : 0)
                    | (kit.isVkStillOff() ? 0x01 : 0);
            w.writeByte(b0);

            int b1 = (kit.isVkSecAudioEnaDis() ? 0x80 : 0)
                    | (kit.isVkSecVideoEnaDis() ? 0x40 : 0)
                    | (kit.isPgTextstEnaDis()   ? 0x20 : 0);
            w.writeByte(b1);

            w.writePadding(2); // remaining 16 bits padding
        }
        return buf.toByteArray();
    }

    // =====================================================================
    // FileAccessInfo
    // =====================================================================

    /**
     * Builds the File Access Info section.
     * <pre>
     *   file_access_length(16) + path(file_access_length bytes)
     * </pre>
     */
    private byte[] buildFileAccessInfoSection(String fileAccessInfo) throws IOException {
        if (fileAccessInfo == null) fileAccessInfo = "";
        byte[] pathBytes = fileAccessInfo.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (BinaryWriter w = new BinaryWriter(buf)) {
            w.writeShort(pathBytes.length);
            if (pathBytes.length > 0) {
                w.writeBytes(pathBytes);
            }
            // Word-align: pad if path length is odd
            if ((pathBytes.length & 1) != 0) {
                w.writePadding(1);
            }
        }
        return buf.toByteArray();
    }

    // =====================================================================
    // Utilities
    // =====================================================================

    /**
     * Writes a fixed-length ASCII string, null-padded if shorter, truncated if longer.
     */
    private void writeFixedAscii(BinaryWriter w, String s, int length) throws IOException {
        byte[] bytes = (s != null ? s : "").getBytes(StandardCharsets.US_ASCII);
        int writeLen = Math.min(bytes.length, length);
        for (int i = 0; i < writeLen; i++) {
            w.writeByte(bytes[i] & 0xFF);
        }
        if (writeLen < length) {
            w.writePadding(length - writeLen);
        }
    }
}
