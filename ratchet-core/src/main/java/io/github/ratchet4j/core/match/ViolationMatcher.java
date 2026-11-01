package io.github.ratchet4j.core.match;

import io.github.ratchet4j.core.model.Baseline;
import io.github.ratchet4j.core.model.BaselineEntry;
import io.github.ratchet4j.core.model.FingerprintedViolation;
import io.github.ratchet4j.core.source.SourceNormalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiered, strictly one-to-one matcher between current violations and baseline entries.
 *
 * <p>All violations go through tier 1, the leftovers through tier 2, and so on, so a weak match can
 * never steal an entry that a strong match would have claimed. Each baseline entry is consumed at
 * most once, which is what keeps duplicate findings counted correctly.
 *
 * <ol>
 *   <li>strong fingerprint
 *   <li>weak fingerprint (surrounding code changed)
 *   <li>same rule, file, scope and message (the violating line itself was edited)
 *   <li>move fingerprint (file renamed or moved)
 * </ol>
 */
public final class ViolationMatcher {

  private static final int TIERS = 4;

  /** Compares the current violations (already fingerprinted and sorted) with the baseline. */
  public MatchResult match(Baseline baseline, List<FingerprintedViolation> current) {
    List<BaselineEntry> entries = baseline.entries();
    boolean[] consumed = new boolean[entries.size()];
    List<Match> matches = new ArrayList<>();
    List<FingerprintedViolation> remaining = new ArrayList<>(current);

    for (int tier = 1; tier <= TIERS && !remaining.isEmpty(); tier++) {
      Map<String, ArrayDeque<Integer>> index = new HashMap<>();
      for (int i = 0; i < entries.size(); i++) {
        if (!consumed[i]) {
          String key = key(tier, entries.get(i));
          if (!key.isEmpty()) {
            index.computeIfAbsent(key, k -> new ArrayDeque<>()).add(i);
          }
        }
      }
      List<FingerprintedViolation> next = new ArrayList<>();
      for (FingerprintedViolation fv : remaining) {
        String key = key(tier, fv);
        ArrayDeque<Integer> queue = key.isEmpty() ? null : index.get(key);
        if (queue != null && !queue.isEmpty()) {
          int i = queue.poll();
          consumed[i] = true;
          matches.add(new Match(fv, entries.get(i), tier));
        } else {
          next.add(fv);
        }
      }
      remaining = next;
    }

    List<BaselineEntry> fixed = new ArrayList<>();
    for (int i = 0; i < entries.size(); i++) {
      if (!consumed[i]) {
        fixed.add(entries.get(i));
      }
    }
    return new MatchResult(
        List.copyOf(matches), List.copyOf(remaining), List.copyOf(fixed));
  }

  private static String key(int tier, FingerprintedViolation fv) {
    return switch (tier) {
      case 1 -> fv.fingerprint().strong();
      case 2 -> fv.fingerprint().weak();
      case 3 ->
          scopeKey(
              fv.violation().rule(),
              fv.violation().file(),
              fv.fingerprint().scope(),
              fv.violation().message());
      default -> fv.fingerprint().move();
    };
  }

  private static String key(int tier, BaselineEntry e) {
    return switch (tier) {
      case 1 -> e.strong();
      case 2 -> e.weak();
      case 3 -> scopeKey(e.rule(), e.file(), e.scope(), e.msg());
      default -> e.move();
    };
  }

  private static String scopeKey(String rule, String file, String scope, String message) {
    if (scope.isEmpty()) {
      return "";
    }
    return rule + "\u0000" + file + "\u0000" + scope + "\u0000"
        + SourceNormalizer.normalizeMessage(message);
  }
}
