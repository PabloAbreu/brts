package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.lowlevel.mkv.MkvToPlaylistConverter;
import org.brts.lowlevel.mkv.MkvToPlaylistDescriptor;
import org.brts.lowlevel.pgs.PgsRenderConfig;
import org.brts.lowlevel.popupmenu.PopupMenuConfig;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.nio.file.Paths;

/**
 * CLI for converting an MKV file into a Blu-ray clip triplet (M2TS + CLPI + MPLS).
 */
public class MkvToPlaylistCli {

	public static class ConvertOptions extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--descriptor", required = true, usage = "Path to the mkv-to-playlist JSON descriptor")
		MkvToPlaylistDescriptor descriptor;

		@Option(name = "--output", required = true, usage = "Output directory for BDMV structure (STREAM/, CLIPINF/, PLAYLIST/ subdirs)")
		File outputDir;

	}

	public static class Convert extends FeatureRunner<ConvertOptions> {

		@Override
		public String getCommandName() {
			return "mkv-to-playlist";
		}

		@Override
		public String getDescription() {
			return "Convert an MKV file to Blu-ray M2TS/CLPI/MPLS";
		}

		@Override
		protected void execute(ConvertOptions opts) throws Exception {
			MkvToPlaylistDescriptor descriptor = opts.descriptor;
			MkvToPlaylistConverter.Config config = new MkvToPlaylistConverter.Config(Paths.get(descriptor.getInput()),
					opts.outputDir.toPath(), descriptor.getClipName());

			config.setAudioTrackFilter(descriptor.getAudioTracks());
			config.setSubtitleTrackFilter(descriptor.getSubtitleTracks());
			config.setPgsConfig(descriptor.getPgsConfig() != null ? descriptor.getPgsConfig() : new PgsRenderConfig());

			PopupMenuConfig popupMenu = descriptor.getPopupMenu();
			if (popupMenu != null) {
				config.setPopupMenuConfig(popupMenu);
				config.setPopupMenuClipName(popupMenu.getOutputClipName());
				config.setPopupMenuStyle(popupMenu.getStyle());
				config.setPopupMenuLayout(
						popupMenu.getLayout() != null ? popupMenu.getLayout() : PopupMenuConfig.Layout.VERTICAL_LIST);
			}

			MkvToPlaylistConverter converter = new MkvToPlaylistConverter();
			MkvToPlaylistConverter.Result result = converter.convert(config);

			System.out.println("M2TS written  → " + result.m2tsFile());
			System.out.println("CLPI written  → " + result.clpiFile());
			System.out.println("MPLS written  → " + result.mplsFile());
		}

	}

}
