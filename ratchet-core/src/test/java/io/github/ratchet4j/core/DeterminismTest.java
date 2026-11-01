package io.github.ratchet4j.core;

import static io.github.ratchet4j.core.TestSupport.SERVICE;
import static io.github.ratchet4j.core.TestSupport.analyze;
import static io.github.ratchet4j.core.TestSupport.engine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.ratchet4j.core.baseline.BaselineIO;
import io.github.ratchet4j.core.baseline.Ratchet;
import io.github.ratchet4j.core.baseline.UpdateResult;
import io.github.ratchet4j.core.match.MatchResult;
import io.github.ratchet4j.core.model.Baseline;
import io.github.ratchet4j.core.model.Violation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Same input must give byte-identical output, whatever the input order. */
class DeterminismTest {

  private static final Map<String, String> FILES =
      Map.of("src/Service.java", SERVICE, "src/Other.java", "class Other { void x() {\n bad();\n } }");

  @Test
  void baselineBytesAreIdenticalAcrossRunsAndInputOrder() {
    List<Violation> violations = analyze(FILES);
    String first = BaselineIO.toJson(engine(FILES, 2).createBaseline(violations));

    List<Violation> shuffled = new ArrayList<>(violations);
    Collections.reverse(shuffled);
    String second = BaselineIO.toJson(engine(FILES, 2).createBaseline(shuffled));

    assertEquals(first, second);
    assertTrue(first.endsWith("}\n"));
    assertTrue(!first.contains("\r"), "LF only");
  }

  @Test
  void ratchetUpdateNeverGrowsTheBaselineUnlessAllowed() {
    Baseline base = engine(FILES, 2).createBaseline(analyze(FILES));
    Map<String, String> worse = Map.of(
        "src/Service.java", SERVICE + "\nclass Extra { void y() {\n bad();\n bad();\n } }",
        "src/Other.java", FILES.get("src/Other.java"));
    MatchResult r = engine(worse, 2).compare(base, analyze(worse));
    assertTrue(r.newViolations().size() >= 1);

    UpdateResult shrinkOnly = Ratchet.update(r, false);
    assertTrue(shrinkOnly.baseline().size() <= base.size());
    assertEquals(0, shrinkOnly.added());
    assertEquals(r.newViolations().size(), shrinkOnly.refused());

    UpdateResult grow = Ratchet.update(r, true);
    assertEquals(base.size() + r.newViolations().size(), grow.baseline().size());
    assertEquals(r.newViolations().size(), grow.added());
  }

  @Test
  void updateAfterFixingRemovesOnlyTheFixedEntries() {
    Baseline base = engine(FILES, 2).createBaseline(analyze(FILES));
    Map<String, String> fixed = Map.of("src/Service.java", SERVICE, "src/Other.java", "class Other { }");
    MatchResult r = engine(fixed, 2).compare(base, analyze(fixed));
    UpdateResult u = Ratchet.update(r, false);
    assertEquals(base.size() - 1, u.baseline().size());
    assertEquals(1, u.fixed());
    assertEquals(Map.of("fake:BadCall", 1), u.fixedByRule());

    // a second compare against the updated baseline is clean
    MatchResult again = engine(fixed, 2).compare(u.baseline(), analyze(fixed));
    assertEquals(0, again.newViolations().size());
    assertEquals(0, again.fixed().size());
  }
}
