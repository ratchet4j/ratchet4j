package io.github.ratchet4j.core.baseline;

import io.github.ratchet4j.core.model.Baseline;
import java.util.Map;

/**
 * Result of a ratchet update.
 *
 * @param baseline the new baseline to store
 * @param fixed baseline entries that disappeared (removed from the baseline)
 * @param added new violations added (only when an increase was explicitly allowed)
 * @param refused new violations that were NOT added because the baseline may only shrink
 * @param unchanged legacy violations that are still present
 * @param fixedByRule number of fixed entries per rule
 */
public record UpdateResult(
    Baseline baseline,
    int fixed,
    int added,
    int refused,
    int unchanged,
    Map<String, Integer> fixedByRule) {}
