# BRTS Copilot Instructions

## Purpose
This file centralizes project context so coding agents can act quickly without broad exploratory scans.

## Project Snapshot
- Project: BRTS (Blu-ray Tools Suite).
- Language and runtime: Java 21.
- Build system: Maven multi-module project with parent `pom.xml`.
- Modules in dependency order:
  1. `brts-common`
  2. `brts-low-level` (depends on `brts-common`)
  3. `brts-middle-level` (depends on `brts-low-level`)
  4. `brts-high-level` (depends on `brts-middle-level`)
  5. `brts-cli` (depends on high/middle/low-level modules)
- Domain focus: Blu-ray authoring from MKV and JSON descriptors (M2TS, CLPI, MPLS, BDMV).

## Core Technologies
- JSON: Jackson (`jackson-databind`, `jackson-core`, `jackson-annotations`, `jackson-datatype-jdk8`).
- CLI: args4j.
- Logging: SLF4J + Logback.
- Boilerplate reduction: Lombok.
- Media processing: ByteDeco FFmpeg artifacts.
- Testing: JUnit 5, Mockito, AssertJ.

## Architecture Rules
### MUST
- Preserve module dependency direction:
  `brts-common -> brts-low-level -> brts-middle-level -> brts-high-level -> brts-cli`.
- Do not introduce back-dependencies from lower layers to higher layers.
- Keep shared utilities and cross-module abstractions in `brts-common`.
- Keep binary Blu-ray parsing and writing responsibilities in `brts-low-level`.
- Do not edit generated artifacts under any `target/` directory.

### SHOULD
- Keep changes localized to the smallest relevant module.
- Avoid adding dependencies to `brts-common` unless they are broadly required by downstream modules.

## Build, Test, and Run
- Full build: `mvn clean package`
- Full test suite: `mvn test`
- Module scoped build/test: `mvn -pl <module> clean test`
- CLI packaging: `brts-cli` produces a fat jar via `maven-assembly-plugin`.
- Local debug launcher: `brts_debug_launch.sh`
  - Uses `.cached_classpath` for dependency classpath caching.
  - Delete `.cached_classpath` after dependency changes.

## Formatting and Style
- Parent build applies `net.revelc.code.formatter:formatter-maven-plugin`.
- Canonical formatter config: `config/brts-java-formatter.xml`.
- Expected encoding and line endings: UTF-8 and LF.
- Run Maven build/test commands before final handoff so formatting and compile issues surface early.

## Coding Conventions
### Logging
- SHOULD prefer `@Slf4j` in classes already aligned with Lombok logging.
- SHOULD use parameterized logging (`log.info("value={}", value)`) instead of concatenation.

### code mutualization
- SHOULD extract shared logic into `brts-common` if it has clear utility across multiple layers.
- SHOULD re-use existing helpers/logic and avoid creating new ones when possible.
- SHOULD extract helper methods to the lowest layer that can use them without introducing new dependencies.
- SHOULD use BrtsFileConfig for config value access to preserve precedence behavior.

### JSON
- MUST use `org.brts.common.json.JsonMapperFactory.get()` for descriptor mapping unless there is a documented exception.

### Error Handling
- SHOULD use clear validation messages when throwing `IllegalArgumentException`.
- SHOULD include contextual details in parsing and I/O failures (file path, offset, clip name, or PTS where relevant).

### Package Layout
- Low-level code currently exists in both `org.brts.lowlevel.*` and `org.brts.common.m2ts.*`.
- Treat that split as existing project state; do not perform package-wide migrations unless explicitly requested.

## Domain-Specific Notes
- Descriptor-driven workflows are core. Use `examples/` as reference shapes for JSON descriptors.
- Setup menu generation currently builds playlists with:
  - optional intro clip,
  - a background clip loop repeated 50 times,
  - menu attachment through SubPath type 3.
- Preserve PID allocation behavior and descriptor compatibility expectations when changing authoring flows.

## Quick Orientation Paths
- Parent build and dependency versions: `pom.xml`
- Module responsibilities: each module `pom.xml`
- CLI root dispatcher: `brts-cli/src/main/java/org/brts/cli/BrtsMain.java`
- Shared mapper: `brts-common/src/main/java/org/brts/common/json/JsonMapperFactory.java`
- Config precedence utility: `brts-common/src/main/java/org/brts/common/utils/BrtsFileConfig.java`
- Setup menu orchestration: `brts-middle-level/src/main/java/org/brts/middle/menu/SetupMenuGenerator.java`
- Logging config: `brts-cli/src/main/resources/logback.xml`
- Formatter profile: `config/brts-java-formatter.xml`

## Agent Checklist
1. Identify the target layer before editing.
2. Keep edits minimal and boundary-safe.
3. Avoid generated outputs and local caches.
4. Run relevant Maven commands for changed modules.
5. Report architecture impact and verification commands in your handoff.