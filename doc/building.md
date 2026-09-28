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

On Linux, the same build also produces a versioned binary distribution:

```text
brts-cli/target/brts-<version>-linux.zip
└── brts-<version>/
  ├── assets/
  ├── bin/brts
  ├── etc/brts.conf.example
  └── lib/brts-cli.jar
```

Build it with the CLI and all required modules:

```bash
mvn -pl brts-cli -am clean package
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

Extract the Linux distribution and run its launcher:

```bash
unzip brts-cli/target/brts-<version>-linux.zip
./brts-<version>/bin/brts <level> <command> [options]
```

The launcher requires Java 21 or newer. It uses `$JAVA_HOME/bin/java` when
`JAVA_HOME` is set and otherwise finds `java` on `PATH`.

On first launch, `etc/brts.conf.example` is copied to `etc/brts.conf`. Later
launches preserve that local configuration. The launcher selects this file and
the distribution's bundled `assets/` directory automatically, including when
`bin/brts` is invoked through a symbolic link.