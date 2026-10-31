package dev.ratchet4j.core.model;

import java.util.Map;
import java.util.Objects;

/**
 * The common, analyzer-agnostic violation model. Every reader converts its native format into this
 * record; everything else in the engine only ever sees this type.
 *
 * @param tool analyzer name, lower case (for example {@code checkstyle}, {@code pmd}, {@code
 *     spotbugs})
 * @param ruleId the analyzer's own rule identifier, without the tool prefix
 * @param severity normalized severity
 * @param message human readable message exactly as reported
 * @param file repository-relative path using forward slashes
 * @param line 1-based line, or 0 when the analyzer reported no line (class-level findings)
 * @param column 1-based column, or 0 when unknown
 * @param raw tool-specific extra data preserved so no information is lost. The optional key {@code
 *     scope} is a hint ({@code Class#method}) used for findings that have no line
 */
public record Violation(
    String tool,
    String ruleId,
    Severity severity,
    String message,
    String file,
    int line,
    int column,
    Map<String, String> raw) {

  /** Validates and normalizes the components. */
  public Violation {
    Objects.requireNonNull(tool, "tool");
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(file, "file");
    severity = severity == null ? Severity.WARNING : severity;
    message = message == null ? "" : message;
    file = file.replace('\\', '/');
    line = Math.max(line, 0);
    column = Math.max(column, 0);
    raw = raw == null ? Map.of() : Map.copyOf(raw);
  }

  /** Fully qualified rule key, {@code tool:ruleId}. */
  public String rule() {
    return tool + ":" + ruleId;
  }
}
