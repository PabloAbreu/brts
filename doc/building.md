# Building, Testing, and Running


## Pre-requisite

BRTS depends on https://github.com/PabloAbreu/jebml .

So you need to clone and build that first, because its artifacts are not available on maven central.


## Build system

Maven multi-module project, parent [`pom.xml`](../pom.xml), Java 21.

- Full build: `mvn clean package`
- Full test suite: `mvn test`
- Module-scoped build/test: `mvn -pl <module> clean test`
  (module names: `brts-common`, `brts-low-level`, `brts-middle-level`,
  `brts-high-level`, `brts-cli`)

Define an alias for BRTS with something similar to this: 

```bash
alias brts='java -jar $HOME/.m2/repository/org/brts/brts-cli/1.0.0-SNAPSHOT/brts-cli-1.0.0-SNAPSHOT.jar'
```

## CLI packaging

`brts-cli` produces a runnable fat jar via `maven-assembly-plugin`, exposing
`org.brts.cli.BrtsMain` as the entry point:

```bash
java -jar brts-cli/target/brts-cli.jar <level> <command> [options]
```

## Local debug launcher

For BRTS developers only.

[`brts_debug_launch.sh`](../brts_debug_launch.sh) runs the CLI directly
against compiled `target/classes` of every module, without repackaging:

```bash
./brts_debug_launch.sh <level> <command> [options]
```

It caches the runtime dependency classpath in `brts-cli/.cached_classpath`
(via `mvn dependency:build-classpath`) to avoid invoking Maven on every run.
**Delete `.cached_classpath` after changing dependencies** so it gets
regenerated.

## Formatting and style

- The parent build applies `net.revelc.code.formatter:formatter-maven-plugin`.
- Canonical formatter config: [`config/brts-java-formatter.xml`](../config/brts-java-formatter.xml).
- Expected encoding and line endings: UTF-8 and LF.
- Run the relevant Maven build/test commands before finishing a change so
  formatting and compile issues surface early.

## Test samples

Some tests reference real Blu-ray sample assets under `samples/` (the
`test.samples.dir` Maven property points there), and generated fixtures are
written under `test_output/` for inspection.
