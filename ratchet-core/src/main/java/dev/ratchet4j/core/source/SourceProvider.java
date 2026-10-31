package dev.ratchet4j.core.source;

import java.util.List;

/** Supplies the text of source files so fingerprints can be computed from content. */
public interface SourceProvider {

  /**
   * Returns the lines of a repository-relative file, or an empty list if the file is missing or
   * unreadable.
   */
  List<String> lines(String file);
}
