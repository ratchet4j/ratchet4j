package dev.ratchet4j.readers;

import dev.ratchet4j.core.model.Violation;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Converts one analyzer report format into the common {@link Violation} model. */
public interface ViolationReader {

  /** Short format name, for messages. */
  String name();

  /** True if the beginning of a report (first few KB, as text) looks like this format. */
  boolean supports(String head);

  /** Reads all violations from the report. */
  List<Violation> read(InputStream in, ReaderContext context) throws IOException;
}
