package org.brts.doc.metadata;

import java.util.List;

public record CommandMetadata(String level, String name, String path, String description, String runnerType,
		String optionsType, List<OptionMetadata> options) {
}