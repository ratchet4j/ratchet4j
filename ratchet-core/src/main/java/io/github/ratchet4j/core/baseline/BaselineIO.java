package io.github.ratchet4j.core.baseline;

import io.github.ratchet4j.core.json.Json;
import io.github.ratchet4j.core.model.Baseline;
import io.github.ratchet4j.core.model.BaselineEntry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes the baseline file.
 *
 * <p>Output is fully deterministic: entries are sorted, one entry per line, LF line endings, no
 * timestamps, trailing newline. Writing the same baseline twice yields identical bytes, so Git
 * diffs show only real changes.
 */
public final class BaselineIO {

  private BaselineIO() {}

  /** Serializes a baseline to canonical JSON text. */
  public static String toJson(Baseline baseline) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"schema\": ").append(Json.quote(Baseline.SCHEMA)).append(",\n");

    Map<String, Integer> counts = baseline.countsByRule();
    if (counts.isEmpty()) {
      sb.append("  \"countsByRule\": {},\n");
    } else {
      sb.append("  \"countsByRule\": {\n");
      int i = 0;
      for (Map.Entry<String, Integer> e : counts.entrySet()) {
        sb.append("    ").append(Json.quote(e.getKey())).append(": ").append(e.getValue());
        sb.append(++i < counts.size() ? ",\n" : "\n");
      }
      sb.append("  },\n");
    }

    List<BaselineEntry> entries = baseline.entries();
    if (entries.isEmpty()) {
      sb.append("  \"entries\": []\n");
    } else {
      sb.append("  \"entries\": [\n");
      for (int i = 0; i < entries.size(); i++) {
        BaselineEntry e = entries.get(i);
        sb.append("    {");
        sb.append("\"rule\": ").append(Json.quote(e.rule()));
        sb.append(", \"file\": ").append(Json.quote(e.file()));
        sb.append(", \"strong\": ").append(Json.quote(e.strong()));
        sb.append(", \"weak\": ").append(Json.quote(e.weak()));
        sb.append(", \"move\": ").append(Json.quote(e.move()));
        sb.append(", \"scope\": ").append(Json.quote(e.scope()));
        sb.append(", \"msg\": ").append(Json.quote(e.msg()));
        sb.append(i + 1 < entries.size() ? "},\n" : "}\n");
      }
      sb.append("  ]\n");
    }
    sb.append("}\n");
    return sb.toString();
  }

  /** Parses baseline JSON text. Throws {@link IllegalArgumentException} if it is not valid. */
  public static Baseline fromJson(String json) {
    Map<String, Object> root = Json.asMap(Json.parse(json));
    String schema = Json.asString(root.get("schema"));
    if (schema == null || !schema.startsWith("ratchet4j-baseline/")) {
      throw new IllegalArgumentException("Not a ratchet4j baseline file (missing schema)");
    }
    if (!Baseline.SCHEMA.equals(schema)) {
      throw new IllegalArgumentException(
          "Unsupported baseline schema '" + schema + "', expected '" + Baseline.SCHEMA + "'");
    }
    List<BaselineEntry> entries = new ArrayList<>();
    for (Object o : Json.asList(root.get("entries"))) {
      Map<String, Object> m = Json.asMap(o);
      String rule = Json.asString(m.get("rule"));
      String file = Json.asString(m.get("file"));
      if (rule == null || file == null) {
        throw new IllegalArgumentException("Baseline entry without rule or file");
      }
      entries.add(
          new BaselineEntry(
              rule,
              file,
              Json.asString(m.get("strong")),
              Json.asString(m.get("weak")),
              Json.asString(m.get("move")),
              Json.asString(m.get("scope")),
              Json.asString(m.get("msg"))));
    }
    return new Baseline(entries);
  }

  /** Writes the baseline, creating parent directories if needed. */
  public static void write(Path path, Baseline baseline) throws IOException {
    Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.write(path, toJson(baseline).getBytes(StandardCharsets.UTF_8));
  }

  /** Reads a baseline file. */
  public static Baseline read(Path path) throws IOException {
    try {
      return fromJson(Files.readString(path, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid baseline file " + path + ": " + e.getMessage(), e);
    }
  }
}
