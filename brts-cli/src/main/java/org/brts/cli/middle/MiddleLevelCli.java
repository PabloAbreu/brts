package org.brts.cli.middle;

import org.brts.cli.LevelDispatcher;

/**
 * CLI for middle-level disc authoring.
 */
public class MiddleLevelCli {

	private static final LevelDispatcher dispatcher;

	static {
		dispatcher = new LevelDispatcher("mid");
		dispatcher.register(new BuildCli.Run())
			.register(new ScanPlaylistsCli.Run())
			.register(new DsPreviewCli.Run())
			.register(new SetupMenuCli.Run())
			.register(new FindFirstPlaylistCli.Run());
	}

	public static LevelDispatcher getLevelDispatcher() {
		return dispatcher;
	}

	public static void main(String[] args) throws Exception {
		dispatcher.dispatch(args);
	}

}
