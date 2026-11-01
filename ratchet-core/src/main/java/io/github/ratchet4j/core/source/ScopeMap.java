package io.github.ratchet4j.core.source;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cheap, deterministic heuristic that assigns each source line the enclosing {@code Class#method}.
 *
 * <p>It tracks brace depth over comment-stripped lines. It is not a Java parser and can be wrong on
 * unusual code, which is acceptable: the value is only used as a secondary matching signal and is
 * always computed the same way for the same input.
 */
public final class ScopeMap {

  private static final Pattern CLASS =
      Pattern.compile(
          "^(?:@\\w+(?:\\([^)]*\\))?\\s+)*"
              + "(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|strictfp)\\s+)*"
              + "(?:@?interface|class|enum|record)\\s+(\\w+)");

  private static final Pattern METHOD =
      Pattern.compile(
          "^(?:@\\w+(?:\\([^)]*\\))?\\s+)*"
              + "(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|strictfp)\\s+)*"
              + "(?:<[^>]+>\\s+)?(?:[\\w.\\[\\]<>?,]+\\s+)?(\\w+)\\s*\\([^;]*$");

  private static final Set<String> NOT_A_METHOD =
      Set.of(
          "if", "for", "while", "switch", "catch", "try", "do", "else", "return", "new", "throw",
          "assert", "case", "yield", "super", "this", "synchronized");

  private ScopeMap() {}

  private record Frame(char kind, String name, int depth) {}

  /** Computes the scope string for every line. Entries are empty when no scope is known. */
  public static String[] compute(String[] normalizedLines) {
    String[] result = new String[normalizedLines.length];
    Deque<Frame> stack = new ArrayDeque<>();
    int depth = 0;
    Frame pending = null;
    for (int i = 0; i < normalizedLines.length; i++) {
      String s = normalizedLines[i];
      if (pending == null) {
        pending = declaration(s);
      }
      boolean inString = false;
      boolean inChar = false;
      for (int k = 0; k < s.length(); k++) {
        char c = s.charAt(k);
        if (inString || inChar) {
          if (c == '\\') {
            k++;
          } else if (inString && c == '"') {
            inString = false;
          } else if (inChar && c == '\'') {
            inChar = false;
          }
          continue;
        }
        if (c == '"') {
          inString = true;
        } else if (c == '\'') {
          inChar = true;
        } else if (c == '{') {
          if (pending != null) {
            stack.push(new Frame(pending.kind, pending.name, depth));
            pending = null;
          }
          depth++;
        } else if (c == '}') {
          depth--;
          if (!stack.isEmpty() && stack.peek().depth == depth) {
            stack.pop();
          }
        } else if (c == ';') {
          pending = null;
        }
      }
      result[i] = describe(stack, pending);
    }
    return result;
  }

  private static Frame declaration(String s) {
    if (s.isEmpty()) {
      return null;
    }
    Matcher cm = CLASS.matcher(s);
    if (cm.find()) {
      return new Frame('C', cm.group(1), 0);
    }
    String firstWord = s.split("[\\s(]", 2)[0];
    if (NOT_A_METHOD.contains(firstWord)) {
      return null;
    }
    Matcher mm = METHOD.matcher(s);
    if (mm.find() && !NOT_A_METHOD.contains(mm.group(1))) {
      return new Frame('M', mm.group(1), 0);
    }
    return null;
  }

  private static String describe(Deque<Frame> stack, Frame pending) {
    String cls = "";
    String method = "";
    if (pending != null) {
      if (pending.kind == 'C') {
        cls = pending.name;
      } else {
        method = pending.name;
      }
    }
    for (Frame f : stack) {
      if (f.kind == 'C' && cls.isEmpty()) {
        cls = f.name;
      } else if (f.kind == 'M' && method.isEmpty()) {
        method = f.name;
      }
    }
    return method.isEmpty() ? cls : cls + "#" + method;
  }
}
