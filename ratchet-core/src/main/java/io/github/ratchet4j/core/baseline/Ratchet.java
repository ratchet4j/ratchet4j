package io.github.ratchet4j.core.baseline;

import io.github.ratchet4j.core.match.Match;
import io.github.ratchet4j.core.match.MatchResult;
import io.github.ratchet4j.core.model.Baseline;
import io.github.ratchet4j.core.model.BaselineEntry;
import io.github.ratchet4j.core.model.FingerprintedViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The ratchet: the baseline may only get smaller.
 *
 * <p>Entries that are still present are refreshed with their current fingerprints (so the baseline
 * follows the code as it evolves), entries that disappeared are dropped, and new violations are
 * only added when growth is explicitly allowed.
 */
public final class Ratchet {

  private Ratchet() {}

  /** Computes the next baseline from a match result. */
  public static UpdateResult update(MatchResult result, boolean allowIncrease) {
    List<BaselineEntry> entries = new ArrayList<>();
    for (Match m : result.matches()) {
      entries.add(BaselineEntry.from(m.current()));
    }
    int added = 0;
    if (allowIncrease) {
      for (FingerprintedViolation fv : result.newViolations()) {
        entries.add(BaselineEntry.from(fv));
        added++;
      }
    }
    Map<String, Integer> fixedByRule = new TreeMap<>();
    for (BaselineEntry e : result.fixed()) {
      fixedByRule.merge(e.rule(), 1, Integer::sum);
    }
    int refused = allowIncrease ? 0 : result.newViolations().size();
    return new UpdateResult(
        new Baseline(entries),
        result.fixed().size(),
        added,
        refused,
        result.matches().size(),
        fixedByRule);
  }
}
