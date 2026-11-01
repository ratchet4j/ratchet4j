package io.github.ratchet4j.core.fingerprint;

import io.github.ratchet4j.core.source.ScopeMap;
import io.github.ratchet4j.core.source.SourceNormalizer;
import java.util.List;

/** Pre-computed normalized view of one source file, built once per file. */
final class FileView {

  final List<String> raw;
  final String[] norm;
  final boolean[] noise;
  final String[] scopes;

  FileView(List<String> raw) {
    this.raw = raw;
    int n = raw.size();
    this.norm = new String[n];
    this.noise = new boolean[n];
    for (int i = 0; i < n; i++) {
      norm[i] = SourceNormalizer.normalizeLine(raw.get(i));
      noise[i] = SourceNormalizer.isNoise(raw.get(i), norm[i]);
    }
    this.scopes = ScopeMap.compute(norm);
  }

  int size() {
    return raw.size();
  }

  /** The anchor text of a line: normalized code, or the collapsed raw text for comment-only lines. */
  String anchorAt(int index) {
    String a = norm[index];
    return a.isEmpty() ? SourceNormalizer.collapse(raw.get(index)) : a;
  }
}
