package dev.ratchet4j.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parsed command line options shared by all commands. */
final class Options {

  final List<Path> inputs = new ArrayList<>();
  final List<String> sourceDirs = new ArrayList<>();
  final List<String> strict = new ArrayList<>();
  Path baseline = Path.of("ratchet-baseline.json");
  Path root = Path.of(".");
  int context = 2;
  boolean allowIncrease;
  boolean force;

  /** Parses options; throws {@link IllegalArgumentException} with a user-facing message. */
  static Options parse(List<String> args) {
    Options o = new Options();
    for (int i = 0; i < args.size(); i++) {
      String a = args.get(i);
      switch (a) {
        case "--input", "-i" -> {
          for (String part : value(args, ++i, a).split(",")) {
            if (!part.isBlank()) {
              o.inputs.add(Path.of(part.trim()));
            }
          }
        }
        case "--baseline", "-b" -> o.baseline = Path.of(value(args, ++i, a));
        case "--root" -> o.root = Path.of(value(args, ++i, a));
        case "--src" -> o.sourceDirs.add(value(args, ++i, a));
        case "--strict" -> o.strict.add(value(args, ++i, a));
        case "--context" -> {
          try {
            o.context = Integer.parseInt(value(args, ++i, a));
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--context needs a number");
          }
        }
        case "--allow-increase" -> o.allowIncrease = true;
        case "--force" -> o.force = true;
        default -> throw new IllegalArgumentException("Unknown option: " + a);
      }
    }
    if (o.sourceDirs.isEmpty()) {
      o.sourceDirs.add("src/main/java");
      o.sourceDirs.add("src/test/java");
    }
    return o;
  }

  private static String value(List<String> args, int index, String option) {
    if (index >= args.size()) {
      throw new IllegalArgumentException(option + " needs a value");
    }
    return args.get(index);
  }
}
