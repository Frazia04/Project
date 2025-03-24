#!/bin/bash
set -e -u -o pipefail

error_exit() {
  echo >&2 "$1"
  exit 1
}

# Retrieve version from gradle.properties
projectVersion="$(sed -n -E 's/^version=(.+)$/\1/p' gradle.properties)"
[ -n "$projectVersion" ] || error_exit "Could not retrieve version from gradle.properties"

# Check whether we build for a tag
gitTagVersion="$(echo "${CI_COMMIT_TAG:-}" | sed -n -E 's/^v([[:digit:]]+\.[[:digit:]]\.[[:digit:]])$/\1/p')"
if [ -n "$gitTagVersion" ] ; then
  projectVersionWithoutSnapshot="$(echo "$projectVersion" | sed 's/-SNAPSHOT$//')"
  [ "$gitTagVersion" =  "$projectVersionWithoutSnapshot" ] || error_exit "Git tag $CI_COMMIT_TAG does not match version=$projectVersion in gradle.properties! You should set version=$gitTagVersion-SNAPSHOT before tagging $gitTagVersion."
  echo "Setting version from git tag: $gitTagVersion"
  sed -i -E "s/^(version=).*$/\1$gitTagVersion/" gradle.properties
  exit 0
fi

# Otherwise add the commit hash
if [ -n "${CI_COMMIT_SHORT_SHA:-}" ] ; then
  versionWithHash="$projectVersion+$CI_COMMIT_SHORT_SHA"
  echo "Setting version from git commit hash: $versionWithHash"
  sed -i -E "s/^(version=).*$/\1$versionWithHash/" gradle.properties
  exit 0
fi

error_exit "Could not detect git commit from environment variables CI_COMMIT_TAG / CI_COMMIT_SHORT_SHA!"
