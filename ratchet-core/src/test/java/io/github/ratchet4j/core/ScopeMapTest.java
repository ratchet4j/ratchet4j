package io.github.ratchet4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.ratchet4j.core.source.ScopeMap;
import io.github.ratchet4j.core.source.SourceNormalizer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ScopeMapTest {

  private static String[] scopes(String... lines) {
    String[] norm = Arrays.stream(lines).map(SourceNormalizer::normalizeLine).toArray(String[]::new);
    return ScopeMap.compute(norm);
  }

  @Test
  void assignsClassAndMethodScopes() {
    String[] s =
        scopes(
            "package demo;",                               // 0
            "public class Service {",                      // 1
            "    private int f = 0;",                      // 2
            "    public Service() {",                      // 3
            "        f = 1;",                              // 4
            "    }",                                       // 5
            "    public static <T> List<T> first(int a) {", // 6
            "        if (a > 0) {",                        // 7
            "            bad();",                          // 8
            "        }",                                   // 9
            "    }",                                       // 10
            "}");                                          // 11
    assertEquals("", s[0]);
    assertEquals("Service", s[1]);
    assertEquals("Service", s[2]);
    assertEquals("Service#Service", s[4]);
    assertEquals("Service", s[5]);
    assertEquals("Service#first", s[6]);
    assertEquals("Service#first", s[8]);
    assertEquals("Service#first", s[9]);
    assertEquals("Service", s[10]);
  }

  @Test
  void multiLineSignaturesAndInnerClasses() {
    String[] s =
        scopes(
            "class Outer {",                 // 0
            "    void run(int a,",           // 1
            "             int b) {",         // 2
            "        x();",                  // 3
            "    }",                         // 4
            "    static class Inner {",      // 5
            "        void deep() {",         // 6
            "            y();",              // 7
            "        }",                     // 8
            "    }",                         // 9
            "}");
    assertEquals("Outer#run", s[1]);
    assertEquals("Outer#run", s[3]);
    assertEquals("Inner#deep", s[7]);
    assertEquals("Outer", s[4]);
  }

  @Test
  void callsAndControlFlowAreNotMistakenForMethods() {
    String[] s =
        scopes(
            "class C {",
            "    void m() {",
            "        helper.call(",
            "            1);",
            "        while (true) {",
            "            return new Thread(",
            "                null);",
            "        }",
            "    }",
            "}");
    assertEquals("C#m", s[3]);
    assertEquals("C#m", s[6]);
  }

  @Test
  void bracesInsideStringsDoNotBreakDepthTracking() {
    String[] s =
        scopes(
            "class C {",
            "    void m() {",
            "        String t = \"}}}\";",
            "        z();",
            "    }",
            "    void n() {",
            "        w();",
            "    }",
            "}");
    assertEquals("C#m", s[3]);
    assertEquals("C#n", s[6]);
  }
}
