package dev.ratchet4j.core.source;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads source files below a repository root. Paths escaping the root are refused. */
public final class FileSystemSourceProvider implements SourceProvider {

  private final Path root;
  private final Map<String, List<String>> cache = new HashMap<>();

  /** Creates a provider rooted at the given repository directory. */
  public FileSystemSourceProvider(Path root) {
    this.root = root.toAbsolutePath().normalize();
  }

  @Override
  public List<String> lines(String file) {
    return cache.computeIfAbsent(file, this::load);
  }

  private List<String> load(String file) {
    Path p = root.resolve(file).normalize();
    if (!p.startsWith(root) || !Files.isRegularFile(p)) {
      return List.of();
    }
    try {
      return Files.readAllLines(p, StandardCharsets.UTF_8);
    } catch (CharacterCodingException e) {
      try {
        return Files.readAllLines(p, StandardCharsets.ISO_8859_1);
      } catch (IOException e2) {
        return List.of();
      }
    } catch (IOException e) {
      return List.of();
    }
  }
}
