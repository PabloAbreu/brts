package org.brts.lowlevel.igs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.brts.common.json.JsonMapperFactory;
import org.brts.lowlevel.igs.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * IGS Demuxer — extracts the contents of a raw IGS elementary stream into a structured
 * sub-folder.
 * <p>
 * For each display set the demuxer writes:
 * <ul>
 * <li>A JSON file describing the ICS, palettes, windows, and object metadata.</li>
 * <li>One {@code .rle} file per ODS containing the raw RLE-compressed bitmap.</li>
 * </ul>
 * <p>
 * The output folder structure: <pre>
 * &lt;outputDir&gt;/
 *   igs_manifest.json           — top-level manifest (display set count, video descriptor)
 *   ds_0000/
 *     display_set.json          — ICS + palette + window + object metadata
 *     obj_0000.rle              — raw RLE data for object 0
 *     obj_0001.rle              — raw RLE data for object 1
 *     ...
 *   ds_0001/
 *     ...
 * </pre>
 *
 * This output can be fed back into {@link IgsMuxer} for a faithful round-trip.
 */
public class IgsDemuxer {

	private static final Logger log = LoggerFactory.getLogger(IgsDemuxer.class);

	private final IgsParser parser = new IgsParser();

	private final ObjectMapper mapper = JsonMapperFactory.get();

	/**
	 * Demuxes the raw .igs file into the given output directory.
	 * @param igsFile path to the raw .igs elementary stream file
	 * @param outputDir target directory (will be created if it doesn't exist)
	 * @return the parsed display sets (for further processing if needed)
	 * @throws IOException on I/O error
	 */
	public List<IgsDisplaySet> demux(Path igsFile, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);

		List<IgsRawSegment> segments = parser.parseSegments(igsFile);
		List<IgsDisplaySet> displaySets = parser.groupIntoDisplaySets(segments);

		// Build top-level manifest
		IgsManifest manifest = new IgsManifest();
		manifest.setSourceFile(igsFile.getFileName().toString());
		manifest.setDisplaySetCount(displaySets.size());

		if (!displaySets.isEmpty() && displaySets.get(0).getCompositionSegment() != null) {
			VideoDescriptor vd = displaySets.get(0).getCompositionSegment().getVideoDescriptor();
			if (vd != null) {
				manifest.setVideoWidth(vd.getWidth());
				manifest.setVideoHeight(vd.getHeight());
				manifest.setFrameRateCode(vd.getFrameRateCode());
			}
		}

		List<IgsManifest.DisplaySetRef> dsRefs = new ArrayList<>();

		for (int i = 0; i < displaySets.size(); i++) {
			IgsDisplaySet ds = displaySets.get(i);
			String dirName = String.format("ds_%04d", i);
			Path dsDir = outputDir.resolve(dirName);
			Files.createDirectories(dsDir);

			// Write each ODS's RLE data as a separate binary file
			DisplaySetJson dsJson = buildDisplaySetJson(ds);
			for (int j = 0; j < ds.getObjects().size(); j++) {
				IgsObject obj = ds.getObjects().get(j);
				String rleFileName = String.format("obj_%04d.rle", j);
				Path rlePath = dsDir.resolve(rleFileName);
				if (obj.getRleData() != null) {
					Files.write(rlePath, obj.getRleData());
				}
				dsJson.getObjects().get(j).setRleFile(rleFileName);
			}

			// Write display set JSON
			Path dsJsonPath = dsDir.resolve("display_set.json");
			mapper.writerWithDefaultPrettyPrinter().writeValue(dsJsonPath.toFile(), dsJson);

			IgsManifest.DisplaySetRef ref = new IgsManifest.DisplaySetRef();
			ref.setDirectory(dirName);
			ref.setEpochStart(ds.isEpochStart());
			ref.setObjectCount(ds.getObjects().size());
			ref.setPaletteCount(ds.getPalettes().size());
			dsRefs.add(ref);
		}

		manifest.setDisplaySets(dsRefs);

		// Write manifest
		Path manifestPath = outputDir.resolve("igs_manifest.json");
		mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest);

		log.info("IGS demuxed: {} display sets → {}", displaySets.size(), outputDir);
		return displaySets;
	}

	// -------------------------------------------------------------------------
	// Build JSON model from parsed display set
	// -------------------------------------------------------------------------

	private DisplaySetJson buildDisplaySetJson(IgsDisplaySet ds) {
		DisplaySetJson json = new DisplaySetJson();
		json.setEpochStart(ds.isEpochStart());

		// ICS
		if (ds.getCompositionSegment() != null) {
			json.setCompositionSegment(ds.getCompositionSegment());
		}

		// Palettes
		json.setPalettes(ds.getPalettes());

		// Windows
		json.setWindowDefinitions(ds.getWindowDefinitions());

		// Objects — metadata only (RLE data written separately)
		List<ObjectMetadata> objMetas = new ArrayList<>();
		for (IgsObject obj : ds.getObjects()) {
			ObjectMetadata meta = new ObjectMetadata();
			meta.setId(obj.getId());
			meta.setVersion(obj.getVersion());
			meta.setWidth(obj.getWidth());
			meta.setHeight(obj.getHeight());
			meta.setDataLength(obj.getDataLength());
			meta.setSequenceDescriptor(obj.getSequenceDescriptor());
			meta.setPts(obj.getPts());
			objMetas.add(meta);
		}
		json.setObjects(objMetas);

		return json;
	}

	// =========================================================================
	// JSON models for serialisation
	// =========================================================================

	@lombok.Getter
	@lombok.Setter
	public static class IgsManifest {

		private String sourceFile;

		private int displaySetCount;

		private int videoWidth;

		private int videoHeight;

		private int frameRateCode;

		private List<DisplaySetRef> displaySets;

		@lombok.Getter
		@lombok.Setter
		public static class DisplaySetRef {

			private String directory;

			private boolean epochStart;

			private int objectCount;

			private int paletteCount;

		}

	}

	@lombok.Getter
	@lombok.Setter
	public static class DisplaySetJson {

		private boolean epochStart;

		private IgsCompositionSegment compositionSegment;

		private List<IgsPalette> palettes;

		private List<IgsWindowDefinition> windowDefinitions;

		private List<ObjectMetadata> objects = new ArrayList<>();

	}

	@lombok.Getter
	@lombok.Setter
	public static class ObjectMetadata {

		private int id;

		private int version;

		private int width;

		private int height;

		private int dataLength;

		private SequenceDescriptor sequenceDescriptor;

		private long pts;

		/** Relative path to the RLE data file. */
		private String rleFile;

	}

}
