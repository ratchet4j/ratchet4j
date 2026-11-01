#!/usr/bin/env bash
# Usage: next-version.sh <last-tag> [major|minor|patch]
# Prints the next version (without the leading "v"). Default bump: minor.
set -euo pipefail

last="${1:?usage: next-version.sh <last-tag> [major|minor|patch]}"
bump="${2:-minor}"
last="${last#v}"

if [[ ! "$last" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-.*)?$ ]]; then
  echo "not a version: $last" >&2
  exit 2
fi
major="${BASH_REMATCH[1]}"
minor="${BASH_REMATCH[2]}"
patch="${BASH_REMATCH[3]}"

case "$bump" in
  major) echo "$((major + 1)).0.0" ;;
  minor) echo "${major}.$((minor + 1)).0" ;;
  patch) echo "${major}.${minor}.$((patch + 1))" ;;
  *) echo "unknown bump type: $bump" >&2; exit 2 ;;
esac
