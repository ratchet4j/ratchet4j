package io.github.ratchet4j.core.model;

import java.util.Comparator;

/** A violation together with its computed fingerprint. */
public record FingerprintedViolation(Violation violation, Fingerprint fingerprint) {

  /** Deterministic display/processing order: file, line, column, rule, message. */
  public static final Comparator<FingerprintedViolation> ORDER =
      Comparator.comparing((FingerprintedViolation f) -> f.violation().file())
          .thenComparingInt(f -> f.violation().line())
          .thenComparingInt(f -> f.violation().column())
          .thenComparing(f -> f.violation().rule())
          .thenComparing(f -> f.violation().message());
}
