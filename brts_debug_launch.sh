#! /bin/bash

# Go to the brts-cli module directory
BRT_ROOT="$(dirname "$0")"
pushd $BRT_ROOT/brts-cli > /dev/null

# we use a mechanism to cache the result of 'mvn dependency:build-classpath' to avoid the overhead of invoking Maven every time
# when adding/changing dependencies, delete the .cached_classpath file to regenerate it
if [ ! -f .cached_classpath ]; then
    echo "Generating classpath cache..."
    mvn -q dependency:build-classpath -Dmdep.outputFile=.cached_classpath -Dmdep.includeScope=runtime -DexcludeGroupIds=org.brts
fi


# Build the classpath: target/classes + all dependencies from the cached classpath
CP="$BRT_ROOT/brts-cli/target/classes:$(cat .cached_classpath | tail -n 1)"

# add other modules' target/classes too
CP="$BRT_ROOT/brts-high-level/target/classes:$BRT_ROOT/brts-middle-level/target/classes:$BRT_ROOT/brts-low-level/target/classes:$BRT_ROOT/brts-common/target/classes:$BRT_ROOT/external_references/jebml/target/classes:$CP"

popd > /dev/null
# Launch your main class
java -cp "$CP" org.brts.cli.BrtsMain "$@"
