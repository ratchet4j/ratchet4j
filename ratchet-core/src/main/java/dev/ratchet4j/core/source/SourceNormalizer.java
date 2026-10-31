package dev.ratchet4j.core.source;

/**
 * Normalizes source lines and messages so that harmless edits (whitespace, trailing comments,
 * numbers inside messages) do not change a violation's identity.
 */
public final class SourceNormalizer {

  private SourceNormalizer() {}

  /**
   * Strips comments that are not inside string/char literals, collapses all whitespace runs to a
   * single space and trims. Returns an empty string for blank and comment-only lines.
   */
  public static String normalizeLine(String line) {
    int n = line.length();
    StringBuilder out = new StringBuilder(n);
    boolean inString = false;
    boolean inChar = false;
    int i = 0;
    while (i < n) {
      char c = line.charAt(i);
      if (inString || inChar) {
        out.append(c);
        if (c == '\\' && i + 1 < n) {
          out.append(line.charAt(i + 1));
          i += 2;
          continue;
        }
        if (inString && c == '"') {
          inString = false;
        } else if (inChar && c == '\'') {
          inChar = false;
        }
        i++;
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == '\'') {
        inChar = true;
      } else if (c == '/' && i + 1 < n) {
        char d = line.charAt(i + 1);
        if (d == '/') {
          break;
        }
        if (d == '*') {
          int end = line.indexOf("*/", i + 2);
          if (end < 0) {
            break;
          }
          out.append(' ');
          i = end + 2;
          continue;
        }
      }
      out.append(c);
      i++;
    }
    return collapse(out.toString());
  }

  /** Collapses whitespace runs to one space and trims. */
  public static String collapse(String s) {
    return s.strip().replaceAll("\\s+", " ");
  }

  /**
   * True for lines that carry no code: blank, comment-only, or javadoc/block-comment continuation
   * lines. Such lines are skipped when collecting context.
   */
  public static boolean isNoise(String raw) {
    return isNoise(raw, normalizeLine(raw));
  }

  /** Same as {@link #isNoise(String)} when the normalized form is already known. */
  public static boolean isNoise(String raw, String normalized) {
    return normalized.isEmpty() || raw.strip().startsWith("*");
  }

  /** Replaces digit runs by {@code #} so messages mentioning line numbers or counts stay stable. */
  public static String normalizeMessage(String message) {
    return collapse(message).replaceAll("\\d+", "#");
  }
}
