package io.github.ratchet4j.core.model;

import java.util.Comparator;

/**
 * One recorded legacy violation inside the baseline file.
 *
 * @param rule {@code tool:ruleId}
 * @param file repository-relative path
 * @param strong strong fingerprint
 * @param weak weak fingerprint
 * @param move move fingerprint (may be empty)
 * @param scope enclosing {@code Class#method} (may be empty)
 * @param msg original message, kept for humans reading the diff and for tier-3 matching
 */
public record BaselineEntry(
    String rule, String file, String strong, String weak, String move, String scope, String msg) {

  /** Stable sort order used everywhere the baseline is stored or iterated. */
  public static final Comparator<BaselineEntry> ORDER =
      Comparator.comparing(BaselineEntry::file)
          .thenComparing(BaselineEntry::rule)
          .thenComparing(BaselineEntry::strong)
          .thenComparing(BaselineEntry::weak)
          .thenComparing(BaselineEntry::move)
          .thenComparing(BaselineEntry::scope)
          .thenComparing(BaselineEntry::msg);

  /** Null-safe normalization. */
  public BaselineEntry {
    rule = rule == null ? "" : rule;
    file = file == null ? "" : file;
    strong = strong == null ? "" : strong;
    weak = weak == null ? "" : weak;
    move = move == null ? "" : move;
    scope = scope == null ? "" : scope;
    msg = msg == null ? "" : msg;
  }

  /** Builds an entry from a freshly fingerprinted violation. */
  public static BaselineEntry from(FingerprintedViolation fv) {
    Violation v = fv.violation();
    Fingerprint f = fv.fingerprint();
    return new BaselineEntry(
        v.rule(), v.file(), f.strong(), f.weak(), f.move(), f.scope(), v.message());
  }
}
