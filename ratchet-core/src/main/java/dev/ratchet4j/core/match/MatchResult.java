package dev.ratchet4j.core.match;

import dev.ratchet4j.core.model.BaselineEntry;
import dev.ratchet4j.core.model.FingerprintedViolation;
import java.util.List;

/**
 * Outcome of comparing current violations with a baseline.
 *
 * @param matches current violations recognized as baselined legacy debt
 * @param newViolations current violations not present in the baseline
 * @param fixed baseline entries no longer present (fixed, or the code was deleted)
 */
public record MatchResult(
    List<Match> matches, List<FingerprintedViolation> newViolations, List<BaselineEntry> fixed) {

  /** Number of tolerated (baselined) violations still present. */
  public int unchangedCount() {
    return matches.size();
  }
}
