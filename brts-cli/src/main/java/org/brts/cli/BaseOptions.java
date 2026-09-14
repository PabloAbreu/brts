package org.brts.cli;

import org.kohsuke.args4j.Option;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseOptions {
	public static final String OPTION_NO_BANNER = "--no-banner";

	@Option(name = "--error-details", required = false, hidden = true, usage = "Shows more detailed errors. This option should be put first.")
	boolean errorDetails;

	@Option(name = OPTION_NO_BANNER, required = false, hidden = true, usage = "Suppresses the startup banner. This option should be put first.")
	boolean noBanner;

}
