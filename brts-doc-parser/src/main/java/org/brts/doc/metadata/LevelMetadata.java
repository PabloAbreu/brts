package org.brts.doc.metadata;

import java.util.List;

public record LevelMetadata(String name, List<CommandMetadata> commands) {
}