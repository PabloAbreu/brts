package org.brts.lowlevel.writer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.brts.common.io.BinaryWriter;
import org.brts.lowlevel.model.mpls.MoviePlaylist;
import org.brts.lowlevel.model.mpls.PlayItem;
import org.brts.lowlevel.model.mpls.PlayItemStream;
import org.brts.lowlevel.model.mpls.PlayMark;
import org.brts.lowlevel.model.mpls.SubPath;

/**
 * Writer for MPLS (Movie Playlist) binary files. Generates a standards-compliant {@code XXXXX.mpls} from a
 * {@link MoviePlaylist} model.
 */
public class MoviePlaylistWriter implements BlurayFileWriter<MoviePlaylist> {

	private static final String MAGIC = "MPLS";

	private static final String VERSION = "0200";

	@Override
	public void write(MoviePlaylist model, OutputStream output) throws IOException {
		byte[] appInfoPlayListSection = buildAppInfoPlayListSection(model);
		byte[] playlistSection = buildPlaylistSection(model);
		byte[] markSection = buildMarkSection(model);

		// Header: 4 magic + 4 version + 3×4 offsets + 20 reserved = 40 bytes
		int headerSize = 40;
		int playlistOffset = headerSize + appInfoPlayListSection.length;
		int playlistMarkOffset = playlistOffset + playlistSection.length;
		int extensionOffset = 0;

		BinaryWriter w = new BinaryWriter(output);
		w.writeAscii(MAGIC);
		w.writeAscii(VERSION);
		w.writeInt(playlistOffset);
		w.writeInt(playlistMarkOffset);
		w.writeInt(extensionOffset);
		w.writePadding(20); // reserved

		w.writeBytes(appInfoPlayListSection);
		w.writeBytes(playlistSection);
		w.writeBytes(markSection);
	}

	private byte[] buildAppInfoPlayListSection(MoviePlaylist model) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);
		final int size = 14;
		w.writeInt(size);
		w.writeByte(0);// reserved
		final int playbackType = 1;// model.isMenu() ? 2 : 1;
		w.writeByte(playbackType); // playback_type
		if (playbackType == 2 || playbackType == 3) {
			final int playbackCount = 0;
			w.writeShort(playbackCount);
		} else {
			w.writePadding(2);
		}
		// UOMaskTable
		final int uoMaskTable = 0;// unset and unused for now
		w.writeInt(uoMaskTable);
		w.writeInt(0); // 2 bits for flags and 30 bits reserved
		final int accessFlags = 0;
		w.writeByte(accessFlags);// flags, unset and unused for now
		w.writeByte(0);// reserved
		return buf.toByteArray();
	}

	// -------------------------------------------------------------------------

	private byte[] buildPlaylistSection(MoviePlaylist pl) throws IOException {
		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);

		List<PlayItem> items = pl.getPlayItems() != null ? pl.getPlayItems() : List.of();
		List<SubPath> subPaths = pl.getSubPaths() != null ? pl.getSubPaths() : List.of();

		wi.writePadding(2); // reserved (2 bytes before numItems per Blu-ray spec)
		wi.writeShort(items.size());
		wi.writeShort(subPaths.size());

		for (PlayItem item : items) {
			byte[] itemBytes = buildPlayItem(item);
			wi.writeShort(itemBytes.length);
			wi.writeBytes(itemBytes);
		}
		for (SubPath sp : subPaths) {
			wi.writeBytes(buildSubPath(sp));
		}

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);
		w.writeInt(inner.size());
		w.writeBytes(inner.toByteArray());
		return buf.toByteArray();
	}

	private byte[] buildPlayItem(PlayItem item) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);

		String clipName = item.getClipName() != null ? item.getClipName() : "00001";
		w.writeAscii(String.format("%-5s", clipName).substring(0, 5));
		w.writeAscii("M2TS"); // codec_id
		w.writePadding(1); // reserved + is_multi_angle
		w.writeByte(item.getConnectionCondition() & 0x0F);
		w.writeByte(0); // stc_id

		w.writeInt(item.getInTimeTicks());
		w.writeInt(item.getOutTimeTicks());

		// UO_mask_table (8 bytes) + random_access_flag + still_mode + still_time
		w.writePadding(12);
		// TODO multiple angles and UO_mask_table are not supported for now

		// STN
		w.writeBytes(buildStn(item));
		return buf.toByteArray();
	}

	private byte[] buildStn(PlayItem item) throws IOException {
		List<PlayItemStream> streams = item.getStreams() != null ? item.getStreams() : List.of();
		long numVideo = streams.stream().filter(s -> s.getCodingType() != null && s.getCodingType().isVideo()).count();
		long numAudio = streams.stream().filter(s -> s.getCodingType() != null && s.getCodingType().isAudio()).count();
		long numPg = streams.stream().filter(s -> s.getCodingType() != null && s.getCodingType().isSubtitle()).count();
		long numIg = streams.stream().filter(s -> s.getCodingType() != null && s.getCodingType().isMenu()).count();

		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);
		wi.writePadding(2); // reserved
		wi.writeByte((int) numVideo);
		wi.writeByte((int) numAudio);
		wi.writeByte((int) numPg);
		wi.writeByte((int) numIg);
		// secondary video, audio and pip streams are not supported for now
		wi.writeByte(0); // numSecVideo
		wi.writeByte(0); // numSecAudio
		wi.writeByte(0); // numPip
		wi.writeByte(0); // numDV
		wi.writePadding(4); // reserved

		for (PlayItemStream s : streams) {
			// stream_entry (length byte + content)
			ByteArrayOutputStream entry = new ByteArrayOutputStream();
			BinaryWriter we = new BinaryWriter(entry);
			// default to in-mux if not set
			int streamType = s.getStreamType() != 0 ? s.getStreamType() : PlayItemStream.STREAM_TYPE_IN_MUX;
			we.writeByte(streamType); // stream_type
			switch (streamType) {
			case 2:
				we.writeByte(s.getSubpathId());
				we.writeByte(s.getSubclipId());
				we.writeShort(s.getPid());
				we.writePadding(4);
				break;
			case 3:
			case 4:
				we.writeByte(s.getSubpathId());
				we.writeShort(s.getPid());
				we.writePadding(5);
				break;
			default: // type 1 (in-mux)
				we.writeShort(s.getPid());
				we.writePadding(6);
				break;
			}
			byte[] entryBytes = entry.toByteArray();
			wi.writeByte(entryBytes.length);
			wi.writeBytes(entryBytes);

			// stream_attributes
			ByteArrayOutputStream attr = new ByteArrayOutputStream();
			BinaryWriter wa = new BinaryWriter(attr);
			if (s.getCodingType() != null) {
				wa.writeByte(s.getCodingType().getCodingTypeByte());
				if (s.getCodingType().isVideo()) {
					int vf = (s.getVideoFormat() != null ? s.getVideoFormat() : 0);
					int fr = (s.getFrameRate() != null ? s.getFrameRate() : 0);
					wa.writeByte((vf << 4) | fr);
					wa.writePadding(3); // reserved (or HDR info for HEVC, but not supported for now)
				} else if (s.getCodingType().isAudio()) {
					int ch = (s.getAudioChannelLayout() != null ? s.getAudioChannelLayout() : 0);
					int sr = (s.getSampleRate() != null ? s.getSampleRate() : 0);
					wa.writeByte((ch << 4) | sr);
					String lang = s.getLanguage() != null ? s.getLanguage() : "und";
					wa.writeAscii(String.format("%-3s", lang).substring(0, 3));
				} else {
					String lang = s.getLanguage() != null ? s.getLanguage() : "und";
					wa.writeAscii(String.format("%-3s", lang).substring(0, 3));
					wa.writePadding(1); // reserved
				}
			}
			byte[] attrBytes = attr.toByteArray();
			wi.writeByte(attrBytes.length);
			wi.writeBytes(attrBytes);
		}

		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);
		w.writeShort(inner.size());
		w.writeBytes(inner.toByteArray());
		return buf.toByteArray();
	}

	private byte[] buildSubPath(SubPath sp) throws IOException {
		List<SubPath.SubPlayItem> spis = sp.getSubPlayItems() != null ? sp.getSubPlayItems() : List.of();
		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);
		wi.writePadding(1);
		wi.writeByte(sp.getSubPathType());
		wi.writeShort(sp.isRepeatSubPath() ? 0x01 : 0x00);
		wi.writePadding(1);
		wi.writeByte(spis.size());
		for (SubPath.SubPlayItem spi : spis) {
			ByteArrayOutputStream spiInner = new ByteArrayOutputStream();
			BinaryWriter ws = new BinaryWriter(spiInner);
			String name = spi.getClipName() != null ? spi.getClipName() : "00001";
			ws.writeAscii(String.format("%-5s", name).substring(0, 5));
			ws.writeAscii("M2TS");
			// next bits 27 reserved 4 ConnectionCondition 1 IsMultiClipEntries 8 RefToSTCID
			ws.writePadding(3);
			ws.writeByte(spi.getConnectionCondition() << 1); // ConnectionCondition
			ws.writeByte(0); // RefToSTCID

			ws.writeInt(spi.getInTimeTicks());
			ws.writeInt(spi.getOutTimeTicks());
			ws.writeShort(spi.getSyncPlayItemId());
			ws.writeInt(spi.getSyncStartPtsTicks());
			// TODO support multiclip entries
			byte[] spiBytes = spiInner.toByteArray();
			wi.writeShort(spiBytes.length);
			wi.writeBytes(spiBytes);
		}
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);
		w.writeInt(inner.size());
		w.writeBytes(inner.toByteArray());
		return buf.toByteArray();
	}

	private byte[] buildMarkSection(MoviePlaylist pl) throws IOException {
		List<PlayMark> marks = pl.getPlayMarks() != null ? pl.getPlayMarks() : List.of();
		ByteArrayOutputStream inner = new ByteArrayOutputStream();
		BinaryWriter wi = new BinaryWriter(inner);
		wi.writeShort(marks.size());
		for (PlayMark m : marks) {
			wi.writePadding(1);
			wi.writeByte(m.getMarkType());
			wi.writeShort(m.getPlayItemRef());
			wi.writeInt(m.getMarkTimeTicks());
			wi.writeShort(m.getEntryEsPid());
			wi.writeInt(m.getDurationTicks());
		}
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		BinaryWriter w = new BinaryWriter(buf);
		w.writeInt(inner.size());
		w.writeBytes(inner.toByteArray());
		return buf.toByteArray();
	}

}
