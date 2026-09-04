package org.brts.cli;

import org.brts.cli.low.BdjoCli;
import org.brts.cli.low.BrtsInfoCli;
import org.brts.cli.low.ChapterThumbnailsCli;
import org.brts.cli.low.ClipInfoCli;
import org.brts.cli.low.ClipRegenCli;
import org.brts.cli.low.DiscCli;
import org.brts.cli.low.IndexBdmvCli;
import org.brts.cli.low.M2tsCli;
import org.brts.cli.low.MkvCli;
import org.brts.cli.low.MkvToPlaylistCli;
import org.brts.cli.low.Mp4Cli;
import org.brts.cli.low.MovieObjectsCli;
import org.brts.cli.low.NavSimulCli;
import org.brts.cli.low.PgsCli;
import org.brts.cli.low.PlaylistCli;
import org.brts.cli.low.PlaylistToMkvCli;
import org.brts.cli.low.RleConverterCli;
import org.brts.cli.low.TemplateCli;
import org.brts.cli.low.TitleMenuCli;
import org.brts.cli.low.VideoGenCli;

/**
 * Dispatches low-level subcommands.
 */
public class LowLevelDispatcher {

	private final LevelDispatcher dispatcher;

	public LowLevelDispatcher() {
		dispatcher = new LevelDispatcher("low");
		dispatcher.register(new ClipInfoCli.Parse()).register(new ClipInfoCli.Write())
				.register(new ClipRegenCli.Regen()).register(new PlaylistCli.Parse()).register(new PlaylistCli.Write())
				.register(new PlaylistCli.FindPlaylist()).register(new IndexBdmvCli.Parse())
				.register(new IndexBdmvCli.Write()).register(new MovieObjectsCli.Parse())
				.register(new MovieObjectsCli.Write()).register(new BdjoCli.Parse()).register(new BdjoCli.Write())
				.register(new MkvCli.Info()).register(new MkvCli.Extract()).register(new Mp4Cli.Info())
				.register(new M2tsCli.Info()).register(new M2tsCli.Extract()).register(new M2tsCli.Create())
				.register(new M2tsCli.Dump()).register(new M2tsCli.IgsDemux()).register(new M2tsCli.M2tsIgsMux())
				.register(new M2tsCli.IgsMux()).register(new PgsCli.Create()).register(new RleConverterCli.Convert())
				.register(new PlaylistToMkvCli.Extract()).register(new MkvToPlaylistCli.Convert())
				.register(new BrtsInfoCli.Info()).register(new NavSimulCli.Simul()).register(new DiscCli.Create())
				.register(new TitleMenuCli.Create()).register(new VideoGenCli.Generate())
				.register(new ChapterThumbnailsCli.Extract()).register(new TemplateCli.Render());
	}

	public LevelDispatcher getLevelDispatcher() {
		return dispatcher;
	}

	public void dispatch(String[] args) throws Exception {
		dispatcher.dispatch(args);
	}

}
