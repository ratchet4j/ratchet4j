package dev.ratchet4j.readers;

import dev.ratchet4j.core.model.Severity;
import dev.ratchet4j.core.model.Violation;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** Reads Checkstyle's XML report ({@code <checkstyle><file><error .../></file></checkstyle>}). */
public final class CheckstyleXmlReader implements ViolationReader {

  @Override
  public String name() {
    return "checkstyle-xml";
  }

  @Override
  public boolean supports(String head) {
    return head.contains("<checkstyle");
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
          } else if (el.equals("error") && file != null) {
            out.add(error(r, file));
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT && r.getLocalName().equals("file")) {
          file = null;
        }
      }
    } catch (XMLStreamException e) {
      throw new IOException("Invalid Checkstyle XML: " + e.getMessage(), e);
    }
    return out;
  }

  private static Violation error(XMLStreamReader r, String file) {
    String source = r.getAttributeValue(null, "source");
    String sev = r.getAttributeValue(null, "severity");
    Map<String, String> raw = new LinkedHashMap<>();
    if (source != null) {
      raw.put("source", source);
    }
    if (sev != null) {
      raw.put("severity", sev);
    }
    return new Violation(
        "checkstyle",
        ruleId(source),
        severity(sev),
        r.getAttributeValue(null, "message"),
        file,
        Xml.intAttr(r, "line"),
        Xml.intAttr(r, "column"),
        raw);
  }

  /** {@code com.puppycrawl...imports.UnusedImportsCheck} becomes {@code UnusedImports}. */
  static String ruleId(String source) {
    if (source == null || source.isBlank()) {
      return "unknown";
    }
    String s = source.substring(source.lastIndexOf('.') + 1);
    if (s.endsWith("Check") && s.length() > "Check".length()) {
      s = s.substring(0, s.length() - "Check".length());
    }
    return s;
  }

  private static Severity severity(String s) {
    if (s == null) {
      return Severity.WARNING;
    }
    return switch (s.toLowerCase(Locale.ROOT)) {
      case "error" -> Severity.ERROR;
      case "warning" -> Severity.WARNING;
      default -> Severity.INFO;
    };
  }
}
