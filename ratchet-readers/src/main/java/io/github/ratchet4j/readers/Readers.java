package io.github.ratchet4j.readers;

import io.github.ratchet4j.core.model.Violation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Registry that auto-detects the report format and reads it. */
public final class Readers {

  private static final List<ViolationReader> ALL =
      List.of(
          new SarifReader(),
          new CheckstyleXmlReader(),
          new PmdXmlReader(),
          new SpotBugsXmlReader());

  private static final int SNIFF_BYTES = 4096;

  private Readers() {}

  /** Finds the reader for a report file by looking at its first few KB. */
  public static ViolationReader detect(Path report) throws IOException {
    String head;
    try (InputStream in = Files.newInputStream(report)) {
      head = new String(in.readNBytes(SNIFF_BYTES), StandardCharsets.UTF_8);
    }
    if (head.startsWith("\uFEFF")) {
      head = head.substring(1);
    }
    for (ViolationReader r : ALL) {
      if (r.supports(head)) {
        return r;
      }
    }
    throw new IOException(
        "Unrecognized report format: "
            + report
            + " (supported: SARIF, Checkstyle XML, PMD XML, SpotBugs XML)");
  }

  /** Reads one report file with the auto-detected reader. */
  public static List<Violation> read(Path report, ReaderContext context) throws IOException {
    ViolationReader reader = detect(report);
    try (InputStream in = Files.newInputStream(report)) {
      return reader.read(in, context);
    }
  }

  /** Reads several report files and concatenates their violations. */
  public static List<Violation> readAll(List<Path> reports, ReaderContext context)
      throws IOException {
    List<Violation> all = new ArrayList<>();
    for (Path p : reports) {
      all.addAll(read(p, context));
    }
    return all;
  }
}
