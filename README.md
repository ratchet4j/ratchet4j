# ratchet4j (working name)

**Fail the build only on NEW static-analysis violations.** Turn on Checkstyle, PMD or SpotBugs
on a legacy codebase without fixing 4,000 old violations first.

1. `baseline`: record every current violation (once).
2. `check`: fail only on violations that are not in the baseline.
3. `update`: when old violations get fixed, the baseline shrinks and can never grow back (the *ratchet*).

Violations are matched by a **content fingerprint, not by line number**, so inserting lines,
reformatting, moving a method or renaming a file does not turn old debt into "new" violations.

## Quickstart

```bash
mvn verify                                  # build + run all tests
# 1. produce reports with the analyzers you already use (default paths of the Maven plugins)
mvn checkstyle:checkstyle pmd:pmd compile spotbugs:spotbugs

# 2. run ratchet4j (the shaded jar is target/ratchet.jar)
R="java -jar ratchet-cli/target/ratchet.jar"
INPUTS=target/checkstyle-result.xml,target/pmd.xml,target/spotbugsXml.xml

$R baseline --input $INPUTS        # once: writes ratchet-baseline.json (commit it)
$R check    --input $INPUTS        # in CI: exit 1 if there are NEW violations
$R update   --input $INPUTS        # after fixing old ones: shrink the baseline
$R report   --input $INPUTS        # per-rule progress table
```

Useful options: `--strict 'spotbugs:SQL*'` (fail on every occurrence of a rule, baselined or not),
`--context 3`, `--root <dir>`, `--src <dir>`, `--allow-increase` (update), `--force` (baseline).
Exit codes: `0` ok, `1` new violations, `2` usage error, `3` internal error.

## Architecture

```
 Checkstyle XML ─┐
 PMD XML ────────┤
 SpotBugs XML ───┼─► ViolationReader ─► Violation (common model) ─┐
 any SARIF ──────┘      (ratchet-readers)                         │
                                                                  ▼
 source files ──────────────────────────────────────────►  Fingerprinter
                                                      (anchor + context + scope + occurrence)
                                                                  │
                     ratchet-baseline.json ──────────────►  ViolationMatcher (4 tiers, 1-to-1)
                                                                  │
                                  ┌───────────────────────────────┼──────────────────────┐
                                  ▼                               ▼                      ▼
                         NEW violations                  UNCHANGED (legacy)          FIXED
                    `check` fails (Policy)                  tolerated        `update` removes (Ratchet)
```

| Module | What it does |
|---|---|
| `ratchet-core` | model, source normalizer, scope heuristic, fingerprinter, matcher, policy, baseline IO, ratchet, `Engine` facade. **No dependencies.** |
| `ratchet-readers` | `ViolationReader` implementations + format auto-detection. **No dependencies** (JDK StAX, XXE-hardened, plus a small JSON parser from core). |
| `ratchet-cli` | `baseline`, `check`, `update`, `report`. Builds one runnable jar. |

### Fingerprints (the heart of the project)
For a violation on line L:
- **anchor**: text of line L, normalized (comments stripped, whitespace collapsed)
- **context**: up to N code lines above and below (blank/comment lines skipped)
- **scope**: enclosing `Class#method` (brace-depth heuristic, not a full parser)
- **occurrence**: index among identical findings, so duplicates are counted exactly

Matching runs in tiers, each baseline entry used **at most once**:

| Tier | Key | Survives |
|---|---|---|
| 1 | rule + file + anchor + context + occurrence | line shifts, reformatting, comments |
| 2 | rule + file + anchor + occurrence | edits to neighbouring lines |
| 3 | rule + file + scope + normalized message | edits to the violating line itself |
| 4 | rule + anchor + context | file renamed / moved |

Findings without a line (class-level SpotBugs) use the normalized message plus a scope hint and
never take part in tier 4.

### Design choices
- **Zero runtime dependencies**: tiny jar, no supply-chain surface, trivial to embed in a Maven plugin later.
- **Own baseline JSON** (sorted, one entry per line, no timestamps): byte-identical output, clean Git diffs.
- **A false "new violation" is worse than a missed one**; matching therefore falls through four tiers.
- **Baseline can only shrink**: `baseline` refuses to overwrite without `--force`; `update` refuses to add new violations without `--allow-increase`.

## Status and honesty

Tested (61 tests): the 20 refactoring scenarios (line shifts, reformatting, comments, moved methods,
renamed files, duplicates, one-entry-cannot-match-twice, ...), determinism, ratchet invariants,
JSON, readers, XXE, and an end-to-end CLI lifecycle. Mutation-checked: deliberately breaking the
matcher (dropping a tier, letting an entry match twice, putting line numbers into fingerprints) makes tests fail.

**Not yet done / caveats**
- Reader tests use **hand-written** reports that follow the real layouts. Next step (P1-T3): generate fixtures from real Checkstyle/PMD/SpotBugs runs and add them.
- Not yet benchmarked on real open-source repos (false-NEW rate on real history is the key number to measure).
- Scope detection is a heuristic; unusual code (e.g. text blocks) can confuse it. It only feeds tier 3.
- The Maven poms were not executed by the author's environment (no Maven access); the code was compiled and tested with `javac` directly. Run `mvn verify` first and report anything that fails.

## Roadmap
1. Real-tool fixtures + real-repo benchmark
2. SARIF output with `baselineState` and `partialFingerprints` (GitHub code scanning)
3. Maven plugin (`ratchet:baseline|check|update`)
4. GitHub Action: PR comment listing only new violations
5. Quality gates on this repo + dogfooding (Spotless, Checkstyle, PMD, SpotBugs, PIT)
6. Auto-fix engine (OpenRewrite-based, verify loop, small single-rule PRs)
7. Open-core extras: multi-repo dashboard, policy-as-code, SSO, audit reports
