<p align="center">
  <img src="docs/assets/ratchet4j-logo.png" alt="ratchet4j logo" width="140">
</p>

<h1 align="center">ratchet4j</h1>

<p align="center">
  <b>Fail the build only on NEW static analysis violations.</b><br>
  A baseline and ratchet for Java: Checkstyle, PMD, SpotBugs and SARIF.
</p>

<p align="center">
  <a href="https://github.com/ratchet4j/ratchet4j/actions/workflows/ci.yml"><img src="https://github.com/ratchet4j/ratchet4j/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue.svg" alt="License: Apache 2.0"></a>
  <img src="https://img.shields.io/badge/Java-17%2B-orange.svg" alt="Java 17+">
  <img src="https://img.shields.io/badge/status-early%20(0.x)-yellow.svg" alt="Status: early">
</p>

---

## The problem

You turn on Checkstyle, PMD or SpotBugs on a ten-year-old codebase and get **4,000 violations**.
Your options are all bad: suppress everything in one giant file, fix it all in one huge pull
request, or switch the rule off again.

## The idea

1. **Baseline**: record the violations you have today.
2. **Check**: fail the build only on violations that are *not* in the baseline. Old debt blocks nobody.
3. **Ratchet**: when old violations get fixed, the baseline shrinks, and it can **never grow back**.

Violations are recognized by what the code says, **not by line number**. Inserting lines,
reformatting, moving a method or renaming a file does not turn old debt into "new" violations.

```text
$ ratchet check --input target/checkstyle-result.xml
src/main/java/demo/A.java:2:1: [checkstyle:UnusedImports] Unused import - java.io.File. (NEW)
ratchet4j: 1 new, 0 strict, 2 baselined (tolerated), 0 fixed since baseline
$ echo $?
1
```

## Features

- **Analyzer-agnostic**: one baseline for Checkstyle, PMD, SpotBugs and any tool that writes SARIF
- **Refactoring-proof matching**: content fingerprints with four fallback tiers; each old violation is matched at most once, so duplicates are counted exactly
- **Ratchet**: `update` only ever shrinks the baseline; growing it needs an explicit `--allow-increase`
- **Strict rules**: make chosen rules fail on every occurrence, baselined or not (`--strict 'spotbugs:SQL*'`)
- **Deterministic**: the baseline file is sorted, one entry per line, no timestamps, so Git diffs show only real changes
- **No runtime dependencies**: one small jar, nothing to audit besides this repository

## Install

**Download** `ratchet-<version>.jar` from the latest
[GitHub Release](https://github.com/ratchet4j/ratchet4j/releases), then verify and try it
(needs Java 17+):

```bash
sha256sum -c ratchet-0.1.0.jar.sha256
java -jar ratchet-0.1.0.jar --version
```

**Or build from source** (JDK 17+ and Maven 3.9+):

```bash
git clone https://github.com/ratchet4j/ratchet4j.git
cd ratchet4j
mvn verify
# the runnable jar is now at ratchet-cli/target/ratchet.jar
```

Releases are published every second month; see [docs/RELEASING.md](docs/RELEASING.md). A Maven
Central release is planned.

## Quickstart

```bash
# 1. produce reports with the analyzers you already use
#    (default output locations of their Maven plugins)
mvn checkstyle:checkstyle pmd:pmd compile spotbugs:spotbugs

R="java -jar ratchet-0.1.0.jar"      # or ratchet-cli/target/ratchet.jar when built from source
INPUTS=target/checkstyle-result.xml,target/pmd.xml,target/spotbugsXml.xml

# 2. once: record today's violations, and commit the file
$R baseline --input $INPUTS          # writes ratchet-baseline.json

# 3. in CI, on every pull request: fail on new violations only
$R check --input $INPUTS             # exit code 1 if something new appeared

# 4. after fixing old violations: lock in the progress
$R update --input $INPUTS            # baseline shrinks, never grows

# 5. see where you are
$R report --input $INPUTS
```

### In GitHub Actions

```yaml
- run: mvn -B checkstyle:checkstyle pmd:pmd
- run: java -jar ratchet.jar check --input target/checkstyle-result.xml,target/pmd.xml
```

(`ratchet.jar` is the jar from a release or one you built from source. A ready-made GitHub
Action and a Maven plugin are on the [roadmap](#roadmap).)

## Commands

| Command | What it does |
|---|---|
| `baseline` | Record all current violations. Refuses to overwrite an existing baseline unless `--force`. |
| `check` | Print new violations and exit `1` if there are any. |
| `update` | Ratchet down: drop fixed violations. New violations are *not* added unless `--allow-increase`. |
| `report` | Per-rule table: baseline count, current count, new. |

| Option | Meaning |
|---|---|
| `-i, --input <file>[,<file>...]` | Analyzer reports (Checkstyle/PMD/SpotBugs XML, SARIF); repeatable |
| `-b, --baseline <file>` | Baseline file (default `ratchet-baseline.json`) |
| `--root <dir>` | Repository root (default `.`) |
| `--src <dir>` | Source dir relative to the root, used to resolve SpotBugs paths (repeatable; default `src/main/java`, `src/test/java`) |
| `--context <n>` | Context lines above/below a violation used for matching (default `2`) |
| `--strict <glob>` | Rules that fail on every occurrence, e.g. `'spotbugs:SQL*'` (repeatable) |
| `--allow-increase` | `update`: also add new violations to the baseline |
| `--force` | `baseline`: overwrite an existing baseline |

**Exit codes:** `0` ok, `1` new violations found (or `update` refused to grow the baseline),
`2` usage error, `3` internal error.

## How matching works (short version)

For each violation ratchet4j looks at the line it points to, a few code lines around it, and the
enclosing `Class#method`, never at the line number. It then matches current violations to the
baseline in four tiers, strongest first:

| Tier | Survives |
|---|---|
| 1. exact | line shifts, reformatting, comment changes |
| 2. same line, nearby code edited | edits to neighbouring lines |
| 3. same rule, method and message | edits to the violating line itself |
| 4. same line and context elsewhere | the file was renamed or moved |

Details, the baseline format and the design decisions are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Does it work on real code?

A first benchmark on [google/gson](https://github.com/google/gson) (real Checkstyle 10.17.0
output, baseline from a commit one year older, 52 changed files in between): **355 violations
reported as new; 352 of them are on added or modified lines or in brand-new files**, whereas a
baseline keyed on line numbers would have reported 2,228. This is one repository and an
approximate check, and it also exposed weaknesses. Read the honest write-up in
[docs/BENCHMARK.md](docs/BENCHMARK.md).

## Status

Early (0.x). The core engine, the four readers and the CLI work and are covered by tests, but:

- reader tests use hand-written reports that follow the real layouts; fixtures captured from real tool runs are still to come
- only Checkstyle has been run on a real repository so far
- the CLI options and the baseline schema may change before 1.0

## Roadmap

- [ ] Real PMD and SpotBugs fixtures and a larger benchmark
- [ ] A switch to make tier-3 matching stricter (see the benchmark notes)
- [ ] SARIF output (`baselineState`, `partialFingerprints`) for GitHub code scanning
- [ ] Maven plugin: `ratchet:baseline`, `ratchet:check`, `ratchet:update`
- [ ] GitHub Action that comments only the new violations on a pull request
- [ ] Automatic, verified fixes for the simplest rules (via OpenRewrite)
- [ ] Release to Maven Central

## Contributing

Contributions, bug reports and real-world feedback are very welcome. The most valuable help right
now: run it on your own code and tell us when an old violation shows up as new. See
[CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md).
Security issues: see [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE)
