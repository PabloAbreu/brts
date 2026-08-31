package org.brts.cli.low;

import org.brts.cli.FeatureRunner;
import org.brts.cli.JsonInputOption;
import org.brts.lowlevel.model.disc.LowLevelDiscDescriptor;

import lombok.extern.slf4j.Slf4j;

/**
 * CLI for low-level disc operations.
 * <p>
 * Sub-commands:
 * <ul>
 * <li><b>disc-create</b> — Create a full disc</li>
 * </ul>
 */
@Slf4j
public class DiscCli {

	// -------------------------------------------------------------------------
	// Create command: full descriptor -> full Disc
	// -------------------------------------------------------------------------

	public static class CreateOptions extends org.brts.cli.BaseOptions {

		@JsonInputOption(name = "--input", required = true, usage = "Path to the disc descriptor")
		LowLevelDiscDescriptor input;

	}

	public static class Create extends FeatureRunner<CreateOptions> {

		@Override
		public String getCommandName() {
			return "disc-create";
		}

		@Override
		public String getDescription() {
			return "Create a full disc from a descriptor and video files";
		}

		@Override
		protected void execute(CreateOptions opts) throws Exception {
			LowLevelDiscDescriptor descriptor = opts.input;
			log.debug("Creation of disc " + descriptor.getDiscName());
		}

	}

}
