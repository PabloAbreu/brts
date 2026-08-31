package org.brts.cli;

import org.kohsuke.args4j.Option;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BaseOptions {

	@Option(name = "--error-details", required = false, hidden = true, usage = "Shows more detailed errors. This option should be put first.")
	boolean errorDetails;

}
