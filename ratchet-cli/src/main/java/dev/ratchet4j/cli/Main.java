package dev.ratchet4j.cli;

import dev.ratchet4j.core.Engine;
import dev.ratchet4j.core.baseline.BaselineIO;
import dev.ratchet4j.core.baseline.Ratchet;
import dev.ratchet4j.core.baseline.UpdateResult;
import dev.ratchet4j.core.match.Match;
import dev.ratchet4j.core.match.MatchResult;
import dev.ratchet4j.core.match.Policy;
import dev.ratchet4j.core.model.Baseline;
import dev.ratchet4j.core.model.FingerprintedViolation;
import dev.ratchet4j.core.model.Violation;
import dev.ratchet4j.core.source.FileSystemSourceProvider;
import dev.ratchet4j.readers.ReaderContext;
import dev.ratchet4j.readers.Readers;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Command line interface.
 *
 * <p>Exit codes: 0 success, 1 new violations found (or baseline refused growth), 2 usage error, 3
 * internal error.
 */
public final class Main {

  static final int OK = 0;
  static final int NEW_VIOLATIONS = 1;
  static final int USAGE = 2;
  static final int INTERNAL = 3;

  private static final String USAGE_TEXT =
      """
      ratchet4j - fail the build only on NEW static analysis violations

      Usage: ratchet <command> [options]

      Commands:
        baseline   Record all current violations as the accepted baseline
        check      Fail (exit 1) if there are violations that are not in the baseline
        update     Ratchet down: drop fixed violations from the baseline (never grows it)
        report     Show per-rule progress against the baseline

      Options:
        -i, --input <file>[,<file>...]  analyzer reports (Checkstyle/PMD/SpotBugs XML, SARIF); repeatable
        -b, --baseline <file>           baseline file (default: ratchet-baseline.json)
            --root <dir>                repository root (default: .)
            --src <dir>                 source dir relative to root, for SpotBugs paths (repeatable;
                                        default: src/main/java, src/test/java)
            --context <n>               context lines above/below a violation (default: 2)
            --strict <glob>             rule glob that fails on every occurrence, e.g. 'spotbugs:SQL*'
            --allow-increase            update: also add new violations to the baseline
            --force                     baseline: overwrite an existing baseline file
      """;

  private Main() {}

  /** Program entry point. */
  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  /** Runs the CLI and returns the exit code (testable, no System.exit). */
  public static int run(String[] args, PrintStream out, PrintStream err) {
    if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
      out.print(USAGE_TEXT);
      return args.length == 0 ? USAGE : OK;
    }
    String command = args[0];
    Options opts;
    try {
      opts = Options.parse(List.of(args).subList(1, args.length));
    } catch (IllegalArgumentException e) {
      err.println("error: " + e.getMessage());
      err.println("Run 'ratchet --help' for usage.");
      return USAGE;
    }
    try {
      return switch (command) {
        case "baseline" -> baseline(opts, out, err);
        case "check" -> check(opts, out, err);
        case "update" -> update(opts, out, err);
        case "report" -> report(opts, out, err);
        default -> {
          err.println("error: unknown command '" + command + "'");
          err.println("Run 'ratchet --help' for usage.");
          yield USAGE;
        }
      };
    } catch (UsageException e) {
      err.println("error: " + e.getMessage());
      return USAGE;
    } catch (IOException | RuntimeException e) {
      err.println("internal error: " + e.getMessage());
      return INTERNAL;
    }
  }

  /** A problem the user can fix by changing the command line or files. */
  private static final class UsageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    UsageException(String message) {
      super(message);
    }
  }

  private static List<Violation> readInputs(Options o) throws IOException {
    if (o.inputs.isEmpty()) {
      throw new UsageException("no analyzer reports given; use --input <file>");
    }
    for (var p : o.inputs) {
      if (!Files.isRegularFile(p)) {
        throw new UsageException("report not found: " + p);
      }
    }
    return Readers.readAll(o.inputs, new ReaderContext(o.root, o.sourceDirs));
  }

  private static Engine engine(Options o) {
    return new Engine(new FileSystemSourceProvider(o.root), o.context);
  }

  private static Baseline loadBaseline(Options o) throws IOException {
    if (!Files.isRegularFile(o.baseline)) {
      throw new UsageException(
          "baseline not found: " + o.baseline + " (create it with 'ratchet baseline')");
    }
    return BaselineIO.read(o.baseline);
  }

  private static int baseline(Options o, PrintStream out, PrintStream err) throws IOException {
    if (Files.exists(o.baseline) && !o.force) {
      throw new UsageException(
          o.baseline
              + " already exists. Creating a new baseline could grow it; use 'ratchet update' "
              + "to shrink it, or pass --force to overwrite.");
    }
    List<Violation> violations = readInputs(o);
    Baseline b = engine(o).createBaseline(violations);
    BaselineIO.write(o.baseline, b);
    out.printf(
        "Baseline written to %s: %d violations across %d rules%n",
        o.baseline, b.size(), b.countsByRule().size());
    return OK;
  }

  private static int check(Options o, PrintStream out, PrintStream err) throws IOException {
    Baseline b = loadBaseline(o);
    MatchResult result = engine(o).compare(b, readInputs(o));
    Policy policy = new Policy(o.strict);
    List<FingerprintedViolation> failing = policy.failing(result);

    int strictCount = 0;
    for (FingerprintedViolation fv : failing) {
      boolean isNew = result.newViolations().contains(fv);
      if (!isNew) {
        strictCount++;
      }
      Violation v = fv.violation();
      out.printf(
          "%s:%d:%d: [%s] %s (%s)%n",
          v.file(), v.line(), v.column(), v.rule(), v.message(), isNew ? "NEW" : "STRICT");
    }
    out.printf(
        "ratchet4j: %d new, %d strict, %d baselined (tolerated), %d fixed since baseline%n",
        result.newViolations().size(), strictCount, result.unchangedCount(), result.fixed().size());
    if (!result.fixed().isEmpty()) {
      out.println("Tip: run 'ratchet update' to lock in the progress (baseline shrinks).");
    }
    return failing.isEmpty() ? OK : NEW_VIOLATIONS;
  }

  private static int update(Options o, PrintStream out, PrintStream err) throws IOException {
    Baseline b = loadBaseline(o);
    MatchResult result = engine(o).compare(b, readInputs(o));
    UpdateResult u = Ratchet.update(result, o.allowIncrease);
    BaselineIO.write(o.baseline, u.baseline());
    out.printf(
        "Baseline updated: %d -> %d violations (%d fixed, %d added)%n",
        b.size(), u.baseline().size(), u.fixed(), u.added());
    for (Map.Entry<String, Integer> e : u.fixedByRule().entrySet()) {
      out.printf("  fixed %4d  %s%n", e.getValue(), e.getKey());
    }
    if (u.refused() > 0) {
      err.printf(
          "warning: %d new violations were NOT added (baseline can only shrink). "
              + "Fix them or pass --allow-increase.%n",
          u.refused());
      return NEW_VIOLATIONS;
    }
    return OK;
  }

  private static int report(Options o, PrintStream out, PrintStream err) throws IOException {
    Baseline b = loadBaseline(o);
    MatchResult result = engine(o).compare(b, readInputs(o));

    Map<String, Integer> baselineCounts = b.countsByRule();
    Map<String, Integer> nowCounts = new TreeMap<>();
    Map<String, Integer> newCounts = new TreeMap<>();
    for (Match m : result.matches()) {
      nowCounts.merge(m.current().violation().rule(), 1, Integer::sum);
    }
    for (FingerprintedViolation fv : result.newViolations()) {
      nowCounts.merge(fv.violation().rule(), 1, Integer::sum);
      newCounts.merge(fv.violation().rule(), 1, Integer::sum);
    }
    var rules = new TreeSet<String>(baselineCounts.keySet());
    rules.addAll(nowCounts.keySet());

    out.printf("%-48s %9s %9s %9s%n", "RULE", "BASELINE", "NOW", "NEW");
    int totalBase = 0;
    int totalNow = 0;
    int totalNew = 0;
    List<String> lines = new ArrayList<>();
    for (String rule : rules) {
      int base = baselineCounts.getOrDefault(rule, 0);
      int now = nowCounts.getOrDefault(rule, 0);
      int nw = newCounts.getOrDefault(rule, 0);
      totalBase += base;
      totalNow += now;
      totalNew += nw;
      lines.add(String.format("%-48s %9d %9d %9d", rule, base, now, nw));
    }
    lines.forEach(out::println);
    out.printf("%-48s %9d %9d %9d%n", "TOTAL", totalBase, totalNow, totalNew);
    out.printf(
        "%d violations fixed since the baseline (%d remaining legacy)%n",
        result.fixed().size(), result.unchangedCount());
    return OK;
  }
}
