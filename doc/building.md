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

Common JavaCPP classifiers are:

- Linux x86-64 (`linux-x86_64`; optionally `linux-x86_64-gpl`)
- Windows x86-64 (`windows-x86_64`)
- Intel macOS (`macosx-x86_64`)
- Apple Silicon macOS (`macosx-arm64`)

The optional `BRTS_LINUX_X86_64_GPL` environment profile selects
`linux-x86_64-gpl`; the root POM documents its redistribution restriction.

Override the detected JavaCPP classifier when cross-targeting or diagnosing a
build:

```bash
mvn clean package -Djavacpp.platform=windows-x86_64
```

The classifier is used verbatim in the fat-jar and ZIP filenames. The selected
classifier also determines the packaged launcher (`brts.bat` for Windows,
`brts` for Linux and macOS).

The generated CLI fat jar contains native libraries for one platform and must
run on the platform targeted by that build. Its filename includes the
JavaCPP classifier, and the build produces one matching ZIP distribution.
Build separately for each target when publishing binaries for multiple
platforms; pass `-Djavacpp.platform=<classifier>` to cross-target.

## CLI packaging

`brts-cli` produces a runnable fat jar via `maven-assembly-plugin`, exposing
`org.brts.cli.BrtsMain` as the entry point:

```bash
java -jar brts-cli/target/brts-cli-<classifier>.jar <level> <command> [options]
```

For example, a standard Linux x86-64 build produces:

```text
brts-cli/target/brts-cli-<classifier>.jar
brts-cli/target/brts-<version>-<classifier>.zip
└── brts-<version>-<classifier>/
  ├── assets/
  ├── bin/brts
  ├── etc/brts.conf.example
  └── lib/brts-cli-<classifier>.jar
```

Each build produces one platform-specific fat jar and one matching ZIP. On
Windows, the package contains `bin/brts.bat`; Linux and macOS packages contain
the shell launcher. The standalone fat jar and the ZIP's `lib/` directory
contain the same target-specific jar.

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

Extract the matching platform distribution and run its launcher. For a
standard Linux x86-64 build:

```bash
unzip brts-cli/target/brts-<version>-linux-x86_64.zip
./brts-<version>-linux-x86_64/bin/brts <level> <command> [options]
```

On Windows, extract `brts-<version>-windows-x86_64.zip` and run
`brts-<version>-windows-x86_64\\bin\\brts.bat` with the same CLI arguments.

For the optional Linux GPL build, use the classifier selected by
`BRTS_LINUX_X86_64_GPL` (normally `linux-x86_64-gpl`) in the ZIP filename and
extracted directory name.

The launcher requires Java 21 or newer. It uses `JAVA_HOME` when set and
otherwise finds Java on `PATH`.

On first launch, `etc/brts.conf.example` is copied to `etc/brts.conf`. Later
launches preserve that local configuration. The launcher selects this file and
the distribution's bundled `assets/` directory automatically.