package io.github.ratchet4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ratchet4j.core.baseline.BaselineIO;
import io.github.ratchet4j.core.model.Baseline;
import io.github.ratchet4j.core.model.BaselineEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaselineIOTest {

  private static BaselineEntry entry(String file, String rule, String strong, String msg) {
    return new BaselineEntry(rule, file, strong, "w" + strong, "m" + strong, "Cls#m", msg);
  }

  @Test
  void roundTripPreservesEverythingIncludingSpecialCharacters() {
    Baseline b =
        new Baseline(
            List.of(
                entry("a/B.java", "pmd:X", "1", "quote \" backslash \\ tab \t newline \n unicode \u00e9\u4e2d"),
                entry("a/A.java", "checkstyle:Y", "2", "plain")));
    Baseline back = BaselineIO.fromJson(BaselineIO.toJson(b));
    assertEquals(b, back);
  }

  @Test
  void entriesAreSortedAndOutputIsStable() {
    Baseline b = new Baseline(List.of(entry("z.java", "r", "2", "m"), entry("a.java", "r", "1", "m")));
    String json = BaselineIO.toJson(b);
    assertTrue(json.indexOf("a.java") < json.indexOf("z.java"));
    assertEquals(json, BaselineIO.toJson(BaselineIO.fromJson(json)));
    assertEquals("a.java", b.entries().get(0).file());
  }

  @Test
  void emptyBaselineIsValidJson() {
    String json = BaselineIO.toJson(Baseline.empty());
    assertEquals(Baseline.empty(), BaselineIO.fromJson(json));
  }

  @Test
  void countsByRuleAreWrittenAndSorted() {
    Baseline b = new Baseline(List.of(entry("a", "z:R", "1", ""), entry("a", "a:R", "2", ""), entry("b", "a:R", "3", "")));
    assertEquals(List.of("a:R", "z:R"), List.copyOf(b.countsByRule().keySet()));
    assertEquals(2, b.countsByRule().get("a:R"));
    assertTrue(BaselineIO.toJson(b).contains("\"a:R\": 2"));
  }

  @Test
  void rejectsFilesThatAreNotBaselines() {
    assertThrows(IllegalArgumentException.class, () -> BaselineIO.fromJson("{\"hello\": 1}"));
    assertThrows(IllegalArgumentException.class, () -> BaselineIO.fromJson("{\"schema\": \"ratchet4j-baseline/99\", \"entries\": []}"));
    assertThrows(IllegalArgumentException.class, () -> BaselineIO.fromJson("not json"));
  }

  @Test
  void writeAndReadFileCreatesParentDirectories() throws IOException {
    Path dir = Files.createTempDirectory("ratchet");
    Path file = dir.resolve("nested/dir/baseline.json");
    Baseline b = new Baseline(List.of(entry("a.java", "r", "1", "m")));
    BaselineIO.write(file, b);
    assertEquals(b, BaselineIO.read(file));
    assertThrows(IOException.class, () -> BaselineIO.read(dir.resolve("missing.json")));
  }
}
