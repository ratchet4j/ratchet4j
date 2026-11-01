package io.github.ratchet4j.core;

import io.github.ratchet4j.core.model.Severity;
import io.github.ratchet4j.core.model.Violation;
import io.github.ratchet4j.core.source.SourceProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Shared helpers: an in-memory project and a fake analyzer that flags every {@code bad(} call. */
final class TestSupport {

  private TestSupport() {}

  /** Joins lines with LF. */
  static String src(String... lines) {
    return String.join("\n", lines);
  }

  /** Builds an in-memory source provider from file name to file text. */
  static SourceProvider sources(Map<String, String> files) {
    Map<String, List<String>> split = new TreeMap<>();
    files.forEach((name, text) -> split.put(name, List.of(text.split("\n", -1))));
    return file -> split.getOrDefault(file, List.of());
  }

  static Engine engine(Map<String, String> files, int context) {
    return new Engine(sources(files), context);
  }

  /** Fake analyzer: one violation for every line containing {@code bad(}. */
  static List<Violation> analyze(Map<String, String> files) {
    List<Violation> out = new ArrayList<>();
    new TreeMap<>(files)
        .forEach(
            (name, text) -> {
              String[] lines = text.split("\n", -1);
              for (int i = 0; i < lines.length; i++) {
                int col = lines[i].indexOf("bad(");
                if (col >= 0) {
                  out.add(
                      new Violation(
                          "fake",
                          "BadCall",
                          Severity.WARNING,
                          "Call to bad() is not allowed",
                          name,
                          i + 1,
                          col + 1,
                          Map.of()));
                }
              }
            });
    return out;
  }

  /** A small class with two violations in different methods. */
  static final String SERVICE =
      src(
          "package demo;",
          "",
          "import java.util.List;",
          "",
          "public class Service {",
          "    public void first() {",
          "        int a = 1;",
          "        bad();",
          "        int b = 2;",
          "    }",
          "",
          "    public void second() {",
          "        int c = 3;",
          "        bad();",
          "        int d = 4;",
          "    }",
          "}");
}
