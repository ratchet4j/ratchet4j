package dev.ratchet4j.core.match;

import dev.ratchet4j.core.model.BaselineEntry;
import dev.ratchet4j.core.model.FingerprintedViolation;

/**
 * A current violation paired with the baseline entry it was recognized as.
 *
 * @param current the violation found now
 * @param baseline the recorded legacy entry
 * @param tier 1 strong, 2 weak, 3 rule+file+scope+message, 4 moved file
 */
public record Match(FingerprintedViolation current, BaselineEntry baseline, int tier) {}
