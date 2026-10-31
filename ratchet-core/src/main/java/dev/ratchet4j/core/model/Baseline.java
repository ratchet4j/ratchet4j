package dev.ratchet4j.core.model;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The set of accepted legacy violations. Entries are always held in canonical sorted order. */
public record Baseline(List<BaselineEntry> entries) {

  /** Schema identifier written into the baseline file. */
  public static final String SCHEMA = "ratchet4j-baseline/1";

  /** Copies and sorts the entries so equal baselines are always byte-identical when written. */
  public Baseline {
    entries = entries.stream().sorted(BaselineEntry.ORDER).toList();
  }

  /** An empty baseline. */
  public static Baseline empty() {
    return new Baseline(List.of());
  }

  /** Number of baselined violations. */
  public int size() {
    return entries.size();
  }

  /** Violation count per {@code tool:ruleId}, sorted by rule. */
  public Map<String, Integer> countsByRule() {
    Map<String, Integer> counts = new TreeMap<>();
    for (BaselineEntry e : entries) {
      counts.merge(e.rule(), 1, Integer::sum);
    }
    return counts;
  }
}
