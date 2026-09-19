package org.brts.cli.low;

/*-
 * ===_LICENSE_BEGIN_===
 * BRTS — Blu-ray Tools Suite for authoring Blu-ray discs
 *
 * This file '/data/work/brts/brts-cli/src/main/java/org/brts/cli/low/MkvToPlaylistCli.java' is part of BRTS.
 * ==============================
 * Copyright (C) 2026 Pablo ABREU
 * ==============================
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * ===_LICENSE_END_===
 */

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
