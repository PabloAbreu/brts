package org.brts.doc.metadata;

import java.util.List;
import java.util.Map;

public record CliMetadata(String schemaVersion, String applicationVersion, List<LevelMetadata> levels,
		Map<String, TypeMetadata> types, List<String> warnings) {
}