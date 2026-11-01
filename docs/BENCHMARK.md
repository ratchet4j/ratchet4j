# Benchmark: Gson (first real-world run)

A first check that the matching holds up on real code and real history. **One repository, one
analyzer, an approximate ground truth.** Treat it as a smoke test, not as proof.

## Setup

| | |
|---|---|
| Repository | [google/gson](https://github.com/google/gson), directory `gson/src/main/java` |
| Baseline commit (A) | `569279fa`, 2025-10-06 |
| Checked commit (B) | `57731049`, 2026-10-24 |
| Change between A and B | 52 files, 1,786 lines added, 952 deleted |
| Analyzer | Checkstyle 10.17.0, built-in `sun_checks.xml` (deliberately noisy) |
| Reports | real Checkstyle XML at A and at B |
| Context lines | 2 (default) |

Procedure: check out A, `ratchet baseline`; check out B, `ratchet check`.

## Results

| | |
|---|---|
| Violations at A (baseline) | 4,057 across 24 rules |
| Violations at B | 4,218 |
| Reported **new** | **355** |
| Tolerated (matched to baseline) | 3,863 |
| Fixed since baseline | 194 |
| Time (single run, incl. JVM start, one laptop-class machine) | baseline 1.4 s, check 1.7 s |

The numbers reconcile with the raw reports: 3,863 + 194 = 4,057 and 3,863 + 355 = 4,218.

### Are the 355 really new?

Ground truth approximated from `git diff -M -U0 A B`: a new violation is plausible if it sits on an
added/modified line or in a brand-new file.

| Where the 355 sit | Count |
|---|---|
| on an added or modified line | 265 |
| in a brand-new file | 87 |
| on an untouched line | 3 |

Two of the three untouched-line cases are correct detections: `JsonReader.java` grew past the
2,000-line `FileLength` limit and its `doPeek` method past the 150-line `MethodLength` limit. One
`DesignForExtension` case has not been explained.

### Compared with simpler baselines (same data)

| Approach | Reported as new |
|---|---|
| key = file + rule + **line number** | 2,228 |
| key = file + rule + message, counted | 352 |
| **ratchet4j** | **355** |

The count-based approach reaches a similar total, but it cannot say *which* violations are new, so
it cannot annotate a pull request. Whether it hides "fix one, add one" swaps was not measured.

### Which tiers were needed

| Tier | Matches |
|---|---|
| 1 exact | 3,332 |
| 2 neighbouring code edited | 441 |
| 3 violating line edited (same method) | 86 |
| 4 file moved | 4 |

The 4 tier-4 matches were checked by hand: the code really moved from `Gson.java` to `TypeAdapters.java`.

## Weaknesses this run exposed

1. **Tier 3 is too generous for some rules, notably `LineLength`.** Digits in messages are
   normalized, so any over-long line in the same method matches any other baselined over-long line
   there. A developer who fixes one long line and adds another in the same method would not be
   flagged. How often this hides real violations has not been measured. Planned: an option to
   disable or restrict tier 3.
2. **The baseline file is large**: 1,148,014 bytes for 4,057 entries, mostly stored messages.
   Planned: shorter or optional messages.

## Limits of this benchmark

- One repository, one analyzer (Checkstyle). PMD and SpotBugs were not run on real code.
- "On a changed line" is only an approximation: a reformatted line may legitimately keep an old violation.
- Violations that appear because of changes elsewhere (length limits, class-level rules) can look suspicious but be correct.
- The comparison commits were chosen once, not sampled.

Help wanted: run the same procedure on your own repository and open an issue with the numbers
(see CONTRIBUTING.md).
