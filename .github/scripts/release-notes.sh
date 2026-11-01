#!/usr/bin/env bash
# Usage: release-notes.sh <version> [previous-tag]
# Prints the GitHub release notes: the CHANGELOG section for the version (falling back to
# "Unreleased"), download/verify instructions, and a compare link.
set -euo pipefail

version="${1:?usage: release-notes.sh <version> [previous-tag]}"
prev="${2:-}"
repo="${GITHUB_REPOSITORY:-ratchet4j/ratchet4j}"

section() {
  awk -v h="## [$1]" '
    index($0, h) == 1 { on = 1; next }
    on && /^## \[/ { exit }
    on { print }
  ' CHANGELOG.md
}

body="$(section "$version" || true)"
if [ -z "$(printf '%s' "$body" | tr -d '[:space:]')" ]; then
  body="$(section "Unreleased" || true)"
fi
if [ -z "$(printf '%s' "$body" | tr -d '[:space:]')" ]; then
  body="_No changelog entry for this release; see the commits._"
fi

cat << NOTES
## ratchet4j ${version}

${body}

### Download

- \`ratchet-${version}.jar\`: runnable jar (Java 17 or newer): \`java -jar ratchet-${version}.jar --version\`
- \`ratchet-${version}.jar.sha256\`: verify with \`sha256sum -c ratchet-${version}.jar.sha256\`
NOTES

if [ -n "$prev" ]; then
  printf '\n**Full changelog:** https://github.com/%s/compare/%s...v%s\n' "$repo" "$prev" "$version"
fi
