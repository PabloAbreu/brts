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

## Native platform selection

Maven automatically selects the FFmpeg native artifact for the host running the
build. The optional OpenCV and OpenBLAS native artifacts use the same platform
when OpenCV is enabled with `-DenableOpenCV`.

Supported build hosts are:

- Linux x86-64 (`linux-x86_64`)
- Windows x86-64 (`windows-x86_64`)
- Intel macOS (`macosx-x86_64`)
- Apple Silicon macOS (`macosx-arm64`)

Override the detected JavaCPP classifier when cross-targeting or diagnosing a
build:

```bash
mvn clean package -Djavacpp.platform=windows-x86_64
```

The generated CLI fat jar contains native libraries for one platform and must
run on the platform targeted by that build. Build separately on each target OS
when publishing binaries for multiple platforms.

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

## Installing

No installer yet.

In the mean time ...

To "install" BRTS, you may create a script named brts and place it on the PATH.
```bash
#! /bin/bash
java -jar /path/to/brts/brts-cli/target/brts-cli.jar "@$"
```

Alternatively, you might want to define an alias for BRTS with something similar to this: 

```bash
alias brts='java -jar $HOME/.m2/repository/org/brts/brts-cli/0.0.1-SNAPSHOT/brts-cli-0.0.1-SNAPSHOT.jar'
alias brts_debug='java -Dlogback.configurationFile=classpath:logback-dev.xml -jar $HOME/.m2/repository/org/brts/brts-cli/0.0.1-SNAPSHOT/brts-cli-0.0.1-SNAPSHOT.jar'
```