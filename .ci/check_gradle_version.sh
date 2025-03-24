#!/bin/bash
set -e -u -o pipefail

# In CI builds, we do not want to download the Gradle wrapper every time.
# Instead, we use the Gradle instance that is installed in the running Docker container.

# To ensure that CI builds behave exactly like builds using the Gradle wrapper, we enforce that the Gradle version
# installed in the Docker container is the same as the one we are using for the Gradle wrapper.

error_exit() {
  echo >&2 "$1"
  exit 1
}

gradle_version_system="$(gradle --version | sed -n -E '/^Gradle ([0-9][0-9a-zA-Z.-]*)$/,${s//\1/p;q};$q1' || error_exit 'Could not determine installed Gradle version')"
gradle_version_wrapper="$(sed -n -E '/^distributionUrl=.*\/gradle-([0-9][0-9a-zA-Z.-]*)-[a-z]+\.zip$/,${s//\1/p;q};$q1' gradle/wrapper/gradle-wrapper.properties || error_exit 'Could not extract configured Gradle wrapper version')"
[ "$gradle_version_system" = "$gradle_version_wrapper" ] || error_exit "The installed Gradle version $gradle_version_system does not match the configured Gradle wrapper version $gradle_version_wrapper"
