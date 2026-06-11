package org.brts.middle.preview;

import org.brts.common.m2ts.M2tsDemuxer;
import org.brts.common.m2ts.M2tsPacketHandler;
import org.brts.common.m2ts.M2tsParser;
import org.brts.common.m2ts.model.M2tsInfo;
import org.brts.common.m2ts.model.M2tsStreamInfo;
import org.brts.common.model.StreamCodingType;
import org.brts.lowlevel.igs.IgsParser;
import org.brts.lowlevel.igs.RleConverter;
import org.brts.lowlevel.igs.model.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads an M2TS file, extracts its IGS (Interactive Graphics Stream), parses the display sets, decodes all RLE objects
 * into images, and builds a {@link DisplaySetPreviewModel} ready for the Swing viewer.
 * <p>
 * This is the "middle-level" bridge that combines low-level M2TS parsing, IGS parsing, and RLE decoding into a single
 * convenient entry point.
 */
@Slf4j
public class DisplaySetLoader {

	private final M2tsParser m2tsParser = new M2tsParser();

	private final IgsParser igsParser = new IgsParser();

	private final RleConverter rleConverter = new RleConverter();

	/**
	 * Loads the first IGS display set from the given M2TS file.
	 *
	 * @param m2tsFile path to the {@code .m2ts} file
	 * @return a fully populated preview model
	 * @throws IOException if parsing fails or no IGS stream is found
	 */
	public DisplaySetPreviewModel load(Path m2tsFile) throws IOException {
		return load(m2tsFile, 0);
	}

	/**
	 * Loads a specific display set (by index) from the given M2TS file.
	 *
	 * @param m2tsFile        path to the {@code .m2ts} file
	 * @param displaySetIndex zero-based index of the display set to load
	 * @return a fully populated preview model
	 * @throws IOException if parsing fails or no IGS stream is found
	 */
	public DisplaySetPreviewModel load(Path m2tsFile, int displaySetIndex) throws IOException {
		log.info("Loading IGS from M2TS: {}", m2tsFile);

		// 1. Parse M2TS to find the IGS PID
		M2tsInfo info = m2tsParser.parse(m2tsFile);
		int igsPid = -1;
		for (M2tsStreamInfo stream : info.getStreams()) {
			if (stream.getCodingType() == StreamCodingType.INTERACTIVE_GRAPHICS) {
				igsPid = stream.getPid();
				break;
			}
		}
		if (igsPid < 0) {
			throw new IOException("No Interactive Graphics stream found in " + m2tsFile);
		}
		log.info("Found IGS stream at PID 0x{}", Integer.toHexString(igsPid));

		// 2. Demux the IGS elementary stream into memory
		byte[] igsBytes = demuxIgsToMemory(m2tsFile, info, igsPid);
		log.info("Demuxed {} bytes of IGS data", igsBytes.length);

		// 3. Parse IGS segments and group into display sets
		List<IgsRawSegment> segments = igsParser.parseSegments(igsBytes);
		List<IgsDisplaySet> displaySets = igsParser.groupIntoDisplaySets(segments);
		if (displaySets.isEmpty()) {
			throw new IOException("No display sets found in IGS stream");
		}
		if (displaySetIndex >= displaySets.size()) {
			log.warn("Requested display set index {} but only {} available, using 0", displaySetIndex,
					displaySets.size());
			displaySetIndex = 0;
		}
		log.info("Parsed {} display sets, using index {}", displaySets.size(), displaySetIndex);

		// 4. Build the preview model from the chosen display set
		IgsDisplaySet ds = displaySets.get(displaySetIndex);
		return buildModel(ds);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	private byte[] demuxIgsToMemory(Path m2tsFile, M2tsInfo info, int igsPid) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
		M2tsDemuxer demuxer = new M2tsDemuxer();
		demuxer.demux(m2tsFile, info, new M2tsPacketHandler() {
			@Override
			public void onPayload(int pid, byte[] payload, int offset, int length, boolean payloadUnitStart,
					long packetIndex, long ats) throws IOException {
				int payloadOff = offset;
				int payloadLen = length;

				// Strip PES header on packets with Payload Unit Start Indicator.
				// PES packets start with 00 00 01; the header length is at byte [8].
				// This mirrors the logic in FilePacketHandler which the working
				// "igs-demux" CLI path uses via M2tsExtractor.
				if (payloadUnitStart && payloadLen > 8 && payload[payloadOff] == 0x00 && payload[payloadOff + 1] == 0x00
						&& payload[payloadOff + 2] == 0x01) {
					int pesHeaderLen = (payload[payloadOff + 8] & 0xFF) + 9;
					payloadOff += pesHeaderLen;
					payloadLen = length - (payloadOff - offset);
					if (payloadLen <= 0)
						return;
				}

				out.write(payload, payloadOff, payloadLen);
			}

			@Override
			public void close() {
			}
		}, Set.of(igsPid));
		return out.toByteArray();
	}

	private DisplaySetPreviewModel buildModel(IgsDisplaySet ds) {
		DisplaySetPreviewModel model = new DisplaySetPreviewModel();

		// Video descriptor
		IgsCompositionSegment ics = ds.getCompositionSegment();
		if (ics != null && ics.getVideoDescriptor() != null) {
			model.setVideoDescriptor(ics.getVideoDescriptor());
		} else {
			// Default to 1920×1080
			VideoDescriptor vd = new VideoDescriptor();
			vd.setWidth(1920);
			vd.setHeight(1080);
			model.setVideoDescriptor(vd);
		}

		// Palettes
		for (IgsPalette pal : ds.getPalettes()) {
			model.getPalettes().put(pal.getId(), pal);
		}

		// Interactive composition — pages and buttons
		if (ics != null && ics.getInteractiveComposition() != null) {
			IgsInteractiveComposition ic = ics.getInteractiveComposition();
			model.setUiModel(ic.getUiModel());
			model.setPages(ic.getPages());
		}

		// Decode RLE objects into BufferedImage
		// Use the first palette (page 0's paletteIdRef, or palette id 0)
		IgsPalette defaultPalette = findDefaultPalette(model);
		for (IgsObject obj : ds.getObjects()) {
			if (obj.getRleData() != null && obj.getRleData().length > 0) {
				BufferedImage img;
				if (obj.getWidth() < 1 || obj.getHeight() < 1) {
					log.warn("Object {} has invalid dimensions {}×{}, substituting 1×1 placeholder", obj.getId(),
							obj.getWidth(), obj.getHeight());
					img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
				} else {
					img = rleConverter.decodeRle(obj.getRleData(), obj.getWidth(), obj.getHeight(), defaultPalette);
				}
				model.getObjectImages().put(obj.getId(), img);
			}
		}

		// Initialise runtime state
		model.setCurrentPageIndex(0);
		model.resetBogState();
		model.resetSelectedButton();

		log.info("Preview model ready: {}×{}, {} pages, {} objects decoded", model.getScreenWidth(),
				model.getScreenHeight(), model.getPages().size(), model.getObjectImages().size());

		return model;
	}

	private IgsPalette findDefaultPalette(DisplaySetPreviewModel model) {
		// Try the current page's paletteIdRef
		IgsPage page = model.getCurrentPage();
		if (page != null && model.getPalettes().containsKey(page.getPaletteIdRef())) {
			return model.getPalettes().get(page.getPaletteIdRef());
		}
		// Fall back to palette 0 or just the first available
		if (model.getPalettes().containsKey(0)) {
			return model.getPalettes().get(0);
		}
		return model.getPalettes().values().stream().findFirst().orElse(null);
	}

}
