package dev.ratchet4j.core;

import static dev.ratchet4j.core.TestSupport.SERVICE;
import static dev.ratchet4j.core.TestSupport.analyze;
import static dev.ratchet4j.core.TestSupport.engine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ratchet4j.core.match.MatchResult;
import dev.ratchet4j.core.match.Policy;
import dev.ratchet4j.core.model.Baseline;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyTest {

  private static final Map<String, String> FILES = Map.of("src/Service.java", SERVICE);

  private static MatchResult unchanged() {
    Baseline base = engine(FILES, 2).createBaseline(analyze(FILES));
    return engine(FILES, 2).compare(base, analyze(FILES));
  }

  @Test
  void defaultPolicyOnlyFailsOnNewViolations() {
    assertEquals(0, Policy.none().failing(unchanged()).size());
  }

  @Test
  void strictRulesFailEvenWhenBaselined() {
    Policy strict = new Policy(List.of("fake:Bad*"));
    assertEquals(2, strict.failing(unchanged()).size());
    assertTrue(strict.isStrict("fake:BadCall"));
  }

  @Test
  void globsSupportWildcardsAnywhere() {
    Policy p = new Policy(List.of("*:BadCall", "spotbugs:SQL_*_INJ*"));
    assertTrue(p.isStrict("fake:BadCall"));
    assertTrue(p.isStrict("spotbugs:SQL_X_INJECTION"));
    assertFalse(p.isStrict("pmd:BadCall2"));
    assertFalse(p.isStrict("fake:Other"));
  }

  @Test
  void failingViolationsAreSorted() {
    Policy strict = new Policy(List.of("*"));
    var failing = strict.failing(unchanged());
    assertTrue(failing.get(0).violation().line() < failing.get(1).violation().line());
  }
}
