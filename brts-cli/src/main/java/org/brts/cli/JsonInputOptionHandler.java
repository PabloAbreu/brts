package org.brts.cli;

import java.io.File;

import org.brts.common.json.JsonMapperFactory;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

public class JsonInputOptionHandler extends OneArgumentOptionHandler<Object> {

	public JsonInputOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Object> setter) {
		super(parser, option, setter);
	}

	@Override
	public String getDefaultMetaVariable() {
		return "/path/to/input.json";
	}

	@Override
	protected Object parse(String argument) throws NumberFormatException, CmdLineException {
		try {
			return JsonMapperFactory.get().readValue(new File(argument), setter.getType());
		} catch (Exception e) {
			throw new CmdLineException(owner, "Failed to parse JSON input: " + argument, e);
		}
	}

}
