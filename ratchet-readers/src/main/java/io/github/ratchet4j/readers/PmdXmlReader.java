package io.github.ratchet4j.readers;

import io.github.ratchet4j.core.model.Severity;
import io.github.ratchet4j.core.model.Violation;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Reads PMD's XML report ({@code <pmd><file><violation ...>message</violation></file></pmd>}). */
public final class PmdXmlReader implements ViolationReader {

  @Override
  public String name() {
    return "pmd-xml";
  }

  @Override
  public boolean supports(String head) {
    return head.contains("<pmd ") || head.contains("<pmd>");
  }

  @Override
  public List<Violation> read(InputStream in, ReaderContext ctx) throws IOException {
    List<Violation> out = new ArrayList<>();
    try {
      XMLStreamReader r = Xml.open(in);
      String file = null;
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String el = r.getLocalName();
          if (el.equals("file")) {
            String name = r.getAttributeValue(null, "name");
            file = name == null ? null : ctx.normalizePath(name);
          } else if (el.equals("violation") && file != null) {
            out.add(violation(r, file));
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("file")) {
          file = null;
        }
      }
    } catch (XMLStreamException e) {
      throw new IOException("Invalid PMD XML: " + e.getMessage(), e);
    }
    return out;
  }

  private static Violation violation(XMLStreamReader r, String file) throws XMLStreamException {
    String rule = r.getAttributeValue(null, "rule");
    int line = Xml.intAttr(r, "beginline");
    int col = Xml.intAttr(r, "begincolumn");
    String ruleset = r.getAttributeValue(null, "ruleset");
    String priority = r.getAttributeValue(null, "priority");
    Map<String, String> raw = new LinkedHashMap<>();
    if (ruleset != null) {
      raw.put("ruleset", ruleset);
    }
    if (priority != null) {
      raw.put("priority", priority);
    }
    String message = r.getElementText().trim().replaceAll("\\s+", " ");
    return new Violation(
        "pmd",
        rule == null || rule.isBlank() ? "unknown" : rule,
        severity(priority),
        message,
        file,
        line,
        col,
        raw);
  }

  private static Severity severity(String priority) {
    int p = 3;
    try {
      p = Integer.parseInt(priority == null ? "3" : priority.trim());
    } catch (NumberFormatException ignored) {
      // keep default
    }
    if (p <= 2) {
      return Severity.ERROR;
    }
    return p == 3 ? Severity.WARNING : Severity.INFO;
  }
}
