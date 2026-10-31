package dev.ratchet4j.readers;

import dev.ratchet4j.core.json.Json;
import dev.ratchet4j.core.model.Severity;
import dev.ratchet4j.core.model.Violation;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads SARIF 2.1.0 logs from any tool.
 *
 * <p>Tolerates missing optional fields: results without a location or region still produce a
 * violation (line 0). Only the first location is used; the number of extra locations is kept in
 * {@code raw}.
 */
public final class SarifReader implements ViolationReader {

  @Override
  public String name() {
    return "sarif";
  }

  @Override
  public boolean supports(String head) {
    String t = head.stripLeading();
    return t.startsWith("{") && t.contains("\"runs\"");
  }

  @Override
  public List<Violation> read(InputStream in, ReaderContext ctx) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    in.transferTo(buf);
    Map<String, Object> root;
    try {
      root = Json.asMap(Json.parse(buf.toString(StandardCharsets.UTF_8)));
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid SARIF JSON: " + e.getMessage(), e);
    }
    List<Violation> out = new ArrayList<>();
    for (Object runObj : Json.asList(root.get("runs"))) {
      Map<String, Object> run = Json.asMap(runObj);
      Map<String, Object> driver = Json.asMap(Json.asMap(run.get("tool")).get("driver"));
      String tool = Json.asString(driver.get("name"));
      tool = tool == null || tool.isBlank() ? "sarif" : tool.toLowerCase(Locale.ROOT);
      for (Object resObj : Json.asList(run.get("results"))) {
        out.add(result(Json.asMap(resObj), tool, ctx));
      }
    }
    return out;
  }

  private static Violation result(Map<String, Object> res, String tool, ReaderContext ctx) {
    String ruleId = Json.asString(res.get("ruleId"));
    if (ruleId == null) {
      ruleId = Json.asString(Json.asMap(res.get("rule")).get("id"));
    }
    String message = Json.asString(Json.asMap(res.get("message")).get("text"));

    List<Object> locations = Json.asList(res.get("locations"));
    String file = "unknown";
    int line = 0;
    int column = 0;
    if (!locations.isEmpty()) {
      Map<String, Object> phys =
          Json.asMap(Json.asMap(locations.get(0)).get("physicalLocation"));
      String uri = Json.asString(Json.asMap(phys.get("artifactLocation")).get("uri"));
      if (uri != null) {
        file = ctx.normalizePath(uri);
      }
      Map<String, Object> region = Json.asMap(phys.get("region"));
      line = (int) Json.asLong(region.get("startLine"), 0);
      column = (int) Json.asLong(region.get("startColumn"), 0);
    }

    Map<String, String> raw = new LinkedHashMap<>();
    String level = Json.asString(res.get("level"));
    if (level != null) {
      raw.put("level", level);
    }
    if (locations.size() > 1) {
      raw.put("extraLocations", Integer.toString(locations.size() - 1));
    }
    return new Violation(
        tool,
        ruleId == null || ruleId.isBlank() ? "unknown" : ruleId,
        severity(level),
        message,
        file,
        line,
        column,
        raw);
  }

  private static Severity severity(String level) {
    if (level == null) {
      return Severity.WARNING;
    }
    return switch (level) {
      case "error" -> Severity.ERROR;
      case "warning" -> Severity.WARNING;
      default -> Severity.INFO;
    };
  }
}
