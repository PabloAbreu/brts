package org.brts.lowlevel.parser;

import org.brts.common.exception.ParseException;
import org.brts.common.io.BinaryReader;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.model.mpls.*;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parser for MPLS (Movie Playlist) binary files.
 * <p>
 * Reference: Blu-ray Disc Read-Only Format Part 3 Section 6 (MPLS file format).
 */
@Slf4j
public class MoviePlaylistParser implements BinaryParser<MoviePlaylist> {

	private static final String MAGIC = "MPLS";

	private static final String VERSION_300 = "0300";

	private static final String VERSION_200 = "0200";

	@Override
	public MoviePlaylist parse(InputStream input) throws IOException {
		try (BinaryReader r = new BinaryReader(input)) {
			return readPlaylist(r);
		}
	}

	private MoviePlaylist readPlaylist(BinaryReader r) throws IOException {
		String magic = r.readAscii(4);
		if (!MAGIC.equals(magic)) {
			throw new ParseException("Not an MPLS file: expected '" + MAGIC + "', got '" + magic + "'");
		}
		String version = r.readAscii(4);
		if (!VERSION_300.equals(version) && !VERSION_200.equals(version)) {
			throw new ParseException("Unsupported MPLS version: " + version);
		}

		long playlistOffset = r.readUnsignedInt();
		long playlistMarkOffset = r.readUnsignedInt();
		long extensionOffset = r.readUnsignedInt();

		// Jump to PlayList section
		r.skip(playlistOffset - r.getPosition());

		MoviePlaylist playlist = new MoviePlaylist();

		long plLength = r.readUnsignedInt();
		r.skip(2); // reserved (2 bytes, no playbackType byte in this position)

		int numItems = r.readUnsignedShort();
		int numSubPaths = r.readUnsignedShort();

		List<PlayItem> playItems = new ArrayList<>(numItems);
		for (int i = 0; i < numItems; i++) {
			playItems.add(readPlayItem(r));
		}
		playlist.setPlayItems(playItems);

		List<SubPath> subPaths = new ArrayList<>(numSubPaths);
		for (int i = 0; i < numSubPaths; i++) {
			subPaths.add(readSubPath(r));
		}
		playlist.setSubPaths(subPaths);

		// PlayListMark section
		r.skip(playlistMarkOffset - r.getPosition());
		List<PlayMark> marks = readPlayMarks(r);
		playlist.setPlayMarks(marks);

		// Detect whether this playlist represents a menu
		playlist.setMenu(detectMenu(playlist));

		return playlist;
	}

	/**
	 * Determines whether the playlist is a menu rather than main content.
	 * <p>
	 * Uses a combination of deterministic and heuristic checks:
	 * <ol>
	 * <li><b>IG streams</b> — if any PlayItem carries an {@code INTERACTIVE_GRAPHICS} (0x91) stream in its STN, the
	 * playlist is interactive and therefore a menu.</li>
	 * <li><b>SubPath type 3</b> — a sub-path of type 3 is the "Interactive Graphics presentation menu" path defined by
	 * the Blu-ray spec.</li>
	 * <li><b>Repeated-clip heuristic</b> — menus typically loop a short clip many times. If the playlist has ≥ 3
	 * PlayItems and the most-repeated clip accounts for ≥ 80 % of all items, it is flagged as a menu.</li>
	 * </ol>
	 */
	private boolean detectMenu(MoviePlaylist playlist) {
		// 1. Deterministic: any PlayItem with an IG stream
		if (playlist.getPlayItems() != null) {
			for (PlayItem item : playlist.getPlayItems()) {
				if (item.getStreams() != null) {
					for (PlayItemStream stream : item.getStreams()) {
						if (stream.getCodingType() != null && stream.getCodingType().isMenu()) {
							return true;
						}
					}
				}
			}
		}

		// 2. Deterministic: SubPath type 3 (IG presentation menu)
		if (playlist.getSubPaths() != null) {
			for (SubPath sp : playlist.getSubPaths()) {
				if (sp.getSubPathType() == 3) {
					return true;
				}
			}
		}

		// 3. Heuristic: a playlist that repeats the same clip many times is likely a
		// menu
		// FIXME find some deterministic criterion
		if (playlist.getPlayItems() != null && playlist.getPlayItems().size() >= 3) {
			int total = playlist.getPlayItems().size();
			int maxOccurrences = 0;
			Set<String> seen = new HashSet<>();
			for (PlayItem item : playlist.getPlayItems()) {
				if (item.getClipName() != null) {
					seen.add(item.getClipName());
				}
			}
			for (String clip : seen) {
				int count = 0;
				for (PlayItem item : playlist.getPlayItems()) {
					if (clip.equals(item.getClipName())) {
						count++;
					}
				}
				maxOccurrences = Math.max(maxOccurrences, count);
			}
			if ((double) maxOccurrences / total >= 0.8) {
				return true;
			}
		}

		return false;
	}

	private PlayItem readPlayItem(BinaryReader r) throws IOException {
		int itemLength = r.readUnsignedShort();
		String clipName = r.readAscii(5);
		String codecId = r.readAscii(4); // "M2TS"
		if (!"M2TS".equals(codecId))
			log.warn("Found unknown codecId: {}", codecId);
		r.skip(1); // reserved + is_multi_angle nibble
		int connectionCondition = r.readUnsignedByte() & 0x0F;
		r.skip(1); // stc_id

		long inTimeTicks = r.readUnsignedInt() & 0xFFFFFFFFL;
		long outTimeTicks = r.readUnsignedInt() & 0xFFFFFFFFL;

		// UO_mask_table (8 bytes) + random_access_flag + still_mode + still_time
		r.skip(8 + 1 + 1 + 2);

		// STN (Stream Number Table)
		List<PlayItemStream> streams = readStn(r);

		PlayItem item = new PlayItem();
		item.setClipName(clipName);
		item.setConnectionCondition(connectionCondition);
		item.setInTimeTicks(inTimeTicks);
		item.setOutTimeTicks(outTimeTicks);
		item.setStreams(streams);
		return item;
	}

	private List<PlayItemStream> readStn(BinaryReader r) throws IOException {
		int stnLength = r.readUnsignedShort();
		r.skip(2); // reserved
		int numVideo = r.readUnsignedByte();
		int numAudio = r.readUnsignedByte();
		int numPg = r.readUnsignedByte();
		int numIg = r.readUnsignedByte();
		int numSecVideo = r.readUnsignedByte();
		int numSecAudio = r.readUnsignedByte();
		int numPip = r.readUnsignedByte();
		r.skip(5); // reserved

		int totalStreams = numVideo + numAudio + numPg + numIg + numSecVideo + numSecAudio;
		List<PlayItemStream> streams = new ArrayList<>(totalStreams);

		for (int i = 0; i < totalStreams; i++) {
			PlayItemStream s = new PlayItemStream();
			// stream_entry (9 bytes): length, stream_type, pid...
			int entryLength = r.readUnsignedByte();
			int streamType = r.readUnsignedByte(); // 0x01=in-mux, 0x02=out-of-mux, etc.
			s.setPid(r.readUnsignedShort());
			r.skip(entryLength - 3); // skip rest of entry to align

			// stream_attributes
			int attrLength = r.readUnsignedByte();
			int codingByte = r.readUnsignedByte();
			StreamCodingType type;
			try {
				type = StreamCodingType.fromByte(codingByte);
			} catch (IllegalArgumentException e) {
				throw new ParseException("Unknown coding type 0x" + Integer.toHexString(codingByte));
			}
			s.setCodingType(type);

			if (type.isVideo()) {
				int vfr = r.readUnsignedByte();
				s.setVfr(vfr);
				r.skip(attrLength - 2);
			} else if (type.isAudio()) {
				int channelSample = r.readUnsignedByte();
				s.setAudioChannelLayout((channelSample >> 4) & 0x0F);
				s.setSampleRate(channelSample & 0x0F);
				s.setLanguage(r.readAscii(3));
				r.skip(attrLength - 5);
			} else {
				if (attrLength > 1)
					s.setLanguage(r.readAscii(3));
				r.skip(Math.max(0, attrLength - 4));
			}
			streams.add(s);
		}
		return streams;
	}

	private SubPath readSubPath(BinaryReader r) throws IOException {
		long spLength = r.readUnsignedInt();
		r.skip(1); // reserved (0x00 probably)
		int subPathType = r.readUnsignedByte();
		int repeatFlag = r.readUnsignedByte(); // bit 0
		r.skip(2);
		int numSubPlayItems = r.readUnsignedByte();
		log.error("spLength {}, numSubPlayItems {}, position {}", spLength, numSubPlayItems, r.getPosition());

		List<SubPath.SubPlayItem> subPlayItems = new ArrayList<>(numSubPlayItems);
		for (int i = 0; i < numSubPlayItems; i++) {
			SubPath.SubPlayItem spi = new SubPath.SubPlayItem();
			int spiLength = r.readUnsignedShort();
			spi.setClipName(r.readAscii(5));
			String codecId = r.readAscii(4); // "M2TS"
			if (!"M2TS".equals(codecId))
				log.warn("Found unknown codecId: {}", codecId);
			r.skip(3); // no clue what this is
			int connectionConditionAndMultiClip = r.readUnsignedByte();
			int connectionCondition = connectionConditionAndMultiClip;// TODO bits 1->4
			int isMultiClipconnectionCondition = connectionConditionAndMultiClip;// TODO
																					// bit
																					// 0
			int refToStcId = r.readUnsignedByte();
			spi.setInTimeTicks(r.readUnsignedInt());
			spi.setOutTimeTicks(r.readUnsignedInt());
			spi.setSyncPlayItemId(r.readUnsignedShort());
			spi.setSyncStartPtsTicks(r.readUnsignedInt());
			// TODO parse multiclip, whatever that is, and keep checking spiLength for
			// sanity
			subPlayItems.add(spi);
		}

		SubPath sp = new SubPath();
		sp.setSubPathType(subPathType);
		sp.setRepeatSubPath((repeatFlag & 0x01) != 0);
		sp.setSubPlayItems(subPlayItems);
		return sp;
	}

	private List<PlayMark> readPlayMarks(BinaryReader r) throws IOException {
		long sectionLength = r.readUnsignedInt();
		int numMarks = r.readUnsignedShort();

		List<PlayMark> marks = new ArrayList<>(numMarks);
		for (int i = 0; i < numMarks; i++) {
			r.skip(1); // reserved
			PlayMark m = new PlayMark();
			m.setMarkType(r.readUnsignedByte());
			m.setPlayItemRef(r.readUnsignedShort());
			m.setMarkTimeTicks(r.readUnsignedInt());
			m.setEntryEsPid(r.readUnsignedShort());
			m.setDurationTicks(r.readUnsignedInt());
			marks.add(m);
		}
		return marks;
	}

}
