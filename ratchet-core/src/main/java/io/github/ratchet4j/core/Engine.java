package io.github.ratchet4j.core;

import io.github.ratchet4j.core.fingerprint.Fingerprinter;
import io.github.ratchet4j.core.match.MatchResult;
import io.github.ratchet4j.core.match.ViolationMatcher;
import io.github.ratchet4j.core.model.Baseline;
import io.github.ratchet4j.core.model.BaselineEntry;
import io.github.ratchet4j.core.model.FingerprintedViolation;
import io.github.ratchet4j.core.model.Violation;
import io.github.ratchet4j.core.source.SourceProvider;
import java.util.List;

/**
 * Entry point of the core: fingerprints violations, creates baselines and compares against them.
 * The CLI, the Maven plugin and any future integration only need this class.
 */
public final class Engine {

  private final Fingerprinter fingerprinter;
  private final ViolationMatcher matcher = new ViolationMatcher();

  /**
   * Creates an engine.
   *
   * @param sources source text provider
   * @param contextLines context lines above/below the anchor, normally 2
   */
  public Engine(SourceProvider sources, int contextLines) {
    this.fingerprinter = new Fingerprinter(sources, contextLines);
  }

  /** Fingerprints violations (sorted by file, line, column). */
  public List<FingerprintedViolation> fingerprint(List<Violation> violations) {
    return fingerprinter.fingerprint(violations);
  }

  /** Records all given violations as the accepted legacy baseline. */
  public Baseline createBaseline(List<Violation> violations) {
    return new Baseline(fingerprint(violations).stream().map(BaselineEntry::from).toList());
  }

  /** Classifies current violations as new, unchanged (baselined) and fixed. */
  public MatchResult compare(Baseline baseline, List<Violation> current) {
    return matcher.match(baseline, fingerprint(current));
  }
}
