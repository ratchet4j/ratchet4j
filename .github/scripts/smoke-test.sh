#!/usr/bin/env bash
# Usage: smoke-test.sh <path-to-ratchet.jar>
# Black-box test of the built jar: baseline -> check -> shifted lines -> new violation -> update.
set -euo pipefail

jar="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
work="$(mktemp -d)"
cd "$work"
mkdir -p src/main/java/demo
src=src/main/java/demo/A.java
printf 'package demo;\n\nimport java.util.List;\nimport java.util.Map;\n\npublic class A {\n}\n' > "$src"

run() { java -jar "$jar" "$@"; }
fail() { echo "SMOKE TEST FAILED: $*" >&2; exit 1; }

# report <line>...: a Checkstyle-format report with one UnusedImports error per given line
report() {
  {
    printf '<?xml version="1.0"?>\n<checkstyle version="10">\n<file name="%s">\n' "$src"
    for line in "$@"; do
      printf '<error line="%s" column="1" severity="warning" message="Unused import." source="com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck"/>\n' "$line"
    done
    printf '</file>\n</checkstyle>\n'
  } > cs.xml
}
exit_code() { set +e; "$@" > out.txt 2> err.txt; code=$?; set -e; echo "$code"; }

echo "== version"
run --version | grep -q '^ratchet4j ' || fail "--version"

echo "== baseline with two legacy violations"
report 3 4
run baseline --root . --input cs.xml > /dev/null || fail "baseline"
[ "$(grep -c '"rule"' ratchet-baseline.json)" = 2 ] || fail "baseline should hold 2 entries"
[ "$(exit_code run baseline --root . --input cs.xml)" = 2 ] || fail "baseline must refuse to overwrite"

echo "== unchanged code passes"
[ "$(exit_code run check --root . --input cs.xml)" = 0 ] || fail "check on unchanged code"

echo "== lines shifted by an inserted header still pass"
{ printf '// header\n'; cat "$src"; } > shifted.java && mv shifted.java "$src"
report 4 5
[ "$(exit_code run check --root . --input cs.xml)" = 0 ] || fail "check after line shift"

echo "== a new violation fails"
{ printf 'import java.io.File;\n'; cat "$src"; } > shifted.java && mv shifted.java "$src"
report 1 5 6
[ "$(exit_code run check --root . --input cs.xml)" = 1 ] || fail "check with a new violation must exit 1"
grep -q '(NEW)' out.txt || fail "new violation not printed"

echo "== update refuses to grow the baseline"
[ "$(exit_code run update --root . --input cs.xml)" = 1 ] || fail "update must refuse growth"
[ "$(grep -c '"rule"' ratchet-baseline.json)" = 2 ] || fail "baseline must not have grown"

echo "== fixing one legacy violation shrinks the baseline"
report 5
[ "$(exit_code run update --root . --input cs.xml)" = 0 ] || fail "update after fix"
[ "$(grep -c '"rule"' ratchet-baseline.json)" = 1 ] || fail "baseline should shrink to 1 entry"

echo "smoke test passed"
