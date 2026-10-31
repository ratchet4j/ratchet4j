package dev.ratchet4j.core;

import static dev.ratchet4j.core.TestSupport.SERVICE;
import static dev.ratchet4j.core.TestSupport.analyze;
import static dev.ratchet4j.core.TestSupport.engine;
import static dev.ratchet4j.core.TestSupport.src;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ratchet4j.core.match.MatchResult;
import dev.ratchet4j.core.model.Baseline;
import dev.ratchet4j.core.model.Severity;
import dev.ratchet4j.core.model.Violation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The "mutation corpus": realistic edits that must NOT turn old violations into new ones, and real
 * regressions that MUST be reported. This is the most important test class of the project.
 */
class RefactoringScenariosTest {

  private static final String FILE = "src/Service.java";

  private static MatchResult compare(String before, String after) {
    return compare(Map.of(FILE, before), Map.of(FILE, after), 2);
  }

  private static MatchResult compare(
      Map<String, String> before, Map<String, String> after, int context) {
    Baseline base = engine(before, context).createBaseline(analyze(before));
    return engine(after, context).compare(base, analyze(after));
  }

  private static void assertNoNew(MatchResult r) {
    assertEquals(0, r.newViolations().size(), "unexpected NEW violations: " + r.newViolations());
  }

  @Test
  void unchangedCodeProducesNothingNew() {
    MatchResult r = compare(SERVICE, SERVICE);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
    assertEquals(0, r.fixed().size());
    assertTrue(r.matches().stream().allMatch(m -> m.tier() == 1), "all exact (tier 1) matches");
  }

  @Test
  void insertingLinesAboveShiftsLineNumbersButNothingIsNew() {
    String after = "// header\n// header 2\n\nimport java.util.Map;\n" + SERVICE;
    MatchResult r = compare(SERVICE, after);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
    assertTrue(r.matches().stream().allMatch(m -> m.tier() == 1));
  }

  @Test
  void reformattingWhitespaceIsIgnored() {
    String after = SERVICE.replace("    ", "\t").replace("int a = 1;", "int   a  =  1 ;");
    MatchResult r = compare(SERVICE, after);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
  }

  @Test
  void addingOrEditingCommentsIsIgnored() {
    String after =
        SERVICE
            .replace("        bad();\n        int b", "        bad(); // TODO remove later\n        int b")
            .replace("    public void second()", "    /** Now documented. */\n    public void second()");
    MatchResult r = compare(SERVICE, after);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
  }

  @Test
  void editingANeighbouringLineStillMatchesViaWeakFingerprint() {
    String after = SERVICE.replace("int a = 1;", "int a = 42;");
    MatchResult r = compare(SERVICE, after);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
    assertTrue(r.matches().stream().anyMatch(m -> m.tier() == 2), "one match needed tier 2");
  }

  @Test
  void editingTheViolatingLineItselfStillMatchesViaScope() {
    String after = SERVICE.replace("        bad();\n        int b", "        bad(7);\n        int b");
    MatchResult r = compare(SERVICE, after);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
    assertTrue(r.matches().stream().anyMatch(m -> m.tier() == 3), "needed tier 3");
  }

  @Test
  void movingAMethodWithinTheFileIsNotNew() {
    String after =
        src(
            "package demo;",
            "",
            "import java.util.List;",
            "",
            "public class Service {",
            "    public void second() {",
            "        int c = 3;",
            "        bad();",
            "        int d = 4;",
            "    }",
            "",
            "    public void first() {",
            "        int a = 1;",
            "        bad();",
            "        int b = 2;",
            "    }",
            "}");
    MatchResult r = compare(SERVICE, after);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
  }

  @Test
  void renamingOrMovingTheFileIsNotNew() {
    MatchResult r =
        compare(
            Map.of("src/old/Service.java", SERVICE),
            Map.of("src/renamed/Service.java", SERVICE),
            2);
    assertNoNew(r);
    assertEquals(2, r.unchangedCount());
    assertTrue(r.matches().stream().allMatch(m -> m.tier() == 4), "file move is tier 4");
  }

  @Test
  void aGenuinelyNewViolationIsReported() {
    String after = SERVICE.replace("int d = 4;", "int d = 4;\n        bad();\n        int e = 5;");
    MatchResult r = compare(SERVICE, after);
    assertEquals(1, r.newViolations().size());
    assertEquals(2, r.unchangedCount());
    assertEquals("src/Service.java", r.newViolations().get(0).violation().file());
  }

  @Test
  void aNewViolationInAnotherFileIsReported() {
    Map<String, String> after = Map.of(FILE, SERVICE, "src/Other.java", "class Other { void x() { bad(); } }");
    MatchResult r = compare(Map.of(FILE, SERVICE), after, 2);
    assertEquals(1, r.newViolations().size());
    assertEquals("src/Other.java", r.newViolations().get(0).violation().file());
  }

  @Test
  void fixingAViolationIsReportedAsFixedNotNew() {
    String after = SERVICE.replace("        bad();\n        int b", "        int b");
    MatchResult r = compare(SERVICE, after);
    assertNoNew(r);
    assertEquals(1, r.unchangedCount());
    assertEquals(1, r.fixed().size());
  }

  @Test
  void deletingTheWholeFileMarksEverythingFixed() {
    Baseline base = engine(Map.of(FILE, SERVICE), 2).createBaseline(analyze(Map.of(FILE, SERVICE)));
    MatchResult r = engine(Map.of(), 2).compare(base, List.of());
    assertNoNew(r);
    assertEquals(2, r.fixed().size());
  }

  @Test
  void identicalDuplicateViolationsAreCountedExactly() {
    String two = src("class D {", "  void a() {", "    bad();", "    bad();", "  }", "}");
    String three = src("class D {", "  void a() {", "    bad();", "    bad();", "    bad();", "  }", "}");
    // context 0 makes the three calls indistinguishable except by occurrence index
    MatchResult r = compare(Map.of(FILE, two), Map.of(FILE, three), 0);
    assertEquals(1, r.newViolations().size(), "exactly one of three is new");
    assertEquals(2, r.unchangedCount());
  }

  @Test
  void aBaselineEntryNeverMatchesTwice() {
    String one = src("class D {", "  void a() {", "    bad();", "  }", "}");
    String two = src("class D {", "  void a() {", "    bad();", "  }", "  void b() {", "    bad();", "  }", "}");
    MatchResult r = compare(Map.of(FILE, one), Map.of(FILE, two), 2);
    assertEquals(1, r.newViolations().size());
    assertEquals(1, r.unchangedCount());
  }

  @Test
  void oneBaselineEntryCannotHideSeveralNewViolationsSharingAMatchKey() {
    // Baseline has ONE violation in method a(). Afterwards both calls were edited, so tiers 1-2
    // fail and both share the same tier-3 key (rule + file + scope + message).
    String before = src("class D {", "  void a() {", "    bad(1);", "  }", "}");
    String after = src("class D {", "  void a() {", "    bad(2);", "    bad(3);", "  }", "}");
    MatchResult r = compare(Map.of(FILE, before), Map.of(FILE, after), 2);
    assertEquals(1, r.newViolations().size(), "only one of the two can be the old violation");
    assertEquals(1, r.unchangedCount());
  }

  @Test
  void oneBaselineEntryCannotBeClaimedByTwoMovedCopies() {
    String before = src("class D {", "  void a() {", "    bad();", "  }", "}");
    Map<String, String> after =
        Map.of("src/x/D.java", before, "src/y/D.java", before);
    MatchResult r = compare(Map.of("src/D.java", before), after, 2);
    assertEquals(1, r.newViolations().size(), "two copies cannot both be the one old violation");
    assertEquals(1, r.unchangedCount());
  }

  @Test
  void removingOneOfTwoIdenticalDuplicatesIsOneFixed() {
    String two = src("class D {", "  void a() {", "    bad();", "    bad();", "  }", "}");
    String one = src("class D {", "  void a() {", "    bad();", "  }", "}");
    MatchResult r = compare(Map.of(FILE, two), Map.of(FILE, one), 0);
    assertNoNew(r);
    assertEquals(1, r.fixed().size());
    assertEquals(1, r.unchangedCount());
  }

  @Test
  void findingsWithoutLineAreMatchedByMessageAndScopeHint() {
    Map<String, String> files = Map.of();
    Violation v =
        new Violation(
            "spotbugs", "URF_UNREAD_FIELD", Severity.WARNING, "Unread field: Foo.x",
            "src/Foo.java", 0, 0, Map.of("scope", "Foo"));
    Baseline base = engine(files, 2).createBaseline(List.of(v, v));
    MatchResult same = engine(files, 2).compare(base, List.of(v, v));
    assertNoNew(same);
    assertEquals(2, same.unchangedCount());
    MatchResult more = engine(files, 2).compare(base, List.of(v, v, v));
    assertEquals(1, more.newViolations().size());
  }

  @Test
  void missingSourceFileDoesNotCrashAndFallsBackToMessage() {
    Violation v =
        new Violation("pmd", "UnusedPrivateField", Severity.WARNING, "Avoid unused private fields such as 'x'.",
            "src/Gone.java", 12, 5, Map.of());
    Baseline base = engine(Map.of(), 2).createBaseline(List.of(v));
    MatchResult r = engine(Map.of(), 2).compare(base, List.of(v));
    assertNoNew(r);
    assertEquals(1, r.unchangedCount());
  }

  @Test
  void numbersInMessagesDoNotMakeViolationsNew() {
    Violation a = new Violation("x", "R", Severity.INFO, "Line is 130 characters long (max 120)", "src/F.java", 0, 0, Map.of("scope", "F"));
    Violation b = new Violation("x", "R", Severity.INFO, "Line is 145 characters long (max 120)", "src/F.java", 0, 0, Map.of("scope", "F"));
    Baseline base = engine(Map.of(), 2).createBaseline(List.of(a));
    MatchResult r = engine(Map.of(), 2).compare(base, List.of(b));
    assertNoNew(r);
  }
}
