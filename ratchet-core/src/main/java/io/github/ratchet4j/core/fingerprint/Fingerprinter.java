package io.github.ratchet4j.core.fingerprint;

import io.github.ratchet4j.core.model.Fingerprint;
import io.github.ratchet4j.core.model.FingerprintedViolation;
import io.github.ratchet4j.core.model.Violation;
import io.github.ratchet4j.core.source.SourceNormalizer;
import io.github.ratchet4j.core.source.SourceProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Computes content-based fingerprints.
 *
 * <p>For a violation on line L of a file:
 *
 * <ul>
 *   <li><b>anchor</b>: normalized text of line L (whitespace collapsed, comments removed)
 *   <li><b>context</b>: up to N code lines above and N below, skipping blank/comment lines
 *   <li><b>scope</b>: enclosing {@code Class#method}
 *   <li><b>occurrence</b>: index among identical findings in the same file
 * </ul>
 *
 * Findings without a usable line or source (for example class-level SpotBugs findings, or files
 * that no longer exist) use the normalized message as anchor and the reader-provided scope hint.
 * Such findings get no move fingerprint, because matching them across files would be unsafe.
 */
public final class Fingerprinter {

  private static final Comparator<Violation> IN_FILE_ORDER =
      Comparator.comparingInt(Violation::line)
          .thenComparingInt(Violation::column)
          .thenComparing(Violation::rule)
          .thenComparing(Violation::message);

  private final SourceProvider sources;
  private final int contextLines;

  /**
   * Creates a fingerprinter.
   *
   * @param sources where to read source text from
   * @param contextLines number of code lines kept above and below the anchor (typically 2)
   */
  public Fingerprinter(SourceProvider sources, int contextLines) {
    this.sources = sources;
    this.contextLines = Math.max(0, contextLines);
  }

  /** Fingerprints all violations. The result is sorted by file, line, column, rule, message. */
  public List<FingerprintedViolation> fingerprint(List<Violation> violations) {
    Map<String, List<Violation>> byFile = new TreeMap<>();
    for (Violation v : violations) {
      byFile.computeIfAbsent(v.file(), k -> new ArrayList<>()).add(v);
    }
    List<FingerprintedViolation> out = new ArrayList<>(violations.size());
    for (Map.Entry<String, List<Violation>> e : byFile.entrySet()) {
      List<String> lines = sources.lines(e.getKey());
      FileView view = lines.isEmpty() ? null : new FileView(lines);
      List<Violation> sorted = new ArrayList<>(e.getValue());
      sorted.sort(IN_FILE_ORDER);
      Map<String, Integer> strongOcc = new HashMap<>();
      Map<String, Integer> weakOcc = new HashMap<>();
      for (Violation v : sorted) {
        out.add(one(v, view, strongOcc, weakOcc));
      }
    }
    return out;
  }

  private FingerprintedViolation one(
      Violation v, FileView view, Map<String, Integer> strongOcc, Map<String, Integer> weakOcc) {
    String anchor = "";
    String context = "";
    String scope;
    boolean anchored = view != null && v.line() >= 1 && v.line() <= view.size();
    if (anchored) {
      int idx = v.line() - 1;
      anchor = view.anchorAt(idx);
      anchored = !anchor.isEmpty();
      if (anchored) {
        context = context(view, idx);
      }
    }
    if (anchored) {
      scope = view.scopes[v.line() - 1];
    } else {
      anchor = "@" + SourceNormalizer.normalizeMessage(v.message());
      scope = v.raw().getOrDefault("scope", "");
    }

    String rule = v.rule();
    String strongKey = rule + "\u0001" + anchor + "\u0001" + context;
    String weakKey = rule + "\u0001" + anchor;
    int strongIdx = strongOcc.merge(strongKey, 1, Integer::sum) - 1;
    int weakIdx = weakOcc.merge(weakKey, 1, Integer::sum) - 1;

    String strong = Hashing.hash(rule, v.file(), anchor, context, Integer.toString(strongIdx));
    String weak = Hashing.hash(rule, v.file(), anchor, Integer.toString(weakIdx));
    String move = anchored ? Hashing.hash(rule, anchor, context) : "";
    return new FingerprintedViolation(v, new Fingerprint(strong, weak, move, scope));
  }

  private String context(FileView view, int idx) {
    List<String> above = new ArrayList<>(contextLines);
    for (int i = idx - 1; i >= 0 && above.size() < contextLines; i--) {
      if (!view.noise[i]) {
        above.add(view.norm[i]);
      }
    }
    List<String> below = new ArrayList<>(contextLines);
    for (int i = idx + 1; i < view.size() && below.size() < contextLines; i++) {
      if (!view.noise[i]) {
        below.add(view.norm[i]);
      }
    }
    StringBuilder sb = new StringBuilder();
    for (int i = above.size() - 1; i >= 0; i--) {
      sb.append(above.get(i)).append('\n');
    }
    sb.append("--");
    for (String b : below) {
      sb.append('\n').append(b);
    }
    return sb.toString();
  }
}
