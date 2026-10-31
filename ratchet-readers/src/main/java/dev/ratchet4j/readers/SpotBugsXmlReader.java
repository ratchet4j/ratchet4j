package dev.ratchet4j.readers;

import dev.ratchet4j.core.model.Severity;
import dev.ratchet4j.core.model.Violation;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads SpotBugs' XML report ({@code <BugCollection><BugInstance>...}).
 *
 * <p>SpotBugs reports paths relative to a source root and sometimes no line at all. The reader
 * prefers the bug's own source line, then the method's, then the class's; when none exists the
 * violation has line 0 and carries a {@code scope} hint ({@code Class#method}).
 */
public final class SpotBugsXmlReader implements ViolationReader {

  @Override
  public String name() {
    return "spotbugs-xml";
  }

  @Override
  public boolean supports(String head) {
    return head.contains("<BugCollection");
  }

  /** Mutable accumulator for the BugInstance currently being parsed. */
  private static final class Bug {
    String type;
    String priority;
    String category;
    String rank;
    String longMessage;
    String shortMessage;
    String className;
    String methodName;
    String directPath;
    int directLine;
    boolean directPrimary;
    boolean haveDirect;
    String methodPath;
    int methodLine;
    String classPath;
    int classLine;
  }

  @Override
  public List<Violation> read(InputStream in, ReaderContext ctx) throws IOException {
    List<Violation> out = new ArrayList<>();
    try {
      XMLStreamReader r = Xml.open(in);
      Deque<String> stack = new ArrayDeque<>();
      Bug bug = null;
      while (r.hasNext()) {
        int ev = r.next();
        if (ev == XMLStreamConstants.START_ELEMENT) {
          String el = r.getLocalName();
          String parent = stack.peek();
          stack.push(el);
          if (el.equals("BugInstance")) {
            bug = new Bug();
            bug.type = r.getAttributeValue(null, "type");
            bug.priority = r.getAttributeValue(null, "priority");
            bug.category = r.getAttributeValue(null, "category");
            bug.rank = r.getAttributeValue(null, "rank");
          } else if (bug != null && collect(r, el, parent, bug)) {
            // getElementText() already consumed the matching end tag
            stack.pop();
          }
        } else if (ev == XMLStreamConstants.END_ELEMENT) {
          stack.pop();
          if (r.getLocalName().equals("BugInstance") && bug != null) {
            out.add(toViolation(bug, ctx));
            bug = null;
          }
        }
      }
    } catch (XMLStreamException e) {
      throw new IOException("Invalid SpotBugs XML: " + e.getMessage(), e);
    }
    return out;
  }

  /** Collects data from an element inside a BugInstance; true if its end tag was consumed. */
  private static boolean collect(XMLStreamReader r, String el, String parent, Bug bug)
      throws XMLStreamException {
    switch (el) {
      case "LongMessage" -> {
        if ("BugInstance".equals(parent)) {
          bug.longMessage = r.getElementText().trim();
          return true;
        }
      }
      case "ShortMessage" -> {
        if ("BugInstance".equals(parent)) {
          bug.shortMessage = r.getElementText().trim();
          return true;
        }
      }
      case "Class" -> {
        if ("BugInstance".equals(parent) && bug.className == null) {
          bug.className = r.getAttributeValue(null, "classname");
        }
      }
      case "Method" -> {
        if ("BugInstance".equals(parent) && bug.methodName == null) {
          bug.methodName = r.getAttributeValue(null, "name");
        }
      }
      case "SourceLine" -> sourceLine(r, parent, bug);
      default -> {
        // ignored
      }
    }
    return false;
  }

  private static void sourceLine(XMLStreamReader r, String parent, Bug bug) {
    String path = r.getAttributeValue(null, "sourcepath");
    int start = Xml.intAttr(r, "start");
    if ("BugInstance".equals(parent)) {
      boolean primary = "true".equals(r.getAttributeValue(null, "primary"));
      if (!bug.haveDirect || (primary && !bug.directPrimary)) {
        bug.haveDirect = true;
        bug.directPrimary = primary;
        bug.directPath = path;
        bug.directLine = start;
      }
    } else if ("Method".equals(parent) && bug.methodPath == null) {
      bug.methodPath = path;
      bug.methodLine = start;
    } else if ("Class".equals(parent) && bug.classPath == null) {
      bug.classPath = path;
      bug.classLine = start;
    }
  }

  private static Violation toViolation(Bug b, ReaderContext ctx) {
    String path;
    int line;
    if (b.haveDirect && b.directPath != null && b.directLine > 0) {
      path = b.directPath;
      line = b.directLine;
    } else if (b.methodPath != null && b.methodLine > 0) {
      path = b.methodPath;
      line = b.methodLine;
    } else if (b.classPath != null) {
      path = b.classPath;
      line = Math.max(b.classLine, 0);
    } else if (b.directPath != null) {
      path = b.directPath;
      line = 0;
    } else {
      path = classToPath(b.className);
      line = 0;
    }
    String message =
        b.longMessage != null ? b.longMessage : b.shortMessage != null ? b.shortMessage : b.type;

    Map<String, String> raw = new LinkedHashMap<>();
    putIfPresent(raw, "category", b.category);
    putIfPresent(raw, "priority", b.priority);
    putIfPresent(raw, "rank", b.rank);
    putIfPresent(raw, "class", b.className);
    putIfPresent(raw, "method", b.methodName);
    String scope = scope(b.className, b.methodName);
    if (!scope.isEmpty()) {
      raw.put("scope", scope);
    }
    return new Violation(
        "spotbugs",
        b.type == null ? "unknown" : b.type,
        severity(b.priority),
        message,
        ctx.resolveSourcePath(path),
        line,
        0,
        raw);
  }

  private static String classToPath(String className) {
    if (className == null) {
      return "unknown";
    }
    int dollar = className.indexOf('$');
    String top = dollar >= 0 ? className.substring(0, dollar) : className;
    return top.replace('.', '/') + ".java";
  }

  private static String scope(String className, String method) {
    String cls = "";
    if (className != null) {
      String simple = className.substring(className.lastIndexOf('.') + 1);
      cls = simple.substring(simple.lastIndexOf('$') + 1);
    }
    if (method == null || method.isEmpty()) {
      return cls;
    }
    return cls + "#" + method;
  }

  private static void putIfPresent(Map<String, String> map, String key, String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static Severity severity(String priority) {
    if ("1".equals(priority)) {
      return Severity.ERROR;
    }
    return "2".equals(priority) ? Severity.WARNING : Severity.INFO;
  }
}
