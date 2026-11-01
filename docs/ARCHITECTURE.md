# Architecture

ratchet4j sits between your static analyzers and your CI. It never analyzes code itself: it reads
the reports other tools produce, remembers which violations already existed, and tells you which
ones are new.

```text
 Checkstyle XML ─┐
 PMD XML ────────┤
 SpotBugs XML ───┼─► ViolationReader ─► Violation (common model) ─┐
 any SARIF ──────┘      (ratchet-readers)                         │
                                                                  ▼
 source files ───────────────────────────────────────────►  Fingerprinter
                                                      anchor + context + scope + occurrence
                                                                  │
                    ratchet-baseline.json ─────────────►  ViolationMatcher (4 tiers, one-to-one)
                                                                  │
                         ┌────────────────────────────────────────┼──────────────────────┐
                         ▼                                        ▼                      ▼
                  NEW violations                          UNCHANGED (legacy)          FIXED
              `check` fails (Policy)                          tolerated        `update` drops them (Ratchet)
```

## Modules

| Module | Responsibility |
|---|---|
| `ratchet-core` | model, source normalizer, scope heuristic, fingerprinter, matcher, policy, baseline IO, ratchet, `Engine` facade |
| `ratchet-readers` | `ViolationReader` implementations and format auto-detection |
| `ratchet-cli` | `baseline`, `check`, `update`, `report`; builds `ratchet.jar` |

`ratchet-core` and `ratchet-readers` have **no runtime dependencies**. XML is parsed with the JDK's
StAX parser (DTDs and external entities disabled); JSON with a small parser in `ratchet-core`.

## Common model

Every reader converts into one record, and nothing else in the engine knows which tool produced it:

```text
Violation(tool, ruleId, severity, message, file, line, column, raw)
```

`file` is repository-relative with forward slashes; `line` is `0` when the analyzer gave none
(for example class-level SpotBugs findings); `raw` keeps tool-specific data so no information is
lost. An optional `raw["scope"]` hint (`Class#method`) is used for findings without a line.

## Fingerprints

A violation is identified by content, never by line number. For a violation on line *L*:

- **anchor**: text of line *L*, normalized (comments removed outside string literals, whitespace collapsed)
- **context**: up to *N* code lines above and below (default 2), skipping blank and comment lines
- **scope**: enclosing `Class#method`, from a brace-depth heuristic over comment-stripped lines (not a full parser)
- **occurrence**: index among identical findings in the same file, so duplicates are counted exactly

Three SHA-256 based hashes (first 64 bits, hex) are derived:

| Hash | Inputs | Purpose |
|---|---|---|
| `strong` | rule, file, anchor, context, occurrence | exact identity |
| `weak` | rule, file, anchor, occurrence | neighbouring code changed |
| `move` | rule, anchor, context | file renamed or moved |

Findings without a usable line or without readable source use the normalized message (digits
replaced by `#`) as anchor and get **no** `move` hash, because matching them across files would be unsafe.

## Matching

`ViolationMatcher` compares current violations with the baseline in four passes, strongest first.
All violations go through pass 1, the leftovers through pass 2, and so on, so a weak match can never
take an entry that a strong match would have claimed. **Each baseline entry is consumed at most
once.**

| Tier | Key | Survives |
|---|---|---|
| 1 | `strong` | line shifts, reformatting, comment changes |
| 2 | `weak` | edits to neighbouring lines |
| 3 | rule + file + scope + normalized message | edits to the violating line itself |
| 4 | `move` | file renamed or moved |

Result: **new** (not matched), **unchanged** (matched), **fixed** (baseline entries not matched).

Design principle: a false "new" violation blocks developers and destroys trust, so matching is
generous; the one-to-one rule is what keeps it honest. The trade-off of tier 3 is described in
[BENCHMARK.md](BENCHMARK.md).

## Baseline file

```json
{
  "schema": "ratchet4j-baseline/1",
  "countsByRule": { "checkstyle:UnusedImports": 120 },
  "entries": [
    {"rule": "checkstyle:UnusedImports", "file": "src/main/java/A.java", "strong": "a81f…",
     "weak": "92cc…", "move": "41de…", "scope": "A", "msg": "Unused import - java.util.List."}
  ]
}
```

Entries are sorted by (file, rule, strong, …), one per line, LF line endings, trailing newline,
no timestamps. The same baseline therefore always serializes to the same bytes.

## Policy and ratchet

- **Policy**: by default only new violations fail. Rules matching a `--strict` glob (over
  `tool:ruleId`, `*` wildcard) fail on every occurrence.
- **Ratchet**: `update` rebuilds the baseline from the still-present entries (refreshed with their
  current fingerprints so the baseline follows the code). New violations are added only with
  `--allow-increase`; otherwise they are refused and the command exits `1`.

## Readers

| Format | Notes |
|---|---|
| Checkstyle XML | rule id = check class name without package and `Check` suffix |
| PMD XML | severity from `priority`; message is the element text |
| SpotBugs XML | no single line: uses the bug's own source line, then the method's, then the class's; paths are resolved against `--src` directories |
| SARIF 2.1.0 | tool name from `tool.driver.name`; tolerates missing locations/regions |

Format is auto-detected from the first 4 KB of the report.

## Determinism and safety

- Same input gives byte-identical output, whatever the order of the inputs.
- Source paths that escape the repository root are refused.
- XML parsing disables DTDs and external entities; JSON nesting depth is limited.

## Adding an analyzer

See "Adding support for another analyzer" in [CONTRIBUTING.md](../CONTRIBUTING.md).
