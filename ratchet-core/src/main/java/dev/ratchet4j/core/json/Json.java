package dev.ratchet4j.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader and string quoting helper.
 *
 * <p>Parsed values are {@link Map} (objects, insertion ordered), {@link List}, {@link String},
 * {@link Long} / {@link Double}, {@link Boolean} or {@code null}. Nesting depth is limited so
 * hostile input cannot overflow the stack.
 */
public final class Json {

  private static final int MAX_DEPTH = 256;

  private Json() {}

  /** Parses a complete JSON document. Throws {@link IllegalArgumentException} on bad input. */
  public static Object parse(String text) {
    Parser p = new Parser(text);
    p.skipWs();
    Object value = p.value(0);
    p.skipWs();
    if (p.pos != text.length()) {
      throw p.error("Unexpected trailing content");
    }
    return value;
  }

  /** Returns a JSON string literal (with quotes) for the given text. */
  public static String quote(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 2);
    sb.append('"');
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"' -> sb.append("\\\"");
        case '\\' -> sb.append("\\\\");
        case '\n' -> sb.append("\\n");
        case '\r' -> sb.append("\\r");
        case '\t' -> sb.append("\\t");
        default -> {
          if (c < 0x20) {
            sb.append(String.format("\\u%04x", (int) c));
          } else {
            sb.append(c);
          }
        }
      }
    }
    return sb.append('"').toString();
  }

  /** Casts to an object map, or returns an empty map if the value is not an object. */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> asMap(Object o) {
    return o instanceof Map ? (Map<String, Object>) o : Map.of();
  }

  /** Casts to a list, or returns an empty list if the value is not an array. */
  @SuppressWarnings("unchecked")
  public static List<Object> asList(Object o) {
    return o instanceof List ? (List<Object>) o : List.of();
  }

  /** Returns the string value, or {@code null} if the value is not a string. */
  public static String asString(Object o) {
    return o instanceof String s ? s : null;
  }

  /** Returns the numeric value as long, or {@code dflt} if the value is not a number. */
  public static long asLong(Object o, long dflt) {
    return o instanceof Number n ? n.longValue() : dflt;
  }

  private static final class Parser {
    private final String s;
    private int pos;

    Parser(String s) {
      this.s = s;
    }

    IllegalArgumentException error(String msg) {
      return new IllegalArgumentException(msg + " at position " + pos);
    }

    void skipWs() {
      while (pos < s.length()) {
        char c = s.charAt(pos);
        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
          pos++;
        } else {
          break;
        }
      }
    }

    Object value(int depth) {
      if (depth > MAX_DEPTH) {
        throw error("Nesting too deep");
      }
      if (pos >= s.length()) {
        throw error("Unexpected end of input");
      }
      char c = s.charAt(pos);
      switch (c) {
        case '{':
          return object(depth);
        case '[':
          return array(depth);
        case '"':
          return string();
        case 't':
          return literal("true", Boolean.TRUE);
        case 'f':
          return literal("false", Boolean.FALSE);
        case 'n':
          return literal("null", null);
        default:
          if (c == '-' || (c >= '0' && c <= '9')) {
            return number();
          }
          throw error("Unexpected character '" + c + "'");
      }
    }

    Object literal(String word, Object result) {
      if (!s.startsWith(word, pos)) {
        throw error("Invalid literal");
      }
      pos += word.length();
      return result;
    }

    Object number() {
      int start = pos;
      while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
        pos++;
      }
      String num = s.substring(start, pos);
      try {
        if (num.indexOf('.') >= 0 || num.indexOf('e') >= 0 || num.indexOf('E') >= 0) {
          return Double.parseDouble(num);
        }
        return Long.parseLong(num);
      } catch (NumberFormatException e) {
        pos = start;
        throw error("Invalid number");
      }
    }

    String string() {
      pos++; // opening quote
      StringBuilder sb = new StringBuilder();
      while (true) {
        if (pos >= s.length()) {
          throw error("Unterminated string");
        }
        char c = s.charAt(pos++);
        if (c == '"') {
          return sb.toString();
        }
        if (c != '\\') {
          sb.append(c);
          continue;
        }
        if (pos >= s.length()) {
          throw error("Unterminated escape");
        }
        char e = s.charAt(pos++);
        switch (e) {
          case '"' -> sb.append('"');
          case '\\' -> sb.append('\\');
          case '/' -> sb.append('/');
          case 'b' -> sb.append('\b');
          case 'f' -> sb.append('\f');
          case 'n' -> sb.append('\n');
          case 'r' -> sb.append('\r');
          case 't' -> sb.append('\t');
          case 'u' -> {
            if (pos + 4 > s.length()) {
              throw error("Bad unicode escape");
            }
            try {
              sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
            } catch (NumberFormatException ex) {
              throw error("Bad unicode escape");
            }
            pos += 4;
          }
          default -> throw error("Bad escape '\\" + e + "'");
        }
      }
    }

    Object array(int depth) {
      pos++;
      List<Object> list = new ArrayList<>();
      skipWs();
      if (pos < s.length() && s.charAt(pos) == ']') {
        pos++;
        return list;
      }
      while (true) {
        skipWs();
        list.add(value(depth + 1));
        skipWs();
        if (pos >= s.length()) {
          throw error("Unterminated array");
        }
        char c = s.charAt(pos++);
        if (c == ']') {
          return list;
        }
        if (c != ',') {
          throw error("Expected ',' or ']'");
        }
      }
    }

    Object object(int depth) {
      pos++;
      Map<String, Object> map = new LinkedHashMap<>();
      skipWs();
      if (pos < s.length() && s.charAt(pos) == '}') {
        pos++;
        return map;
      }
      while (true) {
        skipWs();
        if (pos >= s.length() || s.charAt(pos) != '"') {
          throw error("Expected object key");
        }
        String key = string();
        skipWs();
        if (pos >= s.length() || s.charAt(pos) != ':') {
          throw error("Expected ':'");
        }
        pos++;
        skipWs();
        map.put(key, value(depth + 1));
        skipWs();
        if (pos >= s.length()) {
          throw error("Unterminated object");
        }
        char c = s.charAt(pos++);
        if (c == '}') {
          return map;
        }
        if (c != ',') {
          throw error("Expected ',' or '}'");
        }
      }
    }
  }
}
