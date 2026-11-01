package io.github.ratchet4j.readers;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Environment readers need to turn analyzer paths into repository-relative paths.
 *
 * @param repoRoot repository root directory
 * @param sourceDirs source directories relative to the root (for analyzers such as SpotBugs that
 *     only report paths relative to a source root)
 */
public record ReaderContext(Path repoRoot, List<String> sourceDirs) {

  private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:/.*");

  /** Defaults: current directory, {@code src/main/java} and {@code src/test/java}. */
  public static ReaderContext defaults(Path repoRoot) {
    return new ReaderContext(repoRoot, List.of("src/main/java", "src/test/java"));
  }

  /**
   * Converts an analyzer-reported path (absolute, relative, {@code file:} URI, Windows or Unix
   * separators) to a repository-relative path with forward slashes.
   */
  public String normalizePath(String raw) {
    String s = raw.trim();
    if (s.startsWith("file:")) {
      try {
        s = Path.of(URI.create(s)).toString();
      } catch (RuntimeException e) {
        s = s.replaceFirst("^file:/*", "/");
      }
    }
    s = s.replace('\\', '/');
    String root = repoRoot.toAbsolutePath().normalize().toString().replace('\\', '/');
    if (!root.endsWith("/")) {
      root = root + "/";
    }
    if (s.startsWith(root)) {
      s = s.substring(root.length());
    }
    return collapseDots(s);
  }

  /**
   * Resolves a path relative to some source root (for example {@code com/acme/Foo.java}) against
   * the configured source directories; falls back to the path as given.
   */
  public String resolveSourcePath(String sourcePath) {
    String sp = sourcePath.replace('\\', '/');
    for (String dir : sourceDirs) {
      String candidate = (dir.endsWith("/") ? dir : dir + "/") + sp;
      if (Files.isRegularFile(repoRoot.resolve(candidate))) {
        return collapseDots(candidate);
      }
    }
    return collapseDots(sp);
  }

  private static String collapseDots(String path) {
    boolean absolute = path.startsWith("/") || WINDOWS_ABSOLUTE.matcher(path).matches();
    List<String> out = new ArrayList<>();
    for (String part : path.split("/")) {
      if (part.isEmpty() || part.equals(".")) {
        continue;
      }
      if (part.equals("..") && !out.isEmpty() && !out.get(out.size() - 1).equals("..")) {
        out.remove(out.size() - 1);
      } else {
        out.add(part);
      }
    }
    String joined = String.join("/", out);
    return absolute && path.startsWith("/") ? "/" + joined : joined;
  }
}
