package dev.ratchet4j.core.match;

import dev.ratchet4j.core.model.FingerprintedViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides which violations fail the build.
 *
 * <p>By default only new violations fail. Rules matching a {@code strictRules} glob (for example
 * {@code spotbugs:SQL_INJECTION*}) fail on every occurrence, baselined or not.
 *
 * @param strictRules glob patterns ({@code *} wildcard) over {@code tool:ruleId}
 */
public record Policy(List<String> strictRules) {

  /** Defensive copy. */
  public Policy {
    strictRules = strictRules == null ? List.of() : List.copyOf(strictRules);
  }

  /** The default policy: only new violations fail. */
  public static Policy none() {
    return new Policy(List.of());
  }

  /** The violations that make a check fail, sorted by file, line, column. */
  public List<FingerprintedViolation> failing(MatchResult result) {
    List<Pattern> patterns = new ArrayList<>();
    for (String glob : strictRules) {
      patterns.add(toPattern(glob));
    }
    List<FingerprintedViolation> failing = new ArrayList<>(result.newViolations());
    if (!patterns.isEmpty()) {
      for (Match m : result.matches()) {
        String rule = m.current().violation().rule();
        if (patterns.stream().anyMatch(p -> p.matcher(rule).matches())) {
          failing.add(m.current());
        }
      }
    }
    failing.sort(FingerprintedViolation.ORDER);
    return failing;
  }

  /** True if the rule is covered by a strict pattern. */
  public boolean isStrict(String rule) {
    return strictRules.stream().anyMatch(g -> toPattern(g).matcher(rule).matches());
  }

  private static Pattern toPattern(String glob) {
    String[] parts = glob.split("\\*", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        sb.append(".*");
      }
      sb.append(Pattern.quote(parts[i]));
    }
    return Pattern.compile(sb.toString());
  }
}
